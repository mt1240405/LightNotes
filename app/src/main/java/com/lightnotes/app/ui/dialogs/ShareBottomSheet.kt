package com.lightnotes.app.ui.dialogs

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.databinding.BottomSheetShareBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetShareBinding? = null
    private val binding get() = _binding!!

    private var noteTitle: String = ""
    private var noteContent: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnShareText.setOnClickListener {
            shareAsPlainText()
            dismiss()
        }

        binding.btnShareMarkdown.setOnClickListener {
            shareAsMarkdown()
            dismiss()
        }

        binding.btnShareImage.setOnClickListener {
            shareAsImage()
            dismiss()
        }

        binding.btnSharePdf.setOnClickListener {
            shareAsPdf()
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun shareAsPlainText() {
        try {
            val plainBody = stripMarkdown(noteContent)
            val shareText = if (noteTitle.isNotBlank()) {
                "$noteTitle\n\n$plainBody"
            } else {
                plainBody
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (noteTitle.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Note"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAsMarkdown() {
        try {
            val fullContent = if (noteTitle.isNotBlank()) {
                "# $noteTitle\n\n$noteContent"
            } else {
                noteContent
            }

            val safeTitle = getSanitizedTitle()
            val fileName = "${safeTitle}.md"
            val sharedDir = File(requireContext().cacheDir, "shared_notes").apply { mkdirs() }
            val file = File(sharedDir, fileName)
            file.writeText(fullContent)

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (noteTitle.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                }
                putExtra(Intent.EXTRA_TEXT, fullContent)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Markdown Document"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to export markdown: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAsImage() {
        try {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val bgColor = if (isNight) Color.BLACK else Color.WHITE
            val textColor = if (isNight) Color.parseColor("#F5F5F7") else Color.parseColor("#1A1B1E")
            val metaColor = if (isNight) Color.parseColor("#8E8E93") else Color.parseColor("#636773")
            val dividerColor = if (isNight) Color.parseColor("#242426") else Color.parseColor("#EAECEF")

            val cardWidth = 1080
            val padding = 64
            val contentWidth = cardWidth - (padding * 2)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 42f
                isFakeBoldText = true
            }

            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = metaColor
                textSize = 24f
            }

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 30f
            }

            val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date())
            val plainBody = stripMarkdown(noteContent).ifBlank { " " }

            val titleLayout = if (noteTitle.isNotBlank()) {
                StaticLayout.Builder.obtain(noteTitle, 0, noteTitle.length, titlePaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
            } else null

            val metaLayout = StaticLayout.Builder.obtain(dateStr, 0, dateStr.length, metaPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            val bodyLayout = StaticLayout.Builder.obtain(plainBody, 0, plainBody.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.4f)
                .build()

            var totalHeight = padding * 2 + metaLayout.height + 24 + bodyLayout.height
            if (titleLayout != null) {
                totalHeight += titleLayout.height + 28 + 16 // title + margin + divider
            }
            totalHeight = maxOf(totalHeight, 400)

            val bitmap = Bitmap.createBitmap(cardWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(bgColor)

            var currentY = padding.toFloat()

            if (titleLayout != null) {
                canvas.save()
                canvas.translate(padding.toFloat(), currentY)
                titleLayout.draw(canvas)
                canvas.restore()
                currentY += titleLayout.height + 16

                val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dividerColor
                    strokeWidth = 2f
                }
                canvas.drawLine(padding.toFloat(), currentY, (cardWidth - padding).toFloat(), currentY, divPaint)
                currentY += 24
            }

            canvas.save()
            canvas.translate(padding.toFloat(), currentY)
            metaLayout.draw(canvas)
            canvas.restore()
            currentY += metaLayout.height + 24

            canvas.save()
            canvas.translate(padding.toFloat(), currentY)
            bodyLayout.draw(canvas)
            canvas.restore()

            val safeTitle = getSanitizedTitle()
            val sharedDir = File(requireContext().cacheDir, "shared_notes").apply { mkdirs() }
            val imageFile = File(sharedDir, "${safeTitle}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                imageFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (noteTitle.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Note Image"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to generate image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAsPdf() {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard pt
            val pageHeight = 842 // A4 standard pt
            val margin = 48
            val contentWidth = pageWidth - (margin * 2)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 20f
                isFakeBoldText = true
            }

            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
            }

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 11f
            }

            val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date())
            val plainBody = stripMarkdown(noteContent).ifBlank { " " }

            val titleLayout = if (noteTitle.isNotBlank()) {
                StaticLayout.Builder.obtain(noteTitle, 0, noteTitle.length, titlePaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
            } else null

            val metaLayout = StaticLayout.Builder.obtain(dateStr, 0, dateStr.length, metaPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            val bodyLayout = StaticLayout.Builder.obtain(plainBody, 0, plainBody.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.35f)
                .build()

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var currentY = margin.toFloat()

            if (titleLayout != null) {
                canvas.save()
                canvas.translate(margin.toFloat(), currentY)
                titleLayout.draw(canvas)
                canvas.restore()
                currentY += titleLayout.height + 12

                val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    strokeWidth = 1f
                }
                canvas.drawLine(margin.toFloat(), currentY, (pageWidth - margin).toFloat(), currentY, divPaint)
                currentY += 16
            }

            canvas.save()
            canvas.translate(margin.toFloat(), currentY)
            metaLayout.draw(canvas)
            canvas.restore()
            currentY += metaLayout.height + 18

            // Draw body lines with multi-page support
            for (i in 0 until bodyLayout.lineCount) {
                val lineBottom = bodyLayout.getLineBottom(i)
                val lineTop = bodyLayout.getLineTop(i)
                val lineHeight = lineBottom - lineTop

                if (currentY + lineHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = margin.toFloat()
                }

                val lineStart = bodyLayout.getLineStart(i)
                val lineEnd = bodyLayout.getLineEnd(i)
                val lineText = plainBody.substring(lineStart, lineEnd).trimEnd('\n', '\r')
                canvas.drawText(lineText, margin.toFloat(), currentY + (lineHeight * 0.75f), bodyPaint)
                currentY += lineHeight
            }

            pdfDocument.finishPage(page)

            val safeTitle = getSanitizedTitle()
            val sharedDir = File(requireContext().cacheDir, "shared_notes").apply { mkdirs() }
            val pdfFile = File(sharedDir, "${safeTitle}.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                pdfFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (noteTitle.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Note PDF"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to generate PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSanitizedTitle(): String {
        return noteTitle.ifBlank { "note" }
            .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            .take(30)
    }

    private fun stripMarkdown(markdown: String): String {
        var text = markdown
        text = text.replace(Regex("```[a-zA-Z0-9_-]*\n?"), "")
        text = text.replace(Regex("`([^`]+)`"), "$1")
        text = text.replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "$1")
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        text = text.replace(Regex("__(.*?)__"), "$1")
        text = text.replace(Regex("\\*(.*?)\\*"), "$1")
        text = text.replace(Regex("_(.*?)_"), "$1")
        text = text.replace(Regex("~~(.*?)~~"), "$1")
        text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
        text = text.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1 ($2)")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+\\[ \\]\\s*"), "☐ ")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+\\[[xX]\\]\\s*"), "☑ ")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        text = text.replace(Regex("(?m)^>\\s*"), "")
        text = text.replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")
        return text.trim()
    }

    fun setLiveContent(title: String, content: String) {
        this.noteTitle = title
        this.noteContent = content
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(title: String, content: String): ShareBottomSheet {
            return ShareBottomSheet().apply {
                setLiveContent(title, content)
            }
        }
    }
}

/** Backwards compatibility alias */
typealias RealmeShareBottomSheet = ShareBottomSheet
