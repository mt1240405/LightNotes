package com.lightnotes.app.storage.repository

import android.content.Context
import androidx.room.withTransaction
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.OutboxStatus
import com.lightnotes.app.core.model.SyncEntityType
import com.lightnotes.app.core.model.SyncOperation
import com.lightnotes.app.core.model.SyncOutboxEvent
import com.lightnotes.app.core.model.SyncState
import com.lightnotes.app.core.repository.FolderRepository
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.FolderEntity
import com.lightnotes.app.data.SyncOutboxEntity
import com.lightnotes.app.platform.DeviceIdentityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LocalFolderRepository(
    private val db: AppDatabase,
    private val deviceIdentityProvider: DeviceIdentityProvider
) : FolderRepository {

    private val folderDao = db.folderDao()
    private val syncOutboxDao = db.syncOutboxDao()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllFoldersSync(): List<Folder> = withContext(Dispatchers.IO) {
        folderDao.getAllFoldersSync().map { it.toDomain() }
    }

    override suspend fun getFolderByGlobalId(globalId: String): Folder? = withContext(Dispatchers.IO) {
        folderDao.getFolderByGlobalId(globalId)?.toDomain()
    }

    override suspend fun createFolder(name: String, color: String): Folder = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val globalId = UUID.randomUUID().toString()
            val deviceId = deviceIdentityProvider.getOrGenerateDeviceId()

            val folder = Folder(
                globalId = globalId,
                name = name.trim(),
                color = color,
                createdAtUtc = now,
                updatedAtUtc = now,
                revision = 1L,
                syncState = SyncState.PENDING_SYNC
            )

            val entity = FolderEntity.fromDomain(folder)
            val id = folderDao.insert(entity)
            val createdFolder = folder.copy(localId = id)

            val event = SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityId = globalId,
                entityType = SyncEntityType.FOLDER,
                operationType = SyncOperation.CREATE,
                deviceId = deviceId,
                localRevision = 1L,
                createdAtUtc = now,
                payloadJson = json.encodeToString(createdFolder),
                syncStatus = OutboxStatus.PENDING
            )
            syncOutboxDao.insert(SyncOutboxEntity.fromDomain(event))

            createdFolder
        }
    }

    override suspend fun updateFolder(folder: Folder): Folder = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val deviceId = deviceIdentityProvider.getOrGenerateDeviceId()
            val newRevision = folder.revision + 1L

            val updatedFolder = folder.copy(
                updatedAtUtc = now,
                revision = newRevision,
                syncState = SyncState.PENDING_SYNC
            )

            folderDao.update(FolderEntity.fromDomain(updatedFolder))

            val event = SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityId = folder.globalId,
                entityType = SyncEntityType.FOLDER,
                operationType = SyncOperation.UPDATE,
                deviceId = deviceId,
                localRevision = newRevision,
                createdAtUtc = now,
                payloadJson = json.encodeToString(updatedFolder),
                syncStatus = OutboxStatus.PENDING
            )
            syncOutboxDao.insert(SyncOutboxEntity.fromDomain(event))

            updatedFolder
        }
    }

    override suspend fun deleteFolder(globalId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val folder = folderDao.getFolderByGlobalId(globalId)
            if (folder != null) {
                folderDao.delete(folder)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LocalFolderRepository? = null

        fun getInstance(context: Context): LocalFolderRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val deviceIdentityProvider = DeviceIdentityProvider.getInstance(context)
                val instance = LocalFolderRepository(db, deviceIdentityProvider)
                INSTANCE = instance
                instance
            }
        }
    }
}
