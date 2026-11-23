package com.lightnotes.app

import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.search.SearchRanker
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.platform.DeviceIdentityProvider
import com.lightnotes.app.storage.repository.LocalFolderRepository
import com.lightnotes.app.storage.repository.LocalNoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchAndFolderTest {

    private lateinit var db: AppDatabase
    private lateinit var noteRepo: LocalNoteRepository
    private lateinit var folderRepo: LocalFolderRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = AppDatabase.getDatabase(context)
        val deviceIdProvider = DeviceIdentityProvider.getInstance(context)
        noteRepo = LocalNoteRepository(db, deviceIdProvider)
        folderRepo = LocalFolderRepository(db, deviceIdProvider)
    }

    @After
    fun tearDown() {
        runBlocking {
            noteRepo.emptyTrash()
            val all = noteRepo.getAllNotesSync()
            for (n in all) {
                noteRepo.permanentlyDelete(n.globalId)
            }
        }
    }

    @Test
    fun testSearchRankingTitleMatchesAboveBodyMatches() {
        val note1 = Note(
            globalId = "note-1",
            title = "Random Thoughts",
            content = "This document contains details about SQLite architecture.",
            updatedAtUtc = 1000L
        )

        val note2 = Note(
            globalId = "note-2",
            title = "SQLite Architecture Deep Dive",
            content = "Comprehensive guide on mobile storage.",
            updatedAtUtc = 2000L
        )

        val note3 = Note(
            globalId = "note-3",
            title = "Unrelated Topic",
            content = "No mentions of the search query at all.",
            updatedAtUtc = 3000L
        )

        val results = SearchRanker.rankNotes("SQLite", listOf(note1, note2, note3))

        // Note 2 has "SQLite" in the title (starting position) -> highest rank
        // Note 1 has "SQLite" in the body -> second rank
        // Note 3 does not match -> excluded
        assertEquals(2, results.size)
        assertEquals("note-2", results[0].globalId)
        assertEquals("note-1", results[1].globalId)
    }

    @Test
    fun testSearchExcludesDeletedNotes() = runBlocking {
        val activeNote = Note(
            globalId = "active-note",
            title = "Active Architecture Spec",
            content = "Content for active spec"
        )
        val deletedNote = Note(
            globalId = "deleted-note",
            title = "Deleted Architecture Spec",
            content = "Content for deleted spec",
            deletedAtUtc = System.currentTimeMillis()
        )

        noteRepo.saveNote(activeNote)
        noteRepo.saveNote(deletedNote)

        val activeList = noteRepo.observeActiveNotes().first()
        val deletedList = noteRepo.observeDeletedNotes().first()

        assertTrue(activeList.any { it.globalId == "active-note" })
        assertTrue(activeList.none { it.globalId == "deleted-note" })
        assertTrue(deletedList.any { it.globalId == "deleted-note" })
    }

    @Test
    fun testFolderFilteringAndMoving() = runBlocking {
        val folder = folderRepo.createFolder("Work Project")
        assertNotNull(folder.globalId)

        val noteInFolder = noteRepo.saveNote(
            Note(
                globalId = "folder-note-1",
                title = "Work Task",
                content = "Task details",
                folderGlobalId = folder.globalId
            )
        )

        val rootNote = noteRepo.saveNote(
            Note(
                globalId = "root-note-1",
                title = "Personal Task",
                content = "Task details",
                folderGlobalId = null
            )
        )

        val folderNotes = noteRepo.observeNotesByFolder(folder.globalId).first()
        assertEquals(1, folderNotes.size)
        assertEquals("folder-note-1", folderNotes[0].globalId)

        // Move note from folder to root
        noteRepo.saveNote(noteInFolder.copy(folderGlobalId = null))
        val updatedFolderNotes = noteRepo.observeNotesByFolder(folder.globalId).first()
        assertEquals(0, updatedFolderNotes.size)
    }

    @Test
    fun testPinFavoriteAndTrashLifecycle() = runBlocking {
        val note = noteRepo.saveNote(
            Note(
                globalId = "lifecycle-note",
                title = "Lifecycle Note",
                content = "Content"
            )
        )

        // Pin
        val pinned = noteRepo.togglePin(note.globalId)
        assertTrue(pinned?.isPinned == true)

        // Favorite
        val favorited = noteRepo.toggleFavorite(note.globalId)
        assertTrue(favorited?.isFavorite == true)

        // Move to Trash (soft delete with tombstone)
        val trashed = noteRepo.moveToTrash(note.globalId)
        assertTrue(trashed?.isDeleted == true)
        assertNotNull(trashed?.deletedAtUtc)

        // Restore
        val restored = noteRepo.restoreFromTrash(note.globalId)
        assertFalse(restored?.isDeleted == true)
        assertNull(restored?.deletedAtUtc)

        // Move to trash again and empty trash
        noteRepo.moveToTrash(note.globalId)
        noteRepo.emptyTrash()

        val afterEmpty = noteRepo.getNoteByGlobalId(note.globalId)
        assertNull(afterEmpty)
    }
}
