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
                // Step 1: Clean recreation of `folders` table to match canonical Room v3 schema
                val existingFolderCols = mutableSetOf<String>()
                db.query("PRAGMA table_info('folders')").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        existingFolderCols.add(cursor.getString(nameIdx))
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS `folders_temp`")
                db.execSQL("""
                    CREATE TABLE `folders_temp` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `global_id` TEXT NOT NULL DEFAULT '',
                        `name` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `deleted_at_utc` INTEGER,
                        `revision` INTEGER NOT NULL DEFAULT 1,
                        `sync_state` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                    )
                """.trimIndent())

                val globalIdFolderExpr = if ("global_id" in existingFolderCols) {
                    "CASE WHEN global_id IS NOT NULL AND length(global_id) > 0 THEN global_id ELSE (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-a' || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))) END"
                } else {
                    "(lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-a' || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6))))"
                }
                val deletedAtFolderExpr = if ("deleted_at_utc" in existingFolderCols) "deleted_at_utc" else "NULL"
                val revisionFolderExpr = if ("revision" in existingFolderCols) "revision" else "1"
                val syncStateFolderExpr = if ("sync_state" in existingFolderCols) "sync_state" else "'LOCAL_ONLY'"

                db.execSQL("""
                    INSERT INTO `folders_temp` (`id`, `global_id`, `name`, `color`, `createdAt`, `deleted_at_utc`, `revision`, `sync_state`)
                    SELECT `id`, $globalIdFolderExpr, `name`, `color`, `createdAt`, $deletedAtFolderExpr, $revisionFolderExpr, $syncStateFolderExpr
                    FROM `folders`
                """.trimIndent())

                db.execSQL("DROP TABLE `folders`")
                db.execSQL("ALTER TABLE `folders_temp` RENAME TO `folders`")

                // Step 2: Clean recreation of `notes` table to match canonical Room v3 schema
                // (Eliminates legacy v1/v2 columns like `syncState`, normalizes default values, and preserves all user notes)
                val existingNoteCols = mutableSetOf<String>()
                db.query("PRAGMA table_info('notes')").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        existingNoteCols.add(cursor.getString(nameIdx))
                    }
                }

                // Drop legacy triggers if present to avoid mutation conflicts during table recreation
                try { db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE") } catch (_: Exception) {}
                try { db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE") } catch (_: Exception) {}
                try { db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE") } catch (_: Exception) {}
                try { db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT") } catch (_: Exception) {}

                db.execSQL("DROP TABLE IF EXISTS `notes_temp`")
                db.execSQL("""
                    CREATE TABLE `notes_temp` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `global_id` TEXT NOT NULL DEFAULT '',
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `previewText` TEXT NOT NULL,
                        `folderId` INTEGER,
                        `folder_global_id` TEXT,
                        `isPinned` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `isLocked` INTEGER NOT NULL,
                        `isEncrypted` INTEGER NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `reminderTime` INTEGER,
                        `tags` TEXT NOT NULL,
                        `schema_version` INTEGER NOT NULL DEFAULT 1,
                        `revision` INTEGER NOT NULL DEFAULT 1,
                        `deleted_at_utc` INTEGER,
                        `sync_state` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                    )
                """.trimIndent())

                val globalIdNoteExpr = if ("global_id" in existingNoteCols) {
                    "CASE WHEN global_id IS NOT NULL AND length(global_id) > 0 THEN global_id WHEN uuid IS NOT NULL AND length(uuid) > 0 THEN uuid ELSE (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-a' || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))) END"
                } else {
                    "CASE WHEN uuid IS NOT NULL AND length(uuid) > 0 THEN uuid ELSE (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-a' || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))) END"
                }
                val folderGlobalIdExpr = if ("folder_global_id" in existingNoteCols) "folder_global_id" else "NULL"
                val isFavoriteExpr = if ("isFavorite" in existingNoteCols) "isFavorite" else "0"
                val isLockedExpr = if ("isLocked" in existingNoteCols) "isLocked" else "0"
                val tagsCsvExpr = if ("tagsCsv" in existingNoteCols) "tagsCsv" else "''"
                val schemaVerExpr = if ("schema_version" in existingNoteCols) "schema_version" else "1"
                val revisionExpr = if ("revision" in existingNoteCols) "revision" else "1"
                val syncStateExpr = if ("sync_state" in existingNoteCols) "sync_state" else "'LOCAL_ONLY'"
                val deletedAtExpr = if ("deleted_at_utc" in existingNoteCols) {
                    "CASE WHEN deleted_at_utc IS NOT NULL THEN deleted_at_utc WHEN isDeleted = 1 THEN updatedAt ELSE NULL END"
                } else {
                    "CASE WHEN isDeleted = 1 THEN updatedAt ELSE NULL END"
                }

                db.execSQL("""
                    INSERT INTO `notes_temp` (
                        `id`, `uuid`, `global_id`, `title`, `content`, `previewText`,
                        `folderId`, `folder_global_id`, `isPinned`, `isFavorite`, `isArchived`,
                        `isDeleted`, `isLocked`, `isEncrypted`, `tagsCsv`, `createdAt`,
                        `updatedAt`, `reminderTime`, `tags`, `schema_version`, `revision`,
                        `deleted_at_utc`, `sync_state`
                    )
                    SELECT
                        `id`, `uuid`, $globalIdNoteExpr, `title`, `content`, `previewText`,
                        `folderId`, $folderGlobalIdExpr, `isPinned`, $isFavoriteExpr, `isArchived`,
                        `isDeleted`, $isLockedExpr, `isEncrypted`, $tagsCsvExpr, `createdAt`,
                        `updatedAt`, `reminderTime`, `tags`, $schemaVerExpr, $revisionExpr,
                        $deletedAtExpr, $syncStateExpr
                    FROM `notes`
                """.trimIndent())

                db.execSQL("DROP TABLE `notes`")
                db.execSQL("ALTER TABLE `notes_temp` RENAME TO `notes`")

                // Step 3: Backfill folder_global_id in notes from newly structured folders
                try {
                    db.execSQL("""
                        UPDATE notes SET folder_global_id = (
                            SELECT f.global_id FROM folders f WHERE f.id = notes.folderId
                        ) WHERE folderId IS NOT NULL AND (folder_global_id IS NULL OR folder_global_id = '')
                    """.trimIndent())
                } catch (_: Exception) {}

                // Step 4: Create sync_outbox table & indices
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_outbox` (
                        `eventId` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `localRevision` INTEGER NOT NULL,
                        `createdAtUtc` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`eventId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_syncStatus_createdAtUtc` ON `sync_outbox` (`syncStatus`, `createdAtUtc`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityId_localRevision` ON `sync_outbox` (`entityId`, `localRevision`)")

                // Step 5: Ensure legacy tables indices are fully consistent
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_tag` ON `note_tags` (`tag`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_noteId` ON `note_tags` (`noteId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_links_sourceNoteId` ON `note_links` (`sourceNoteId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_links_targetTitle` ON `note_links` (`targetTitle`)")
                } catch (_: Exception) {}

                // Step 6: Refresh FTS virtual table
                try {
                    db.execSQL("DROP TABLE IF EXISTS `notes_fts`")
                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(`title` TEXT NOT NULL, `content` TEXT NOT NULL, `tagsCsv` TEXT NOT NULL, content=`notes`)")
                    db.execSQL("INSERT INTO `notes_fts`(`docid`, `title`, `content`, `tagsCsv`) SELECT `id`, `title`, `content`, `tagsCsv` FROM `notes` WHERE `deleted_at_utc` IS NULL")
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
