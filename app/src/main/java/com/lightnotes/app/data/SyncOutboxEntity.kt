package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lightnotes.app.core.model.OutboxStatus
import com.lightnotes.app.core.model.SyncEntityType
import com.lightnotes.app.core.model.SyncOperation
import com.lightnotes.app.core.model.SyncOutboxEvent

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index("syncStatus", "createdAtUtc"),
        Index("entityId", "localRevision")
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey
    val eventId: String,
    val entityId: String,
    val entityType: String,
    val operationType: String,
    val deviceId: String,
    val localRevision: Long,
    val createdAtUtc: Long,
    val payloadJson: String,
    val syncStatus: String = "PENDING"
) {
    fun toDomain(): SyncOutboxEvent {
        return SyncOutboxEvent(
            eventId = eventId,
            entityId = entityId,
            entityType = try { SyncEntityType.valueOf(entityType) } catch (_: Exception) { SyncEntityType.NOTE },
            operationType = try { SyncOperation.valueOf(operationType) } catch (_: Exception) { SyncOperation.UPDATE },
            deviceId = deviceId,
            localRevision = localRevision,
            createdAtUtc = createdAtUtc,
            payloadJson = payloadJson,
            syncStatus = try { OutboxStatus.valueOf(syncStatus) } catch (_: Exception) { OutboxStatus.PENDING }
        )
    }

    companion object {
        fun fromDomain(event: SyncOutboxEvent): SyncOutboxEntity {
            return SyncOutboxEntity(
                eventId = event.eventId,
                entityId = event.entityId,
                entityType = event.entityType.name,
                operationType = event.operationType.name,
                deviceId = event.deviceId,
                localRevision = event.localRevision,
                createdAtUtc = event.createdAtUtc,
                payloadJson = event.payloadJson,
                syncStatus = event.syncStatus.name
            )
        }
    }
}
