package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long = 0,
    var taskText: String = "",
    var isDone: Boolean = false,
    var orderIndex: Int = 0
)
