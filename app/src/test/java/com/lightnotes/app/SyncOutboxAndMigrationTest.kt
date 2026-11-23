package com.lightnotes.app

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.model.OutboxStatus
import com.lightnotes.app.core.model.SyncOperation
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.platform.DeviceIdentityProvider
import com.lightnotes.app.storage.repository.LocalFolderRepository
import com.lightnotes.app.storage.repository.LocalNoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOutboxAndMigrationTest {

    @Test
    fun testRoomMigrationV2ToV3NonDestructive() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("migration_test.db")
        dbFile.delete()

        // 1. Manually create Room v2 SQLite database schema
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        sqlite.execSQL("""
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                uuid TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                previewText TEXT NOT NULL,
                folderId INTEGER,
                isPinned INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL,
                isArchived INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                isLocked INTEGER NOT NULL,
                isEncrypted INTEGER NOT NULL,
                tagsCsv TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                reminderTime INTEGER,
                tags TEXT NOT NULL,
                syncState INTEGER NOT NULL
            )
        """)

        sqlite.execSQL("""
            CREATE TABLE folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // Legacy tables that must NOT be dropped
        sqlite.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, noteId INTEGER NOT NULL, uri TEXT NOT NULL)")
        sqlite.execSQL("CREATE TABLE todo (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, noteId INTEGER NOT NULL, text TEXT NOT NULL, isDone INTEGER NOT NULL)")
        sqlite.execSQL("CREATE TABLE note_tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, noteId INTEGER NOT NULL, tag TEXT NOT NULL)")
        sqlite.execSQL("CREATE TABLE note_links (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceNoteId INTEGER NOT NULL, targetTitle TEXT NOT NULL, targetNoteId INTEGER)")

        // Insert mock v2 records
        sqlite.execSQL("""
            INSERT INTO folders (id, name, color, createdAt) 
            VALUES (1, 'Engineering', '#FFB200', 1700000000)
        """)

        sqlite.execSQL("""
            INSERT INTO notes (id, uuid, title, content, previewText, folderId, isPinned, isFavorite, isArchived, isDeleted, isLocked, isEncrypted, tagsCsv, createdAt, updatedAt, reminderTime, tags, syncState)
            VALUES (10, 'legacy-uuid-10', 'System Architecture', 'Content of architecture', 'Preview', 1, 1, 1, 0, 0, 0, 0, '', 1700000000, 1700005000, NULL, '', 0)
        """)

        sqlite.execSQL("""
            INSERT INTO notes (id, uuid, title, content, previewText, folderId, isPinned, isFavorite, isArchived, isDeleted, isLocked, isEncrypted, tagsCsv, createdAt, updatedAt, reminderTime, tags, syncState)
            VALUES (11, 'legacy-uuid-11', 'Deleted Note', 'Deleted content', 'Preview', NULL, 0, 0, 0, 1, 0, 0, '', 1700001000, 1700006000, NULL, '', 0)
        """)

        sqlite.execSQL("INSERT INTO attachments (id, noteId, uri) VALUES (1, 10, 'file:///data/mock.png')")
        sqlite.version = 2
        sqlite.close()

        // 2. Open with SupportSQLiteOpenHelper to run MIGRATION_2_3
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion < 3) {
                        AppDatabase.MIGRATION_2_3.migrate(db)
                    }
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val migratedDb = helper.writableDatabase

        // 3. Verify V2 data preservation and V3 backfills
        val cursorNotes = migratedDb.query("SELECT id, global_id, folder_global_id, revision, deleted_at_utc, isPinned, isFavorite FROM notes WHERE id = 10")
        assertTrue(cursorNotes.moveToFirst())
        assertEquals(10L, cursorNotes.getLong(0))
        assertEquals("legacy-uuid-10", cursorNotes.getString(1)) // backfilled from uuid
        assertNotNull(cursorNotes.getString(2)) // folder_global_id backfilled
        assertEquals(1L, cursorNotes.getLong(3)) // revision = 1
        assertTrue(cursorNotes.isNull(4)) // active note: deleted_at_utc is null
        assertEquals(1, cursorNotes.getInt(5)) // isPinned preserved
        assertEquals(1, cursorNotes.getInt(6)) // isFavorite preserved
        cursorNotes.close()

        // Verify tombstone backfill on deleted note (id = 11)
        val cursorDeleted = migratedDb.query("SELECT id, deleted_at_utc FROM notes WHERE id = 11")
        assertTrue(cursorDeleted.moveToFirst())
        assertEquals(1700006000L, cursorDeleted.getLong(1)) // backfilled from updatedAt
        cursorDeleted.close()

        // Verify legacy tables are intact
        val cursorAttachments = migratedDb.query("SELECT count(*) FROM attachments")
        assertTrue(cursorAttachments.moveToFirst())
        assertEquals(1, cursorAttachments.getInt(0))
        cursorAttachments.close()

        // Verify sync_outbox table exists
        val cursorOutbox = migratedDb.query("SELECT count(*) FROM sync_outbox")
        assertTrue(cursorOutbox.moveToFirst())
        assertEquals(0, cursorOutbox.getInt(0))
        cursorOutbox.close()

        migratedDb.close()
    }

    @Test
    fun testAtomicSyncOutboxEventLogging() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = AppDatabase.getDatabase(context)
        val deviceIdProvider = DeviceIdentityProvider.getInstance(context)
        val noteRepo = LocalNoteRepository(db, deviceIdProvider)
        val syncOutboxDao = db.syncOutboxDao()

        val deviceId = deviceIdProvider.getOrGenerateDeviceId()
        assertNotNull(deviceId)

        // 1. Create Note
        val note = noteRepo.saveNote(
            Note(
                globalId = UUID.randomUUID().toString(),
                title = "Atomic Outbox Test",
                content = "Testing atomic sync log commits."
            )
        )
        assertNotNull(note.globalId)
        assertEquals(1L, note.revision)

        val eventsAfterCreate = syncOutboxDao.getEventsForEntity(note.globalId)
        assertEquals(1, eventsAfterCreate.size)
        val createEvent = eventsAfterCreate[0]
        assertEquals(SyncOperation.CREATE.name, createEvent.operationType)
        assertEquals(1L, createEvent.localRevision)
        assertEquals(deviceId, createEvent.deviceId)
        assertEquals(OutboxStatus.PENDING.name, createEvent.syncStatus)

        // 2. Update Note
        val updatedNote = noteRepo.saveNote(note.copy(content = "Updated content for revision 2."))
        assertEquals(2L, updatedNote.revision)

        val eventsAfterUpdate = syncOutboxDao.getEventsForEntity(note.globalId)
        assertTrue(eventsAfterUpdate.isNotEmpty())
        assertEquals(2L, eventsAfterUpdate[0].localRevision)
    }
}
