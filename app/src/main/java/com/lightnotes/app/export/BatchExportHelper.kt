package com.lightnotes.app.export

import android.content.Context
import com.lightnotes.app.core.model.Note
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BatchExportHelper {

    enum class Format(val title: String, val extension: String) {
        MARKDOWN("Markdown (.md)", "md"),
        PLAIN_TEXT("Plain Text (.txt)", "txt"),
        PDF_LIGHT("PDF (Light / Print)", "pdf"),
        PDF_OLED_DARK("PDF (OLED Dark)", "pdf"),
        IMAGE("High-Res Image (.png)", "png")
    }

    fun sanitizeFilename(title: String, defaultName: String = "Untitled"): String {
        val sanitized = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim()
        return if (sanitized.isBlank()) defaultName else sanitized.take(64)
    }

    fun exportNotesToZip(
        context: Context,
        notes: List<Note>,
        format: Format,
        outputZipFile: File
    ): File {
        val tempDir = File(context.cacheDir, "batch_export_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val usedFilenames = mutableMapOf<String, Int>()
            val exportedFiles = mutableListOf<File>()

            for (note in notes) {
                val baseName = sanitizeFilename(note.title)
                val count = usedFilenames.getOrDefault(baseName, 0)
                usedFilenames[baseName] = count + 1
                val suffix = if (format == Format.PDF_OLED_DARK) "_dark" else ""
                val fileName = if (count == 0) "$baseName$suffix.${format.extension}" else "${baseName}_${count + 1}$suffix.${format.extension}"
                val targetFile = File(tempDir, fileName)

                when (format) {
                    Format.MARKDOWN -> {
                        val content = if (note.title.isNotBlank()) "# ${note.title}\n\n${note.content}" else note.content
                        targetFile.writeText(content)
                    }
                    Format.PLAIN_TEXT -> {
                        val plainBody = stripMarkdown(note.content)
                        val text = if (note.title.isNotBlank()) "${note.title}\n\n$plainBody" else plainBody
                        targetFile.writeText(text)
                    }
                    Format.PDF_LIGHT -> {
                        val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date(note.updatedAtUtc))
                        val words = if (note.content.isBlank()) 0 else note.content.trim().split(Regex("\\s+")).size
                        PdfExportHelper.exportNoteToPdf(
                            context = context,
                            title = note.title,
                            content = note.content,
                            outputFile = targetFile,
                            dateStr = dateStr,
                            wordCount = words,
                            isOledDark = false
                        )
                    }
                    Format.PDF_OLED_DARK -> {
                        val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date(note.updatedAtUtc))
                        val words = if (note.content.isBlank()) 0 else note.content.trim().split(Regex("\\s+")).size
                        PdfExportHelper.exportNoteToPdf(
                            context = context,
                            title = note.title,
                            content = note.content,
                            outputFile = targetFile,
                            dateStr = dateStr,
                            wordCount = words,
                            isOledDark = true
                        )
                    }
                    Format.IMAGE -> {
                        val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date(note.updatedAtUtc))
                        val words = if (note.content.isBlank()) 0 else note.content.trim().split(Regex("\\s+")).size
                        PdfExportHelper.exportNoteToImage(
                            context = context,
                            title = note.title,
                            content = note.content,
                            outputFile = targetFile,
                            dateStr = dateStr,
                            wordCount = words,
                            isNight = false
                        )
                    }
                }
                exportedFiles.add(targetFile)
            }

            outputZipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                val buffer = ByteArray(8192)
                for (file in exportedFiles) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().use { fis ->
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            zos.write(buffer, 0, read)
                        }
                    }
                    zos.closeEntry()
                }
            }

            return outputZipFile
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun stripMarkdown(markdown: String): String {
        return markdown
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("_(.*?)_"), "$1")
            .replace(Regex("~~(.*?)~~"), "$1")
            .replace(Regex("`{1,3}(.*?)`{1,3}"), "$1")
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            .replace(Regex("!\\[(.*?)\\]\\(.*?\\)"), "[Image: $1]")
            .replace(Regex("(?m)^>\\s*"), "")
            .replace(Regex("(?m)^[-*+] \\[[ xX]\\]\\s*"), "")
            .replace(Regex("(?m)^[-*+]\\s+"), "")
            .replace(Regex("(?m)^\\d+\\.\\s+"), "")
            .trim()
    }
}
