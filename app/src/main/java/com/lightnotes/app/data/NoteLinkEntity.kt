package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "note_links",
    indices = [
        Index(value = ["sourceNoteId"]),
        Index(value = ["targetTitle"])
    ]
)
data class NoteLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceNoteId: Long,
    val targetTitle: String,
    val targetNoteId: Long? = null
)
