package com.aidriy.app.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aidriy.app.AidriyApplication
import com.aidriy.app.MainActivity
import com.aidriy.app.R
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules note reminders and the optional daily journal reminder. Fully local. */
class ReminderScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    fun scheduleNote(noteId: String, title: String, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<NoteReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_NOTE_ID to noteId, KEY_TITLE to title))
            .build()
        workManager.enqueueUniqueWork(noteWorkName(noteId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelNote(noteId: String) {
        workManager.cancelUniqueWork(noteWorkName(noteId))
    }

    fun setDaily(enabled: Boolean, hour: Int, minute: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(DAILY_WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyJournalWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
        const val KEY_TITLE = "title"
        private const val DAILY_WORK = "daily_journal_reminder"
        private fun noteWorkName(noteId: String) = "note_reminder_$noteId"
    }
}

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val EXTRA_OPEN_NOTE = "open_note_id"
    const val EXTRA_OPEN_JOURNAL_TODAY = "open_journal_today"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.notif_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, id: Int, title: String, text: String, intent: Intent) {
        if (!canPost(context)) return
        ensureChannel(context)
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: nothing to show.
        }
    }
}

class NoteReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getString(ReminderScheduler.KEY_NOTE_ID) ?: return Result.success()
        val title = inputData.getString(ReminderScheduler.KEY_TITLE).orEmpty()
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Notifications.EXTRA_OPEN_NOTE, noteId)
        Notifications.post(
            applicationContext,
            noteId.hashCode(),
            applicationContext.getString(R.string.notif_note_title),
            title.ifBlank { applicationContext.getString(R.string.untitled) },
            intent,
        )
        (applicationContext as? AidriyApplication)?.container?.noteRepository?.clearReminder(noteId)
        return Result.success()
    }
}

class DailyJournalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Notifications.EXTRA_OPEN_JOURNAL_TODAY, true)
        Notifications.post(
            applicationContext,
            DAILY_ID,
            applicationContext.getString(R.string.notif_daily_title),
            applicationContext.getString(R.string.notif_daily_body),
            intent,
        )
        return Result.success()
    }

    private companion object {
        const val DAILY_ID = 7001
    }
}
