package com.lightnotes.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.SyncState
import java.util.UUID

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "global_id", defaultValue = "")
    var globalId: String = "",
    var name: String = "",
    var color: String = "#FFB200",
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted_at_utc")
    var deletedAtUtc: Long? = null,
    @ColumnInfo(name = "revision", defaultValue = "1")
    var revision: Long = 1L,
    @ColumnInfo(name = "sync_state", defaultValue = "LOCAL_ONLY")
    var syncState: String = "LOCAL_ONLY"
) {
    fun toDomain(): Folder {
        val effectiveGlobalId = if (globalId.isNotBlank()) globalId else UUID.randomUUID().toString()
        val effectiveSyncState = try {
            SyncState.valueOf(syncState)
        } catch (_: Exception) {
            SyncState.LOCAL_ONLY
        }
        return Folder(
            localId = id,
            globalId = effectiveGlobalId,
            name = name,
            color = color,
            createdAtUtc = createdAt,
            updatedAtUtc = createdAt,
            deletedAtUtc = deletedAtUtc,
            revision = revision,
            syncState = effectiveSyncState
        )
    }

    companion object {
        fun fromDomain(folder: Folder): FolderEntity {
            return FolderEntity(
                id = folder.localId,
                globalId = folder.globalId,
                name = folder.name,
                color = folder.color,
                createdAt = folder.createdAtUtc,
                deletedAtUtc = folder.deletedAtUtc,
                revision = folder.revision,
                syncState = folder.syncState.name
            )
        }
    }
}
