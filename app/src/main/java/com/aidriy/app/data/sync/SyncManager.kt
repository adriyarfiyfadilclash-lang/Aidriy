package com.aidriy.app.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aidriy.app.AidriyApplication
import com.aidriy.app.data.cloud.AuthError
import com.aidriy.app.data.cloud.AuthResult
import com.aidriy.app.data.cloud.CloudAuth
import com.aidriy.app.data.cloud.CloudHttpException
import com.aidriy.app.data.cloud.CloudOfflineException
import com.aidriy.app.data.cloud.CloudSession
import com.aidriy.app.data.cloud.SessionProvider
import com.aidriy.app.data.cloud.SessionStore
import com.aidriy.app.data.cloud.SupabaseRemoteSync
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.data.local.SyncTriggers
import com.aidriy.app.data.repository.TagWriter
import com.aidriy.app.domain.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

enum class SyncStatus { IDLE, RUNNING, OK, OFFLINE, ERROR, SESSION_EXPIRED }

data class CloudState(
    val email: String? = null,
    val status: SyncStatus = SyncStatus.IDLE,
    val lastSyncAt: Long = 0L,
    val pending: Int = 0,
    /** Signed in on a phone that holds another account's data: the user must choose first. */
    val needsMergeChoice: Boolean = false,
    /** Opened from a reset-password email: ask for a new password. */
    val needsNewPassword: Boolean = false,
)

/**
 * Account + cloud sync. Local Room stays the source the UI reads; this keeps the user's cloud
 * copy (Supabase) in step: right after sign-in, a few seconds after local changes, every
 * 15 minutes in the background (WorkManager), and on demand.
 */
