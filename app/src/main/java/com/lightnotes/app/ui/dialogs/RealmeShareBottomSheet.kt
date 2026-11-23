package com.lightnotes.app.ui.dialogs

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.R
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.databinding.BottomSheetRealmeShareBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RealmeShareBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRealmeShareBinding? = null
    private val binding get() = _binding!!

    private var currentNote: NoteEntity? = null
    var onOpenPdfExport: (() -> Unit)? = null

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
        val note = currentNote ?: return

        binding.btnShareText.setOnClickListener {
            val shareText = if (note.title.isNotBlank()) "${note.title}\n\n${note.content}" else note.content
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Note"))
            dismiss()
        }

        binding.btnShareImage.setOnClickListener {
            shareAsImage(note)
            dismiss()
        }

        binding.btnShareMarkdown.setOnClickListener {
            shareAsMarkdown(note)
            dismiss()
        }

        binding.btnSharePdf.setOnClickListener {
            dismiss()
            onOpenPdfExport?.invoke()
        }
    }

    private fun shareAsImage(note: NoteEntity) {
        try {
            val width = 1080
            val padding = 64
            val contentWidth = width - (padding * 2)

            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 52f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = ContextCompat.getColor(requireContext(), R.color.text_primary)
            }

            val metaPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 30f
                typeface = Typeface.DEFAULT
                color = ContextCompat.getColor(requireContext(), R.color.text_hint)
            }

            val bodyPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 38f
                typeface = Typeface.DEFAULT
                color = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            }

            val linePaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = 2f
                color = ContextCompat.getColor(requireContext(), R.color.divider)
            }

            val amberPaint = Paint().apply {
                isAntiAlias = true
                color = ContextCompat.getColor(requireContext(), R.color.brand_amber)
            }

            val title = note.title.ifBlank { "Untitled Note" }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault())
            val words = if (note.content.isBlank()) 0 else note.content.trim().split("\\s+".toRegex()).size
            val meta = "${dateFormat.format(Date(note.updatedAt))} | $words words"

            val titleLayout = createStaticLayout(title, titlePaint, contentWidth)
            val bodyLayout = createStaticLayout(note.content, bodyPaint, contentWidth)

            val totalHeight = padding + titleLayout.height + 16 + 40 + 24 + 2 + 32 + bodyLayout.height + 80 + padding
            val clampedHeight = totalHeight.coerceAtLeast(600).coerceAtMost(8000)

            val bitmap = Bitmap.createBitmap(width, clampedHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(ContextCompat.getColor(requireContext(), R.color.card_bg))

            // Amber top accent stripe
            canvas.drawRect(0f, 0f, width.toFloat(), 12f, amberPaint)

            var yOffset = padding.toFloat() + 12f

            // Title
            canvas.save()
            canvas.translate(padding.toFloat(), yOffset)
            titleLayout.draw(canvas)
            canvas.restore()
            yOffset += titleLayout.height + 16

            // Meta
            canvas.drawText(meta, padding.toFloat(), yOffset + 24, metaPaint)
            yOffset += 40 + 20

            // Divider
            canvas.drawLine(padding.toFloat(), yOffset, (width - padding).toFloat(), yOffset, linePaint)
            yOffset += 32

            // Body
            canvas.save()
            canvas.translate(padding.toFloat(), yOffset)
            bodyLayout.draw(canvas)
            canvas.restore()

            // Save to cache
            val imageFile = File(requireContext().cacheDir, "note_share_${note.id}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                imageFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Note Image"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to generate image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAsMarkdown(note: NoteEntity) {
        try {
            val fileName = "${note.title.ifBlank { "note_${note.id}" }.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.md"
            val file = File(requireContext().cacheDir, fileName)
            val fullContent = if (note.title.isNotBlank()) "# ${note.title}\n\n${note.content}" else note.content
            file.writeText(fullContent)

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Markdown"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to export markdown: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.4f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.4f, 0f, true)
        }
    }

    fun setNote(note: NoteEntity) {
        currentNote = note
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(note: NoteEntity): RealmeShareBottomSheet {
            return RealmeShareBottomSheet().apply {
                setNote(note)
            }
        }
    }
}
