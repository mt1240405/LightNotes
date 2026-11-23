package com.lightnotes.app.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

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
