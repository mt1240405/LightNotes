package com.lightnotes.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox WHERE syncStatus = 'PENDING' ORDER BY createdAtUtc ASC LIMIT :limit")
    suspend fun getPendingEvents(limit: Int = 100): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET syncStatus = :status WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: String)

    @Query("DELETE FROM sync_outbox WHERE syncStatus = 'ACKNOWLEDGED' AND createdAtUtc < :cutoffTime")
    suspend fun pruneAcknowledgedEvents(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun getOutboxCount(): Int

    @Query("SELECT * FROM sync_outbox WHERE entityId = :entityId ORDER BY localRevision DESC")
    suspend fun getEventsForEntity(entityId: String): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE entityId = :entityId AND syncStatus = 'PENDING' AND eventId != :keepEventId")
    suspend fun coalescePendingEvents(entityId: String, keepEventId: String)

    @Query("DELETE FROM sync_outbox WHERE eventId IN (SELECT eventId FROM sync_outbox ORDER BY createdAtUtc ASC LIMIT :count)")
    suspend fun deleteOldestEvents(count: Int): Int
}
