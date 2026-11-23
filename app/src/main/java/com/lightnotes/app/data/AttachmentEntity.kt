package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long = 0,
    val filePath: String = "",
    val mimeType: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
