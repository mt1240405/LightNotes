package com.lightnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var name: String = "",
    var color: String = "#FFB200",
    var createdAt: Long = System.currentTimeMillis()
)
