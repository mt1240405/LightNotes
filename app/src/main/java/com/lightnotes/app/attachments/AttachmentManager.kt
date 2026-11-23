package com.lightnotes.app.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object AttachmentManager {

    fun getAttachmentDir(context: Context): File {
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveMediaFile(context: Context, uri: Uri, mimeType: String): Pair<File, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read media URI")

        // Compute SHA-256 for deduplication
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("text") -> "txt"
            mimeType.contains("audio") || mimeType.contains("m4a") -> "m4a"
            else -> "bin"
        }

        val targetFile = File(getAttachmentDir(context), "$sha256.$ext")
        if (!targetFile.exists()) {
            FileOutputStream(targetFile).use { it.write(bytes) }
        }

        return Pair(targetFile, sha256)
    }

    fun renderPdfFirstPage(file: File): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (_: Throwable) {
            null
        }
    }
}
