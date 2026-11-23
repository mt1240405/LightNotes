package com.lightnotes.app.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

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
