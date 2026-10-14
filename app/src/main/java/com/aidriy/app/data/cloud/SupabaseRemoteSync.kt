package com.aidriy.app.data.cloud

import com.aidriy.app.domain.sync.ColumnType
import com.aidriy.app.domain.sync.RemoteSync
import com.aidriy.app.domain.sync.SyncKind
import com.aidriy.app.domain.sync.SyncRecord
import com.aidriy.app.domain.sync.SyncSchema
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** Supplies a valid session; [forceRefresh] after the server answered 401. */
fun interface SessionProvider {
    suspend fun session(forceRefresh: Boolean): CloudSession
}

/** [RemoteSync] over Supabase REST (PostgREST) and Storage. Row Level Security scopes everything to the user. */
class SupabaseRemoteSync(
    private val sessions: SessionProvider,
    /** Local bytes of a Berkas file. */
    private val fileFor: (String) -> File,
) : RemoteSync {

    private val rest = "${CloudConfig.URL}/rest/v1"
    private val storage = "${CloudConfig.URL}/storage/v1/object/${CloudConfig.BUCKET}"

    override suspend fun fetch(kind: SyncKind, since: String?, limit: Int): List<SyncRecord> {
        val filter = since?.let { "&server_updated=gte.${enc(it)}" }.orEmpty()
        val url = "$rest/${kind.table}?select=*$filter&order=server_updated.asc,id.asc&limit=$limit"
        val body = authed { s -> CloudHttp.request("GET", url, headers(s)).body }
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i -> fromJson(kind, arr.getJSONObject(i)) }
    }

    override suspend fun upsert(kind: SyncKind, records: List<SyncRecord>) {
        records.chunked(200).forEach { chunk ->
            val userId = sessions.session(false).userId
            val arr = JSONArray()
            chunk.forEach { r ->
                val o = toJson(kind, r)
                if (kind == SyncKind.FILES) o.put("storage_path", "$userId/${r.id}")
                arr.put(o)
            }
            authed { s ->
                CloudHttp.request(
                    "POST", "$rest/${kind.table}?on_conflict=id",
                    headers(s) + ("Prefer" to "resolution=merge-duplicates,return=minimal"),
                    arr.toString().toByteArray(),
                )
            }
        }
    }

    override suspend fun markDeleted(kind: SyncKind, ids: List<String>) {
        ids.chunked(200).forEach { chunk ->
            rpc("mark_deleted", JSONObject().put("tbl", kind.table).put("ids", JSONArray(chunk)))
        }
    }

    override suspend fun retireOtherJournals(date: String, keepId: String) {
        rpc("retire_other_journals", JSONObject().put("entry_date", date).put("keep_id", keepId))
    }

    override suspend fun uploadFile(record: SyncRecord) {
        val file = fileFor(record.id)
        if (!file.exists()) return
        val mime = record.fields["mime_type"] as? String ?: "application/octet-stream"
        authed { s ->
            CloudHttp.upload(
                "$storage/${s.userId}/${record.id}",
                mapOf("Authorization" to "Bearer ${s.accessToken}", "x-upsert" to "true"),
                file,
                contentType = mime,
            )
        }
    }

    /** Downloads a Berkas file's bytes from Storage to [target]. */
    suspend fun download(storagePath: String, target: File) {
        authed { s ->
            CloudHttp.download("$storage/$storagePath", mapOf("Authorization" to "Bearer ${s.accessToken}"), target)
        }
    }

    private suspend fun rpc(name: String, args: JSONObject) {
        authed { s -> CloudHttp.request("POST", "$rest/rpc/$name", headers(s), args.toString().toByteArray()) }
    }

    /** Runs [block] with a session; on 401 refreshes the token once and retries. */
    private suspend fun <T> authed(block: suspend (CloudSession) -> T): T = try {
        block(sessions.session(false))
    } catch (e: CloudHttpException) {
        if (e.code == 401) block(sessions.session(true)) else throw e
    }

    private fun headers(s: CloudSession) = mapOf("Authorization" to "Bearer ${s.accessToken}", "Accept" to "application/json")

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    companion object {
        fun toJson(kind: SyncKind, r: SyncRecord): JSONObject {
            val o = JSONObject().put("id", r.id).put("deleted", false)
            SyncSchema.columns.getValue(kind).forEach { c ->
                if (c.name == "storage_path") return@forEach
                val v = r.fields[c.name]
                when {
                    v == null -> o.put(c.name, JSONObject.NULL)
                    c.type == ColumnType.TAGS -> o.put(c.name, JSONArray((v as List<*>).map { it.toString() }))
                    else -> o.put(c.name, v)
                }
            }
            return o
        }

        fun fromJson(kind: SyncKind, o: JSONObject): SyncRecord {
            val fields = SyncSchema.columns.getValue(kind).associate { c ->
                c.name to when {
                    o.isNull(c.name) -> null
                    c.type == ColumnType.TEXT -> o.getString(c.name)
                    c.type == ColumnType.LONG -> o.getLong(c.name)
                    c.type == ColumnType.INT -> o.getInt(c.name)
                    c.type == ColumnType.BOOL -> o.getBoolean(c.name)
                    else -> o.optJSONArray(c.name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList<String>()
                }
            }
            return SyncRecord(
                kind = kind,
                id = o.getString("id"),
                fields = fields,
                deleted = o.optBoolean("deleted", false),
                serverUpdated = o.optString("server_updated"),
            )
        }
    }
}
