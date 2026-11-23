package com.lightnotes.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.R
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.databinding.BottomSheetPdfExportBinding
import com.lightnotes.app.export.WebViewPdfConfig
import com.lightnotes.app.export.WebViewPdfExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PdfExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPdfExportBinding? = null
    private val binding get() = _binding!!

    private var currentNote: NoteEntity? = null
    private val prefsRepo by lazy { PreferencesRepository.getInstance(requireContext()) }

    companion object {
        const val SNIPPET_TWO_COLUMN = "body { column-count: 2; column-gap: 18pt; }\nh1, h2 { column-span: all; }"
        const val SNIPPET_NUMBERED_HEADINGS = "body { counter-reset: h1; }\nh1::before { counter-increment: h1; content: counter(h1) \". \"; }"
        const val SNIPPET_IEEE_TABLES = "th, td { border: 0.75pt solid currentColor; padding: 5pt 7pt; }\nth { font-weight: 700; }"
        const val SNIPPET_DRAFT_WATERMARK = "body::after { content: \"DRAFT\"; position: fixed; top: 45%; left: 25%; font-size: 72pt; opacity: 0.08; transform: rotate(-30deg); }"
        const val SNIPPET_PAGE_BREAK_H1 = "h1 { page-break-before: always; }\nh1:first-of-type { page-break-before: auto; }"

        fun newInstance(note: NoteEntity): PdfExportBottomSheet {
            return PdfExportBottomSheet().apply {
                setNote(note)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPdfExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabNavigation()
        setupPresets()
        setupGeometryControls()
        setupCssSnippets()
        loadSavedPreferences()

        binding.btnGeneratePdf.setOnClickListener {
            handleExportPdf()
        }
    }

    private fun setupTabNavigation() {
        binding.btnTabTypesetting.setOnClickListener {
            binding.layoutTabTypesetting.visibility = View.VISIBLE
            binding.layoutTabAdvancedCss.visibility = View.GONE
            binding.btnTabTypesetting.setBackgroundResource(R.drawable.bg_preview_toggle_active)
            binding.btnTabTypesetting.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_amber))
            binding.btnTabAdvancedCss.background = null
            binding.btnTabAdvancedCss.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }

        binding.btnTabAdvancedCss.setOnClickListener {
            binding.layoutTabTypesetting.visibility = View.GONE
            binding.layoutTabAdvancedCss.visibility = View.VISIBLE
            binding.btnTabAdvancedCss.setBackgroundResource(R.drawable.bg_preview_toggle_active)
            binding.btnTabAdvancedCss.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_amber))
            binding.btnTabTypesetting.background = null
            binding.btnTabTypesetting.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    private fun setupPresets() {
        binding.rgPresets.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPresetLatex -> {
                    binding.rbFontSerif.isChecked = true
                    binding.rbSize11.isChecked = true
                    binding.rbLine155.isChecked = true
                    binding.rbAlignJustified.isChecked = true
                    binding.rbMargin22.isChecked = true
                    binding.layoutCustomMargins.visibility = View.GONE
                }
                R.id.rbPresetModern -> {
                    binding.rbFontSans.isChecked = true
                    binding.rbSize12.isChecked = true
                    binding.rbLine155.isChecked = true
                    binding.rbAlignLeft.isChecked = true
                    binding.rbMargin16.isChecked = true
                    binding.layoutCustomMargins.visibility = View.GONE
                }
                R.id.rbPresetCompact -> {
                    binding.rbFontMono.isChecked = true
                    binding.rbSize10.isChecked = true
                    binding.rbLine135.isChecked = true
                    binding.rbAlignLeft.isChecked = true
                    binding.rbMargin12.isChecked = true
                    binding.layoutCustomMargins.visibility = View.GONE
                }
            }
        }
    }

    private fun setupGeometryControls() {
        binding.rgPageSize.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbSizeCustom) {
                binding.layoutCustomDimensions.visibility = View.VISIBLE
            } else {
                binding.layoutCustomDimensions.visibility = View.GONE
            }
        }

        binding.rgMargins.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbMarginCustom) {
                binding.layoutCustomMargins.visibility = View.VISIBLE
            } else {
                binding.layoutCustomMargins.visibility = View.GONE
            }
        }
    }

    private fun setupCssSnippets() {
        binding.btnSnippetTwoColumn.setOnClickListener {
            toggleSnippet(SNIPPET_TWO_COLUMN, "Two-Column Layout")
        }
        binding.btnSnippetNumberedHeadings.setOnClickListener {
            toggleSnippet(SNIPPET_NUMBERED_HEADINGS, "Numbered H1")
        }
        binding.btnSnippetIeeeTables.setOnClickListener {
            toggleSnippet(SNIPPET_IEEE_TABLES, "IEEE Bordered Tables")
        }
        binding.btnSnippetDraftWatermark.setOnClickListener {
            toggleSnippet(SNIPPET_DRAFT_WATERMARK, "Draft Watermark")
        }
        binding.btnSnippetPageBreakH1.setOnClickListener {
            toggleSnippet(SNIPPET_PAGE_BREAK_H1, "Page Break H1")
        }

        binding.btnResetCss.setOnClickListener {
            binding.etCustomCss.setText("")
            Toast.makeText(requireContext(), "Reset custom CSS", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveCssDefault.setOnClickListener {
            val css = binding.etCustomCss.text.toString().trim()
            val preset = when (binding.rgPresets.checkedRadioButtonId) {
                R.id.rbPresetModern -> "Clean Modern"
                R.id.rbPresetCompact -> "Technical Spec"
                else -> "LaTeX Academic"
            }
            lifecycleScope.launch {
                prefsRepo.setPdfCustomCss(css)
                prefsRepo.setPdfPreset(preset)
                Toast.makeText(requireContext(), "Stylesheet saved as default", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSnippet(snippet: String, name: String) {
        val current = binding.etCustomCss.text.toString()
        if (current.contains(snippet)) {
            val updated = current.replace(snippet, "").trim()
            binding.etCustomCss.setText(updated)
            Toast.makeText(requireContext(), "Removed $name", Toast.LENGTH_SHORT).show()
        } else {
            val prefix = if (current.isNotBlank() && !current.endsWith("\n")) "\n\n" else ""
            binding.etCustomCss.append("$prefix$snippet\n")
            Toast.makeText(requireContext(), "Appended $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedPreferences() {
        lifecycleScope.launch {
            try {
                val savedCss = prefsRepo.pdfCustomCssFlow.first()
                if (savedCss.isNotBlank()) {
                    binding.etCustomCss.setText(savedCss)
                }

                val savedPreset = prefsRepo.pdfPresetFlow.first()
                when (savedPreset) {
                    "Clean Modern" -> binding.rbPresetModern.isChecked = true
                    "Technical Spec" -> binding.rbPresetCompact.isChecked = true
                    else -> binding.rbPresetLatex.isChecked = true
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleExportPdf() {
        val note = currentNote
        if (note == null) {
            Toast.makeText(requireContext(), "No note available to export", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        val preset = when (binding.rgPresets.checkedRadioButtonId) {
            R.id.rbPresetModern -> "Clean Modern"
            R.id.rbPresetCompact -> "Technical Spec"
            else -> "LaTeX Academic"
        }

        val theme = when (binding.rgPaperTheme.checkedRadioButtonId) {
            R.id.rbThemeSepia -> "Warm Sepia"
            R.id.rbThemeDark -> "Dark OLED"
            else -> "Light"
        }

        val pageSize = when (binding.rgPageSize.checkedRadioButtonId) {
            R.id.rbSizeLetter -> "Letter"
            R.id.rbSizeLegal -> "Legal"
            R.id.rbSizeA5 -> "A5"
            R.id.rbSizeCustom -> "Custom"
            else -> "A4"
        }

        val customW = binding.etCustomWidth.text.toString().toFloatOrNull() ?: 210f
        val customH = binding.etCustomHeight.text.toString().toFloatOrNull() ?: 297f

        val (marginTop, marginRight, marginBottom, marginLeft) = when (binding.rgMargins.checkedRadioButtonId) {
            R.id.rbMargin12 -> listOf(12f, 12f, 12f, 12f)
            R.id.rbMargin16 -> listOf(16f, 16f, 16f, 16f)
            R.id.rbMargin22 -> listOf(22f, 22f, 22f, 22f)
            R.id.rbMarginCustom -> listOf(
                binding.etMarginTop.text.toString().toFloatOrNull() ?: 16f,
                binding.etMarginRight.text.toString().toFloatOrNull() ?: 16f,
                binding.etMarginBottom.text.toString().toFloatOrNull() ?: 16f,
                binding.etMarginLeft.text.toString().toFloatOrNull() ?: 16f
            )
            else -> listOf(22f, 22f, 22f, 22f)
        }

        val font = when (binding.rgFontFamily.checkedRadioButtonId) {
            R.id.rbFontSerif -> "LaTeX Serif"
            R.id.rbFontMono -> "Technical Mono"
            else -> "Modern Sans"
        }

        val fontSize = when (binding.rgFontSize.checkedRadioButtonId) {
            R.id.rbSize10 -> 10
            R.id.rbSize11 -> 11
            R.id.rbSize14 -> 14
            R.id.rbSize16 -> 16
            else -> 12
        }

        val lineHeight = when (binding.rgLineHeight.checkedRadioButtonId) {
            R.id.rbLine115 -> 1.15f
            R.id.rbLine135 -> 1.35f
            R.id.rbLine18 -> 1.80f
            else -> 1.60f
        }

        val textAlignment = when (binding.rgTextAlignment.checkedRadioButtonId) {
            R.id.rbAlignLeft -> "Left"
            else -> "Justified"
        }

        val showHeader = binding.switchShowHeader.isChecked
        val showPageNumbers = binding.switchShowPageNumbers.isChecked
        val customCss = binding.etCustomCss.text.toString().trim()

        val config = WebViewPdfConfig(
            preset = preset,
            fontFamily = font,
            fontSizePt = fontSize,
            lineHeight = lineHeight,
            paragraphSpacingPt = 8f,
            textAlignment = textAlignment,
            pageSize = pageSize,
            customWidthMm = customW,
            customHeightMm = customH,
            marginTopMm = marginTop,
            marginRightMm = marginRight,
            marginBottomMm = marginBottom,
            marginLeftMm = marginLeft,
            paperTheme = theme,
            showHeader = showHeader,
            showPageNumbers = showPageNumbers,
            customCss = customCss
        )

        val act = activity as? com.lightnotes.app.ui.NoteEditorActivity
        if (act != null) {
            act.exportPdfWithWebView(note, config)
        } else {
            Toast.makeText(requireContext(), "Preparing PDF export...", Toast.LENGTH_SHORT).show()
            WebViewPdfExporter(requireActivity()).exportToPdf(note, config)
        }
        dismiss()
    }

    fun setNote(note: NoteEntity) {
        currentNote = note
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
