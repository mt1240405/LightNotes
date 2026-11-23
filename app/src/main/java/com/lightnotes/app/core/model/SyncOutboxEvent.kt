package com.lightnotes.app.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

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
