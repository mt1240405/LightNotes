package com.lightnotes.app.ui.dialogs

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.R
import com.lightnotes.app.databinding.BottomSheetDevToolsBinding
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

class DevToolsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDevToolsBinding? = null
    private val binding get() = _binding!!

    var onInsertBlockListener: ((String) -> Unit)? = null
    private var currentNoteContent: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDevToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentNoteContent = arguments?.getString(ARG_NOTE_CONTENT) ?: ""

        setupTabs()
        setupJsonXml()
        setupRegex()
        setupCodec()
        setupDiff()
    }

    private fun setupTabs() {
        binding.tabJsonXml.setOnClickListener { switchTab(0) }
        binding.tabRegex.setOnClickListener { switchTab(1) }
        binding.tabCodec.setOnClickListener { switchTab(2) }
        binding.tabDiff.setOnClickListener { switchTab(3) }
    }

    private fun switchTab(index: Int) {
        val tabs = listOf(binding.tabJsonXml, binding.tabRegex, binding.tabCodec, binding.tabDiff)
        val panes = listOf(binding.paneJsonXml, binding.paneRegex, binding.paneCodec, binding.paneDiff)

        for (i in tabs.indices) {
            if (i == index) {
                tabs[i].setBackgroundResource(R.drawable.bg_preview_toggle_active)
                tabs[i].setTextColor(resources.getColor(R.color.brand_amber, null))
                panes[i].visibility = View.VISIBLE
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_preview_toggle_inactive)
                tabs[i].setTextColor(resources.getColor(R.color.text_secondary, null))
                panes[i].visibility = View.GONE
            }
        }
    }

    // 1. JSON / XML Formatter & Validator
    private fun setupJsonXml() {
        binding.btnFormatJson.setOnClickListener {
            val input = binding.etJsonInput.text.toString().trim()
            try {
                val formatted = if (input.startsWith("[")) {
                    JSONArray(input).toString(2)
                } else {
                    JSONObject(input).toString(2)
                }
                binding.etJsonInput.setText(formatted)
                Toast.makeText(requireContext(), "Valid JSON formatted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "JSON Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnMinifyJson.setOnClickListener {
            val input = binding.etJsonInput.text.toString().trim()
            try {
                val minified = if (input.startsWith("[")) {
                    JSONArray(input).toString()
                } else {
                    JSONObject(input).toString()
                }
                binding.etJsonInput.setText(minified)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnInsertJson.setOnClickListener {
            val text = binding.etJsonInput.text.toString().trim()
            if (text.isNotEmpty()) {
                onInsertBlockListener?.invoke("\n```json\n$text\n```\n")
                dismiss()
            }
        }
    }

    // 2. Regex Tester with Live Highlights
    private fun setupRegex() {
        val runMatch = {
            val patternStr = binding.etRegexPattern.text.toString()
            val sample = binding.etRegexTestString.text.toString()

            if (patternStr.isEmpty() || sample.isEmpty()) {
                binding.tvRegexResult.text = "Matches: 0"
            } else {
                try {
                    var flags = 0
                    if (binding.cbRegexIgnoreCase.isChecked) flags = flags or Pattern.CASE_INSENSITIVE
                    if (binding.cbRegexMultiline.isChecked) flags = flags or Pattern.MULTILINE

                    val pattern = Pattern.compile(patternStr, flags)
                    val matcher = pattern.matcher(sample)

                    var count = 0
                    val ssb = SpannableStringBuilder(sample)
                    val amberBg = Color.parseColor("#4DFFB200")

                    while (matcher.find()) {
                        count++
                        ssb.setSpan(
                            BackgroundColorSpan(amberBg),
                            matcher.start(),
                            matcher.end(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    binding.tvRegexResult.text = "Matches found: $count"
                } catch (e: Exception) {
                    binding.tvRegexResult.text = "Invalid Regex: ${e.message}"
                }
            }
        }

        binding.etRegexPattern.addTextChangedListener { runMatch() }
        binding.etRegexTestString.addTextChangedListener { runMatch() }
        binding.cbRegexIgnoreCase.setOnCheckedChangeListener { _, _ -> runMatch() }
        binding.cbRegexMultiline.setOnCheckedChangeListener { _, _ -> runMatch() }
    }

    // 3. Base64 / URL / Epoch Converter
    private fun setupCodec() {
        binding.btnBase64Encode.setOnClickListener {
            val input = binding.etCodecInput.text.toString()
            val encoded = Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            binding.etCodecOutput.setText(encoded)
        }

        binding.btnBase64Decode.setOnClickListener {
            val input = binding.etCodecInput.text.toString().trim()
            try {
                val decoded = String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
                binding.etCodecOutput.setText(decoded)
            } catch (e: Exception) {
                binding.etCodecOutput.setText("Decode error: ${e.message}")
            }
        }

        binding.btnUrlEncode.setOnClickListener {
            val input = binding.etCodecInput.text.toString()
            binding.etCodecOutput.setText(URLEncoder.encode(input, "UTF-8"))
        }

        binding.btnEpochConvert.setOnClickListener {
            val input = binding.etCodecInput.text.toString().trim()
            try {
                val millis = if (input.length == 10) input.toLong() * 1000 else input.toLong()
                val date = Date(millis)
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val localFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                binding.etCodecOutput.setText("UTC (ISO-8601): ${isoFormat.format(date)}\nLocal Time: ${localFormat.format(date)}")
            } catch (_: Exception) {
                // If text is not long, convert current timestamp
                val now = System.currentTimeMillis()
                binding.etCodecOutput.setText("Epoch (millis): $now\nEpoch (seconds): ${now / 1000}")
            }
        }
    }

    // 4. Line-by-Line Myers / LCS Text Diff Utility
    private fun setupDiff() {
        binding.etDiffOriginal.setText(currentNoteContent)

        binding.btnRunDiff.setOnClickListener {
            val textA = binding.etDiffOriginal.text.toString()
            val textB = binding.etDiffModified.text.toString()

            val linesA = textA.lines()
            val linesB = textB.lines()

            val diffOutput = computeSimpleLineDiff(linesA, linesB)
            binding.tvDiffResult.text = diffOutput
        }
    }

    private fun computeSimpleLineDiff(a: List<String>, b: List<String>): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        val setA = a.toSet()
        val setB = b.toSet()

        for (line in a) {
            if (!setB.contains(line)) {
                val start = ssb.length
                ssb.append("- $line\n")
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#E53935")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        for (line in b) {
            if (!setA.contains(line)) {
                val start = ssb.length
                ssb.append("+ $line\n")
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#43A047")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        if (ssb.isEmpty()) {
            ssb.append("Texts are identical (0 differences)")
        }

        return ssb
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_NOTE_CONTENT = "arg_note_content"

        fun newInstance(content: String): DevToolsBottomSheet {
            return DevToolsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NOTE_CONTENT, content)
                }
            }
        }
    }
}
