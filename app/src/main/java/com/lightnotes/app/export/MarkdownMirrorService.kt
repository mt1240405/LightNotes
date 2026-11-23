package com.lightnotes.app.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lightnotes.app.data.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MarkdownMirrorService {

    fun mirrorNoteToDirectory(context: Context, note: NoteEntity, treeUri: Uri): Boolean {
        return try {
            val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val safeTitle = note.title.ifBlank { "Untitled_${note.id}" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeTitle.md"

            val existingFile = rootDir.findFile(fileName)
            val targetFile = if (existingFile != null) {
                // Conflict safe: only update if note is newer than file
                if (note.updatedAt <= existingFile.lastModified()) {
                    return true // Already up to date
                }
                existingFile
            } else {
                rootDir.createFile("text/markdown", fileName) ?: return false
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val header = "---\ntitle: \"${note.title}\"\nupdated: \"${dateFormat.format(Date(note.updatedAt))}\"\ntags: [${note.tagsCsv}]\n---\n\n"
            val content = header + note.content

            context.contentResolver.openOutputStream(targetFile.uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
