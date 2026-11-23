package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "note_tags",
    indices = [
        Index(value = ["tag"]),
        Index(value = ["noteId"])
    ]
)
data class NoteTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val tag: String
)
