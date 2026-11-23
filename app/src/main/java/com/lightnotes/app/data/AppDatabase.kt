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
        NoteTagEntity::class,
        NoteLinkEntity::class,
        FolderEntity::class,
        AttachmentEntity::class,
        TodoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to notes table if not present
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE notes ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}

                // Create FTS4 table
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts4(content=`notes`, title, content, tagsCsv)")
                try {
                    db.execSQL("INSERT INTO notes_fts(docid, title, content, tagsCsv) SELECT id, title, content, tagsCsv FROM notes")
                } catch (_: Exception) {}

                // Create note_tags table & indices
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        tag TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_tag ON note_tags(tag)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_noteId ON note_tags(noteId)")

                // Create note_links table & indices
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "light_notes_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
