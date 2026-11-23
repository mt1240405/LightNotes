package com.lightnotes.app.ui.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.databinding.BottomSheetRealmeShareBinding
import java.io.File

class RealmeShareBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRealmeShareBinding? = null
    private val binding get() = _binding!!

    private var noteTitle: String = ""
    private var noteContent: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRealmeShareBinding.inflate(inflater, container, false)
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

            val safeTitle = noteTitle.ifBlank { "note" }
                .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                .take(30)
            val fileName = "${safeTitle}.md"
            val file = File(requireContext().cacheDir, fileName)
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

    private fun stripMarkdown(markdown: String): String {
        var text = markdown
        // Code blocks
        text = text.replace(Regex("```[a-zA-Z0-9_-]*\n?"), "")
        // Inline code
        text = text.replace(Regex("`([^`]+)`"), "$1")
        // Bold & Italic & Strikethrough
        text = text.replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "$1")
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        text = text.replace(Regex("__(.*?)__"), "$1")
        text = text.replace(Regex("\\*(.*?)\\*"), "$1")
        text = text.replace(Regex("_(.*?)_"), "$1")
        text = text.replace(Regex("~~(.*?)~~"), "$1")
        // Headings
        text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
        // Links
        text = text.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1 ($2)")
        // Checklists
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+\\[ \\]\\s*"), "☐ ")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+\\[[xX]\\]\\s*"), "☑ ")
        // Bullet points
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
        // Blockquotes
        text = text.replace(Regex("(?m)^>\\s*"), "")
        // Horizontal rules
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
        fun newInstance(title: String, content: String): RealmeShareBottomSheet {
            return RealmeShareBottomSheet().apply {
                setLiveContent(title, content)
            }
        }
    }
}
