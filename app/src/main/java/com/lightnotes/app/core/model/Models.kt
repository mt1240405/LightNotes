package com.lightnotes.app.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class SyncState {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCHRONIZING,
    SYNCHRONIZED,
    SYNC_FAILED,
    CONFLICT_DETECTED
}

@Serializable
data class Note(
    val localId: Long = 0,
    val globalId: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val folderGlobalId: String? = null,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val updatedAtUtc: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val deletedAtUtc: Long? = null,
    val schemaVersion: Int = 1,
    val revision: Long = 1L,
    val syncState: SyncState = SyncState.LOCAL_ONLY
) {
    val isDeleted: Boolean
        get() = deletedAtUtc != null

    val previewText: String
        get() {
            if (content.isBlank()) return ""
            val lines = content.trim().lines()
            val firstBodyLine = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?: lines.firstOrNull { it.isNotBlank() }
                ?: ""
            return firstBodyLine.take(120)
        }
}

@Serializable
data class Folder(
    val localId: Long = 0,
    val globalId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: String = "#FFB200",
    val createdAtUtc: Long = System.currentTimeMillis(),
    val updatedAtUtc: Long = System.currentTimeMillis(),
    val deletedAtUtc: Long? = null,
    val revision: Long = 1L,
    val syncState: SyncState = SyncState.LOCAL_ONLY
) {
    val isDeleted: Boolean
        get() = deletedAtUtc != null
}

enum class SyncEntityType {
    NOTE,
    FOLDER
}

enum class SyncOperation {
    CREATE,
    UPDATE,
    MOVE,
    ARCHIVE,
    DELETE
}

enum class OutboxStatus {
    PENDING,
    IN_FLIGHT,
    ACKNOWLEDGED,
    FAILED
}

@Serializable
data class SyncOutboxEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val entityId: String,
    val entityType: SyncEntityType,
    val operationType: SyncOperation,
    val deviceId: String,
    val localRevision: Long,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val payloadJson: String,
    val syncStatus: OutboxStatus = OutboxStatus.PENDING
)
