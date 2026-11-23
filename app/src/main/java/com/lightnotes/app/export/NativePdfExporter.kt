package com.lightnotes.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.markdown.MarkdownEngine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportConfig(
    val pageWidthPt: Int = 595, // A4 preset (595x842)
    val pageHeightPt: Int = 842,
    val marginTopMm: Float = 15f,
    val marginBottomMm: Float = 15f,
    val marginLeftMm: Float = 15f,
    val marginRightMm: Float = 15f,
    val contentScale: Float = 1.0f,
    val showHeader: Boolean = true,
    val showFooter: Boolean = true
) {
    companion object {
        const val MM_TO_PT = 2.83465f

        val PRESET_A4 = Pair(595, 842)
        val PRESET_LETTER = Pair(612, 792)
        val PRESET_LEGAL = Pair(612, 1008)
        val PRESET_A5 = Pair(420, 595)
    }
}

class NativePdfExporter(private val context: Context) {

    fun exportNoteToPdf(
        note: NoteEntity,
        config: PdfExportConfig = PdfExportConfig(),
        outputStream: OutputStream
    ) {
        val pdfDoc = PdfDocument()

        val marginTopPt = config.marginTopMm * PdfExportConfig.MM_TO_PT
        val marginBottomPt = config.marginBottomMm * PdfExportConfig.MM_TO_PT
        val marginLeftPt = config.marginLeftMm * PdfExportConfig.MM_TO_PT
        val marginRightPt = config.marginRightMm * PdfExportConfig.MM_TO_PT

        val printableWidth = (config.pageWidthPt - marginLeftPt - marginRightPt).toInt().coerceAtLeast(100)
        val printableHeight = (config.pageHeightPt - marginTopPt - marginBottomPt).toInt().coerceAtLeast(100)

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 14f * config.contentScale
            color = Color.BLACK
            typeface = Typeface.DEFAULT
        }

        // Render Markdown to Spanned
        val fullText = "# ${note.title}\n\n${note.content}"
        val processed = MarkdownEngine.getInstance(context).preprocessMarkdown(fullText)
        val spanned = android.text.Html.fromHtml(
            processed.replace("\n", "<br>"),
            android.text.Html.FROM_HTML_MODE_COMPACT
        )

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(spanned, 0, spanned.length, textPaint, printableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.35f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                spanned,
                textPaint,
                printableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.35f,
                0f,
                true
            )
        }

        val totalLines = layout.lineCount
        var currentLine = 0
        var pageNumber = 1

        val headerPaint = Paint().apply {
            textSize = 9f
            color = Color.GRAY
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            textSize = 9f
            color = Color.GRAY
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(note.updatedAt))

        // First pass: count total pages for "Page X of Y"
        var tempLine = 0
        var estimatedPages = 0
        while (tempLine < totalLines) {
            val pageStartTop = layout.getLineTop(tempLine)
            var endLine = tempLine
            while (endLine < totalLines && (layout.getLineBottom(endLine) - pageStartTop) <= printableHeight) {
                endLine++
            }
            if (endLine == tempLine) endLine++ // Progress at least one line
            tempLine = endLine
            estimatedPages++
        }
        val totalPages = estimatedPages.coerceAtLeast(1)

        while (currentLine < totalLines) {
            val pageInfo = PdfDocument.PageInfo.Builder(
                config.pageWidthPt,
                config.pageHeightPt,
                pageNumber
            ).create()

            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // Header
            if (config.showHeader) {
                canvas.drawText(note.title.ifBlank { "LightNotes" }, marginLeftPt, marginTopPt - 12f, headerPaint)
                canvas.drawText(dateStr, config.pageWidthPt - marginRightPt - headerPaint.measureText(dateStr), marginTopPt - 12f, headerPaint)
            }

            // Footer
            if (config.showFooter) {
                val pageStr = "Page $pageNumber of $totalPages"
                canvas.drawText(pageStr, config.pageWidthPt / 2f, config.pageHeightPt - marginBottomPt + 16f, footerPaint)
            }

            // Slice content preventing horizontal line slicing across page breaks
            val pageStartTop = layout.getLineTop(currentLine)
            var endLine = currentLine
            while (endLine < totalLines && (layout.getLineBottom(endLine) - pageStartTop) <= printableHeight) {
                endLine++
            }
            if (endLine == currentLine) endLine++

            canvas.save()
            canvas.clipRect(
                marginLeftPt,
                marginTopPt,
                config.pageWidthPt - marginRightPt,
                config.pageHeightPt - marginBottomPt
            )
            canvas.translate(marginLeftPt, marginTopPt - pageStartTop)
            layout.draw(canvas)
            canvas.restore()

            pdfDoc.finishPage(page)
            currentLine = endLine
            pageNumber++
        }

        pdfDoc.writeTo(outputStream)
        pdfDoc.close()
    }
}
