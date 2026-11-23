package com.lightnotes.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NOT NULL ORDER BY deleted_at_utc DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL AND isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL AND isFavorite = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL AND isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL AND (folder_global_id = :folderGlobalId OR folderId = :folderId) ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByFolder(folderGlobalId: String, folderId: Long?): Flow<List<NoteEntity>>

    @Query("""
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.id = notes_fts.docid
        WHERE notes_fts MATCH :query AND notes.deleted_at_utc IS NULL
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
    """)
    fun searchNotesFts(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotesLike(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE global_id = :globalId OR uuid = :globalId LIMIT 1")
    suspend fun getNoteByGlobalId(globalId: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE title = :title AND deleted_at_utc IS NULL LIMIT 1")
    suspend fun findNoteByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted_at_utc IS NULL")
    suspend fun getAllActiveNotesSync(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>): List<Long>

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE global_id = :globalId OR uuid = :globalId")
    suspend fun deleteByGlobalId(globalId: String)

    @Query("DELETE FROM notes WHERE deleted_at_utc IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at_utc IS NULL")
    fun getActiveNotesCount(): Flow<Int>

    // FTS maintenance queries
    @Query("INSERT OR REPLACE INTO notes_fts(docid, title, content, tagsCsv) VALUES(:docId, :title, :content, :tagsCsv)")
    suspend fun updateFts(docId: Long, title: String, content: String, tagsCsv: String)

    @Query("DELETE FROM notes_fts WHERE docid = :docId")
    suspend fun deleteFts(docId: Long)
}
