package com.lightnotes.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        FolderEntity::class,
        SyncOutboxEntity::class,
        // Legacy entities preserved for backwards compatibility and zero data loss
        NoteTagEntity::class,
        NoteLinkEntity::class,
        AttachmentEntity::class,
        TodoEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}

                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(content=`notes`, title, content, tagsCsv)")
                try {
                    db.execSQL("INSERT INTO notes_fts(docid, title, content, tagsCsv) SELECT id, title, content, tagsCsv FROM notes")
                } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        tag TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_tag ON note_tags(tag)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_noteId ON note_tags(noteId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceNoteId INTEGER NOT NULL,
                        targetTitle TEXT NOT NULL,
                        targetNoteId INTEGER
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_sourceNoteId ON note_links(sourceNoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_targetTitle ON note_links(targetTitle)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add new sync-ready columns to `notes` table
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN global_id TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN folder_global_id TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at_utc INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                } catch (_: Exception) {}

                // 2. Backfill `global_id`
                try {
                    db.execSQL("UPDATE notes SET global_id = uuid WHERE uuid IS NOT NULL AND length(uuid) > 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("""
                        UPDATE notes SET global_id = (
                            lower(hex(randomblob(4))) || '-' ||
                            lower(hex(randomblob(2))) || '-4' ||
                            substr(lower(hex(randomblob(2))), 2) || '-a' ||
                            substr(lower(hex(randomblob(2))), 2) || '-' ||
                            lower(hex(randomblob(6)))
                        ) WHERE global_id = '' OR global_id IS NULL
                    """)
                } catch (_: Exception) {}

                // 3. Backfill tombstones from legacy isDeleted flag
                try {
                    db.execSQL("UPDATE notes SET deleted_at_utc = updatedAt WHERE isDeleted = 1 AND deleted_at_utc IS NULL")
                } catch (_: Exception) {}

                // 4. Add new columns to `folders` table
                try {
                    db.execSQL("ALTER TABLE folders ADD COLUMN global_id TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE folders ADD COLUMN deleted_at_utc INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE folders ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE folders ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                } catch (_: Exception) {}

                try {
                    db.execSQL("""
                        UPDATE folders SET global_id = (
                            lower(hex(randomblob(4))) || '-' ||
                            lower(hex(randomblob(2))) || '-4' ||
                            substr(lower(hex(randomblob(2))), 2) || '-a' ||
                            substr(lower(hex(randomblob(2))), 2) || '-' ||
                            lower(hex(randomblob(6)))
                        ) WHERE global_id = '' OR global_id IS NULL
                    """)
                } catch (_: Exception) {}

                // 5. Backfill folder_global_id in notes
                try {
                    db.execSQL("""
                        UPDATE notes SET folder_global_id = (
                            SELECT f.global_id FROM folders f WHERE f.id = notes.folderId
                        ) WHERE folderId IS NOT NULL AND (folder_global_id IS NULL OR folder_global_id = '')
                    """)
                } catch (_: Exception) {}

                // 6. Create sync_outbox table & indices
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        eventId TEXT PRIMARY KEY NOT NULL,
                        entityId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        operationType TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        localRevision INTEGER NOT NULL,
                        createdAtUtc INTEGER NOT NULL,
                        payloadJson TEXT NOT NULL,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_syncStatus_createdAtUtc ON sync_outbox(syncStatus, createdAtUtc)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_entityId_localRevision ON sync_outbox(entityId, localRevision)")

                // 7. Refresh FTS table
                try {
                    db.execSQL("DROP TABLE IF EXISTS notes_fts")
                    db.execSQL("CREATE VIRTUAL TABLE notes_fts USING fts4(content=`notes`, title, content, tagsCsv)")
                    db.execSQL("INSERT INTO notes_fts(docid, title, content, tagsCsv) SELECT id, title, content, tagsCsv FROM notes WHERE deleted_at_utc IS NULL")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "light_notes_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
