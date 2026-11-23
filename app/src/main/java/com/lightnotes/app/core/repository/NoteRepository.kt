package com.lightnotes.app.core.repository

import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeActiveNotes(): Flow<List<Note>>
    fun observeArchivedNotes(): Flow<List<Note>>
    fun observeDeletedNotes(): Flow<List<Note>>
    fun observeFavoriteNotes(): Flow<List<Note>>
    fun observeNotesByFolder(folderGlobalId: String): Flow<List<Note>>
    fun searchNotes(query: String): Flow<List<Note>>
    suspend fun getNoteByLocalId(localId: Long): Note?
    suspend fun getNoteByGlobalId(globalId: String): Note?
    suspend fun saveNote(note: Note): Note
    suspend fun togglePin(globalId: String): Note?
    suspend fun toggleFavorite(globalId: String): Note?
    suspend fun archiveNote(globalId: String): Note?
    suspend fun unarchiveNote(globalId: String): Note?
    suspend fun moveToTrash(globalId: String): Note?
    suspend fun restoreFromTrash(globalId: String): Note?
    suspend fun permanentlyDelete(globalId: String)
    suspend fun emptyTrash()
    suspend fun getAllNotesSync(): List<Note>
    suspend fun importNotes(notes: List<Note>, folders: List<Folder>): Int
}
