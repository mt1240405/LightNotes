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
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND folderId = :folderId ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByFolder(folderId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.id = notes_fts.docid
        WHERE notes_fts MATCH :query AND notes.isDeleted = 0
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
    """)
    fun searchNotesFts(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getNoteByUuid(uuid: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE title = :title AND isDeleted = 0 LIMIT 1")
    suspend fun findNoteByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<NoteEntity>

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

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    fun getActiveNotesCount(): Flow<Int>

    // Tag Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<NoteTagEntity>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteTagsForNote(noteId: Long)

    @Query("SELECT DISTINCT tag FROM note_tags ORDER BY tag ASC")
    fun getAllTags(): Flow<List<String>>

    @Query("""
        SELECT notes.* FROM notes
        JOIN note_tags ON notes.id = note_tags.noteId
        WHERE note_tags.tag = :tag AND notes.isDeleted = 0
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
    """)
    fun getNotesByTag(tag: String): Flow<List<NoteEntity>>

    // Link & Backlink Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<NoteLinkEntity>)

    @Query("DELETE FROM note_links WHERE sourceNoteId = :sourceNoteId")
    suspend fun deleteLinksForNote(sourceNoteId: Long)

    @Query("SELECT * FROM note_links WHERE targetTitle = :title OR (targetNoteId IS NOT NULL AND targetNoteId = :noteId)")
    fun getBacklinksForNote(title: String, noteId: Long): Flow<List<NoteLinkEntity>>

    @Query("""
        SELECT notes.* FROM notes
        JOIN note_links ON notes.id = note_links.sourceNoteId
        WHERE (note_links.targetTitle = :title OR (note_links.targetNoteId IS NOT NULL AND note_links.targetNoteId = :noteId))
          AND notes.isDeleted = 0
        ORDER BY notes.updatedAt DESC
    """)
    fun getBacklinkSourceNotes(title: String, noteId: Long): Flow<List<NoteEntity>>
}