@OptIn(FlowPreview::class)
class SyncManager internal constructor(
    private val context: Context,
    private val db: AidriyDatabase,
    tags: TagWriter,
    private val sessions: SessionStore,
    private val auth: CloudAuth,
    private val storageDir: () -> File,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CloudState(email = sessions.session.value?.email, lastSyncAt = sessions.lastSyncAt))
    val state: StateFlow<CloudState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val refreshMutex = Mutex()

    private val provider = SessionProvider { force -> validSession(force) }
    private val remote = SupabaseRemoteSync(provider) { id -> File(storageDir(), id) }
    private val local = RoomLocalSync(db, tags, sessions, storageDir) { path, target -> remote.download(path, target) }
    private val engine = SyncEngine(local, remote)
    private var watcher: Job? = null

    /** Called once from Application.onCreate. */
    fun start() {
        scope.launch {
            if (sessions.session.value != null && sessions.lastUserId == sessions.session.value?.userId) {
                db.syncDao().setPaused(0)
                watchLocalChanges()
                schedulePeriodic()
                syncNow()
            } else {
                db.syncDao().setPaused(1)
            }
        }
    }

    // ------------------------------------------------------------------ account

    suspend fun signIn(email: String, password: String): AuthResult = finish(auth.signIn(email, password))

    suspend fun signUp(email: String, password: String): AuthResult = finish(auth.signUp(email, password))

    suspend fun sendPasswordReset(email: String): AuthError? = auth.sendPasswordReset(email)

    /** aidriy://auth from a confirmation or reset email. */
    suspend fun handleRedirect(uri: Uri): AuthResult {
        val (result, type) = auth.fromRedirect(uri)
        val done = finish(result)
        if (done is AuthResult.SignedIn && type == "recovery") _state.update { it.copy(needsNewPassword = true) }
        return done
    }

    suspend fun setNewPassword(password: String): AuthError? {
        if (sessions.session.value == null) return AuthError.SessionExpired
        val error = try {
            auth.updatePassword(validSession(false), password)
        } catch (e: SessionExpiredException) {
            AuthError.SessionExpired
        } catch (e: CloudOfflineException) {
            AuthError.Offline
        }
        if (error == null) _state.update { it.copy(needsNewPassword = false) }
        return error
    }

    fun dismissNewPassword() = _state.update { it.copy(needsNewPassword = false) }

    private suspend fun finish(result: AuthResult): AuthResult {
        if (result !is AuthResult.SignedIn) return result
        val session = result.session
        sessions.save(session)
        _state.update { it.copy(email = session.email, status = SyncStatus.IDLE) }
        val previous = sessions.lastUserId
        when {
            previous == session.userId -> activate(markAll = false)
            previous != null && hasLocalData() -> _state.update { it.copy(needsMergeChoice = true) }
            // First account on this phone: everything already here belongs to it.
            else -> activate(markAll = true)
        }
        return result
    }

    /** After signing in with another account than last time: keep (merge) or replace local data. */
    suspend fun resolveMerge(keepLocal: Boolean) {
        if (!keepLocal) wipeLocalData()
        _state.update { it.copy(needsMergeChoice = false) }
        activate(markAll = keepLocal)
    }

    private suspend fun activate(markAll: Boolean) {
        val session = sessions.session.value ?: return
        if (sessions.lastUserId != session.userId) {
            sessions.resetCursors()
            db.syncDao().clearUploaded()
            db.syncDao().clearAllDirty()
        }
        sessions.lastUserId = session.userId
        db.syncDao().setPaused(0)
        if (markAll) {
            withContext(Dispatchers.IO) {
                val sql = db.openHelper.writableDatabase
                SyncTriggers.markAllSql().forEach(sql::execSQL)
            }
        }
        watchLocalChanges()
        schedulePeriodic()
        syncNow()
    }

    /** Signs out. Data stays on the phone unless [wipeLocal]; the cloud copy is untouched. */
    suspend fun signOut(wipeLocal: Boolean) {
        sessions.session.value?.let { auth.signOut(it) }
        watcher?.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        db.syncDao().setPaused(1)
        if (wipeLocal) {
            wipeLocalData()
            sessions.lastUserId = null
            sessions.resetCursors()
        }
        sessions.clear()
        _state.value = CloudState()
    }

    private suspend fun hasLocalData(): Boolean = withContext(Dispatchers.IO) {
        val c = db.openHelper.readableDatabase.query(
            "SELECT (SELECT COUNT(*) FROM notes) + (SELECT COUNT(*) FROM ideas) + " +
                "(SELECT COUNT(*) FROM journal_entries) + (SELECT COUNT(*) FROM files)",
        )
        c.use { it.moveToFirst() && it.getLong(0) > 0 }
    }

    private suspend fun wipeLocalData() = withContext(Dispatchers.IO) {
        db.syncDao().setPaused(1)
        db.clearAllTables()
        storageDir().listFiles()?.forEach { it.delete() }
        // clearAllTables also emptied sync_meta: put the row back (paused).
        SyncTriggers.install(db.openHelper.writableDatabase)
        db.syncDao().setPaused(1)
    }

    // ------------------------------------------------------------------ syncing

    private fun watchLocalChanges() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            db.syncDao().observeDirtyCount()
                .debounce(LOCAL_CHANGE_DELAY_MS)
                .filter { it > 0 }
                .collect { count ->
                    _state.update { it.copy(pending = count) }
                    syncNow()
                }
        }
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Pull then push. Safe to call often: runs one at a time, no-op when signed out. */
    suspend fun syncNow(): SyncStatus = mutex.withLock {
        if (sessions.session.value == null || _state.value.needsMergeChoice) return SyncStatus.IDLE
        _state.update { it.copy(status = SyncStatus.RUNNING) }
        val status = try {
            engine.sync()
            sessions.lastSyncAt = System.currentTimeMillis()
            SyncStatus.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudOfflineException) {
            SyncStatus.OFFLINE
        } catch (e: SessionExpiredException) {
            SyncStatus.SESSION_EXPIRED
        } catch (e: CloudHttpException) {
            Log.w(TAG, "Sync failed: HTTP ${e.code}")
            SyncStatus.ERROR
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.javaClass.simpleName}")
            SyncStatus.ERROR
        }
        val pending = db.syncDao().dirty(Int.MAX_VALUE).size
        _state.update { it.copy(status = status, lastSyncAt = sessions.lastSyncAt, pending = pending) }
        status
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    private class SessionExpiredException : Exception()

    /** A session whose access token is valid for at least a minute; refreshes when needed. */
    private suspend fun validSession(force: Boolean): CloudSession = refreshMutex.withLock {
        val current = sessions.session.value ?: throw SessionExpiredException()
        if (!force && !current.expiresSoon()) return current
        when (val r = auth.refresh(current)) {
            is AuthResult.SignedIn -> r.session.also { sessions.save(it) }
            is AuthResult.Failed -> when (r.error) {
                AuthError.Offline -> throw CloudOfflineException(java.io.IOException("offline"))
                else -> {
                    sessions.clear()
                    db.syncDao().setPaused(1)
                    _state.update { it.copy(email = null) }
                    throw SessionExpiredException()
                }
            }
            is AuthResult.ConfirmEmail -> throw SessionExpiredException()
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val WORK_NAME = "aidriy_sync"
        private const val LOCAL_CHANGE_DELAY_MS = 5_000L
    }
}

/** Background sync every 15 minutes while signed in (network required). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? AidriyApplication ?: return Result.failure()
        return when (app.container.syncManager.syncNow()) {
            SyncStatus.OFFLINE -> Result.retry()
            else -> Result.success()
        }
    }
}
