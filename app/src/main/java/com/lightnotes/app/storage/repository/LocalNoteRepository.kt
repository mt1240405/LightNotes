package com.lightnotes.app.storage.repository

import android.content.Context
import androidx.room.withTransaction
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.model.OutboxStatus
import com.lightnotes.app.core.model.SyncEntityType
import com.lightnotes.app.core.model.SyncOperation
import com.lightnotes.app.core.model.SyncOutboxEvent
import com.lightnotes.app.core.model.SyncState
import com.lightnotes.app.core.repository.NoteRepository
import com.lightnotes.app.core.search.SearchRanker
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.FolderEntity
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.data.SyncOutboxEntity
import com.lightnotes.app.platform.DeviceIdentityProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LocalNoteRepository(
    private val db: AppDatabase,
    private val deviceIdentityProvider: DeviceIdentityProvider
) : NoteRepository {

    private val noteDao = db.noteDao()
    private val syncOutboxDao = db.syncOutboxDao()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeActiveNotes(): Flow<List<Note>> {
        return noteDao.getActiveNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeArchivedNotes(): Flow<List<Note>> {
        return noteDao.getArchivedNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeDeletedNotes(): Flow<List<Note>> {
        return noteDao.getDeletedNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeFavoriteNotes(): Flow<List<Note>> {
        return noteDao.getFavoriteNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeNotesByFolder(folderGlobalId: String): Flow<List<Note>> {
        val folderId = folderGlobalId.toLongOrNull()
        return noteDao.getNotesByFolder(folderGlobalId, folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchNotes(query: String): Flow<List<Note>> {
        val cleanQuery = query.trim()
        val flow = if (cleanQuery.isBlank()) {
            noteDao.getActiveNotes()
        } else {
            val ftsQuery = cleanQuery.replace(Regex("[^a-zA-Z0-9_]"), " ").trim()
            if (ftsQuery.isNotEmpty()) {
                val wildcardQuery = ftsQuery.split(Regex("\\s+")).joinToString(" ") { "$it*" }
                noteDao.searchNotesFts(wildcardQuery)
            } else {
                noteDao.searchNotesLike(cleanQuery)
            }
        }

        return flow.map { entities ->
            val domainNotes = entities.map { it.toDomain() }
            SearchRanker.rankNotes(cleanQuery, domainNotes)
        }
    }

    override suspend fun getNoteByLocalId(localId: Long): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(localId)?.toDomain()
    }

    override suspend fun getNoteByGlobalId(globalId: String): Note? = withContext(Dispatchers.IO) {
        noteDao.getNoteByGlobalId(globalId)?.toDomain()
    }

    override suspend fun saveNote(note: Note): Note = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val deviceId = deviceIdentityProvider.getOrGenerateDeviceId()

            val existing = if (note.localId > 0) {
                noteDao.getNoteById(note.localId)
            } else {
                noteDao.getNoteByGlobalId(note.globalId)
            }

            val isNew = existing == null
            val newRevision = if (isNew) 1L else existing!!.revision + 1L
            val globalId = if (isNew) {
                if (note.globalId.isNotBlank()) note.globalId else UUID.randomUUID().toString()
            } else {
                existing!!.globalId.ifBlank { existing.uuid.ifBlank { note.globalId } }
            }

            val updatedDomain = note.copy(
                globalId = globalId,
                updatedAtUtc = now,
                revision = newRevision,
                syncState = SyncState.PENDING_SYNC
            )

            val entity = NoteEntity.fromDomain(updatedDomain).apply {
                if (existing != null) {
                    createdAt = existing.createdAt
                }
            }

            val localId = noteDao.insert(entity)
            val finalDomain = updatedDomain.copy(localId = localId)

            // FTS update
            if (finalDomain.deletedAtUtc == null) {
                noteDao.updateFts(localId, finalDomain.title, finalDomain.content, "")
            } else {
                noteDao.deleteFts(localId)
            }

            // Sync outbox write
            val event = SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityId = globalId,
                entityType = SyncEntityType.NOTE,
                operationType = if (isNew) SyncOperation.CREATE else SyncOperation.UPDATE,
                deviceId = deviceId,
                localRevision = newRevision,
                createdAtUtc = now,
                payloadJson = json.encodeToString(finalDomain),
                syncStatus = OutboxStatus.PENDING
            )

            syncOutboxDao.insert(SyncOutboxEntity.fromDomain(event))
            syncOutboxDao.coalescePendingEvents(globalId, event.eventId)

            finalDomain
        }
    }

    override suspend fun togglePin(globalId: String): Note? = withContext(Dispatchers.IO) {
        val note = getNoteByGlobalId(globalId) ?: return@withContext null
        saveNote(note.copy(isPinned = !note.isPinned))
    }

    override suspend fun toggleFavorite(globalId: String): Note? = withContext(Dispatchers.IO) {
        val note = getNoteByGlobalId(globalId) ?: return@withContext null
        saveNote(note.copy(isFavorite = !note.isFavorite))
    }

    override suspend fun archiveNote(globalId: String): Note? = withContext(Dispatchers.IO) {
        val note = getNoteByGlobalId(globalId) ?: return@withContext null
        saveNote(note.copy(isArchived = true))
    }

    override suspend fun unarchiveNote(globalId: String): Note? = withContext(Dispatchers.IO) {
        val note = getNoteByGlobalId(globalId) ?: return@withContext null
        saveNote(note.copy(isArchived = false))
    }

    override suspend fun moveToTrash(globalId: String): Note? = withContext(Dispatchers.IO) {
        val note = getNoteByGlobalId(globalId) ?: return@withContext null
        saveNote(note.copy(deletedAtUtc = System.currentTimeMillis()))
    }

    override suspend fun restoreFromTrash(globalId: String): Note? = withContext(Dispatchers.IO) {
        val note = getNoteByGlobalId(globalId) ?: return@withContext null
        saveNote(note.copy(deletedAtUtc = null))
    }

    override suspend fun permanentlyDelete(globalId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val entity = noteDao.getNoteByGlobalId(globalId)
            if (entity != null) {
                noteDao.delete(entity)
                noteDao.deleteFts(entity.id)
            }
        }
    }

    override suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        db.withTransaction {
            val trashed = noteDao.getAllNotesSync().filter { it.deletedAtUtc != null || it.isDeleted }
            for (note in trashed) {
                noteDao.deleteFts(note.id)
            }
            noteDao.emptyTrash()
        }
    }

    override suspend fun getAllNotesSync(): List<Note> = withContext(Dispatchers.IO) {
        noteDao.getAllNotesSync().map { it.toDomain() }
    }

    override suspend fun importNotes(notes: List<Note>, folders: List<Folder>): Int = withContext(Dispatchers.IO) {
        db.withTransaction {
            var count = 0
            for (folder in folders) {
                val existingFolder = db.folderDao().getFolderByGlobalId(folder.globalId)
                    ?: db.folderDao().getFolderByName(folder.name)
                if (existingFolder == null) {
                    db.folderDao().insert(FolderEntity.fromDomain(folder))
                }
            }

            for (note in notes) {
                val existingNote = noteDao.getNoteByGlobalId(note.globalId)
                if (existingNote == null) {
                    val entity = NoteEntity.fromDomain(note)
                    val id = noteDao.insert(entity)
                    if (note.deletedAtUtc == null) {
                        noteDao.updateFts(id, note.title, note.content, "")
                    }
                    count++
                } else {
                    // Non-destructive: if incoming has newer updatedAt, update; otherwise preserve local
                    if (note.updatedAtUtc > existingNote.updatedAt) {
                        val merged = NoteEntity.fromDomain(
                            note.copy(localId = existingNote.id, revision = existingNote.revision + 1)
                        )
                        noteDao.update(merged)
                        if (merged.deletedAtUtc == null) {
                            noteDao.updateFts(existingNote.id, merged.title, merged.content, "")
                        }
                        count++
                    }
                }
            }
            count
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LocalNoteRepository? = null

        fun getInstance(context: Context): LocalNoteRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val deviceIdentityProvider = DeviceIdentityProvider.getInstance(context)
                val instance = LocalNoteRepository(db, deviceIdentityProvider)
                INSTANCE = instance
                instance
            }
        }
    }
}
