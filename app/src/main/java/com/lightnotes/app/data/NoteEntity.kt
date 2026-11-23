package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    var title: String = "",
    var content: String = "",
    var previewText: String = "",
    var folderId: Long? = null,
    var isPinned: Boolean = false,
    var isFavorite: Boolean = false,
    var isArchived: Boolean = false,
    var isDeleted: Boolean = false,
    var isLocked: Boolean = false,
    var isEncrypted: Boolean = false,
    var tagsCsv: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var reminderTime: Long? = null,
    var tags: String = "",
    var syncState: Int = 0
)
