package com.lightnotes.app.backup

import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.data.NoteLinkEntity
import com.lightnotes.app.data.NoteTagEntity
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val version: Int = 2,
    val app: String = "LightNotes",
    val exportedAt: Long = System.currentTimeMillis(),
    val notesCount: Int = 0,
    val foldersCount: Int = 0
)

@Serializable
data class NoteBackupPayload(
    val manifest: BackupManifest,
    val notes: List<NoteEntity>,
    val tags: List<NoteTagEntity> = emptyList(),
    val links: List<NoteLinkEntity> = emptyList()
)
