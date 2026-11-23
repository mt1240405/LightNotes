package com.lightnotes.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.model.SyncState
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "global_id", defaultValue = "")
    var globalId: String = "",
    var title: String = "",
    var content: String = "",
    var previewText: String = "",
    var folderId: Long? = null,
    @ColumnInfo(name = "folder_global_id")
    var folderGlobalId: String? = null,
    var isPinned: Boolean = false,
    var isFavorite: Boolean = false,
    var isArchived: Boolean = false,
    var isDeleted: Boolean = false,
    var isLocked: Boolean = false,
    var isEncrypted: Boolean = false,
    var tagsCsv: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var reminderTime: Long? = null,
    var tags: String = "",
    @ColumnInfo(name = "schema_version", defaultValue = "1")
    var schemaVersion: Int = 1,
    @ColumnInfo(name = "revision", defaultValue = "1")
    var revision: Long = 1L,
    @ColumnInfo(name = "deleted_at_utc")
    var deletedAtUtc: Long? = null,
    @ColumnInfo(name = "sync_state", defaultValue = "LOCAL_ONLY")
    var syncState: String = "LOCAL_ONLY"
) {
    fun toDomain(): Note {
        val effectiveGlobalId = if (globalId.isNotBlank()) globalId else if (uuid.isNotBlank()) uuid else UUID.randomUUID().toString()
        val effectiveSyncState = try {
            SyncState.valueOf(syncState)
        } catch (_: Exception) {
            SyncState.LOCAL_ONLY
        }
        val effectiveDeletedAt = deletedAtUtc ?: if (isDeleted) updatedAt else null
        return Note(
            localId = id,
            globalId = effectiveGlobalId,
            title = title,
            content = content,
            folderGlobalId = folderGlobalId,
            createdAtUtc = createdAt,
            updatedAtUtc = updatedAt,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isArchived = isArchived,
            deletedAtUtc = effectiveDeletedAt,
            schemaVersion = schemaVersion,
            revision = revision,
            syncState = effectiveSyncState
        )
    }

    companion object {
        fun fromDomain(note: Note): NoteEntity {
            return NoteEntity(
                id = note.localId,
                uuid = note.globalId,
                globalId = note.globalId,
                title = note.title,
                content = note.content,
                previewText = note.previewText,
                folderGlobalId = note.folderGlobalId,
                isPinned = note.isPinned,
                isFavorite = note.isFavorite,
                isArchived = note.isArchived,
                isDeleted = note.isDeleted,
                deletedAtUtc = note.deletedAtUtc,
                createdAt = note.createdAtUtc,
                updatedAt = note.updatedAtUtc,
                schemaVersion = note.schemaVersion,
                revision = note.revision,
                syncState = note.syncState.name
            )
        }
    }
}
