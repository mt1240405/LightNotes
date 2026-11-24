package com.lightnotes.app.ui.dialogs

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.databinding.BottomSheetShareBinding
import com.lightnotes.app.export.PdfExportHelper
import java.io.File
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
            shareAsPdf(isOledDark = false)
            dismiss()
        }

        binding.btnSharePdfDark.setOnClickListener {
            shareAsPdf(isOledDark = true)
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
            val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date())
            val words = if (noteContent.isBlank()) 0 else noteContent.trim().split(Regex("\\s+")).size
            val safeTitle = getSanitizedTitle()
            val sharedDir = File(requireContext().cacheDir, "shared_notes").apply { mkdirs() }
            val imageFile = File(sharedDir, "${safeTitle}.png")

            PdfExportHelper.exportNoteToImage(
                context = requireContext(),
                title = noteTitle,
                content = noteContent,
                outputFile = imageFile,
                dateStr = dateStr,
                wordCount = words,
                isNight = isNight
            )

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

    private fun shareAsPdf(isOledDark: Boolean = false) {
        try {
            val dateStr = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault()).format(Date())
            val words = if (noteContent.isBlank()) 0 else noteContent.trim().split(Regex("\\s+")).size
            val safeTitle = getSanitizedTitle()
            val sharedDir = File(requireContext().cacheDir, "shared_notes").apply { mkdirs() }
            val fileName = if (isOledDark) "${safeTitle}_dark.pdf" else "${safeTitle}.pdf"
            val pdfFile = File(sharedDir, fileName)

            PdfExportHelper.exportNoteToPdf(
                context = requireContext(),
                title = noteTitle,
                content = noteContent,
                outputFile = pdfFile,
                dateStr = dateStr,
                wordCount = words,
                isOledDark = isOledDark
            )

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                pdfFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (noteTitle.isNotBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, if (isOledDark) "$noteTitle (OLED Dark)" else noteTitle)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserTitle = if (isOledDark) "Share OLED Dark PDF" else "Share Note PDF"
            startActivity(Intent.createChooser(sendIntent, chooserTitle))
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
