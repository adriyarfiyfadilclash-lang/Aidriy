package com.aidriy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records changed locally and not yet sent to the cloud. Filled by SQLite triggers (see
 * SyncTriggers), so repositories need no sync code. [seq] grows on every change, so a row
 * edited again during a push stays in the list.
 */
@Entity(tableName = "sync_dirty", primaryKeys = ["kind", "id"])
data class SyncDirtyEntity(
    val kind: String,
    val id: String,
    val seq: Long,
)

/** Single row (id = 1). While [paused] is 1 the triggers record nothing (signed out, or applying cloud rows). */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int,
    val paused: Int,
)

/** Berkas files whose bytes are already in cloud storage. */
@Entity(tableName = "sync_uploaded")
data class SyncUploadedEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: Long,
)
