package com.lightnotes.app.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.databinding.BottomSheetAiAssistBinding
import kotlinx.coroutines.launch

class AiAssistBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAiAssistBinding? = null
    private val binding get() = _binding!!

    private var targetText: String = ""
    private var isSelection: Boolean = false
    private var generatedResult: String = ""

    var onReplaceListener: ((String) -> Unit)? = null
    var onInsertBelowListener: ((String) -> Unit)? = null

    companion object {
        private const val ARG_TARGET_TEXT = "arg_target_text"
        private const val ARG_IS_SELECTION = "arg_is_selection"

        fun newInstance(text: String, isSelection: Boolean): AiAssistBottomSheet {
            return AiAssistBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_TEXT, text)
                    putBoolean(ARG_IS_SELECTION, isSelection)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetText = arguments?.getString(ARG_TARGET_TEXT) ?: ""
        isSelection = arguments?.getBoolean(ARG_IS_SELECTION, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAiAssistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTargetScope.text = if (isSelection) {
            "Operating on selected text (${targetText.length} characters)"
        } else {
            "Operating on full note content (${targetText.length} characters)"
        }

        binding.btnCloseAi.setOnClickListener {
            dismiss()
        }

        setupQuickActions()
        setupResultActions()
    }

    private fun setupQuickActions() {
        binding.btnActionGrammar.setOnClickListener {
            executeTask(
                "You are a professional editor. Correct all grammar, punctuation, and typos while preserving tone. Output ONLY the corrected text without any chat or preamble.",
                "Fix grammar and spelling:"
            )
        }

        binding.btnActionSummarize.setOnClickListener {
            executeTask(
                "You are an executive summarizer. Produce a clear, high-density summary of the note using bullet points where appropriate. Output ONLY the summary.",
                "Summarize this text:"
            )
        }

        binding.btnActionContinue.setOnClickListener {
            executeTask(
                "You are a writing assistant. Continue writing seamlessly in the exact style, tone, and perspective of the note.",
                "Continue writing from here:"
            )
        }

        binding.btnActionConcise.setOnClickListener {
            executeTask(
                "You are a concise editor. Remove fluff, passive voice, and wordiness while keeping all essential information. Output ONLY the concise version.",
                "Make this text concise and punchy:"
            )
        }

        binding.btnActionChecklist.setOnClickListener {
            executeTask(
                "You are an organizer. Convert the key items, tasks, or ideas in this text into a clean Markdown checklist using '- [ ] ' syntax. Output ONLY the checklist.",
                "Convert into a Markdown checklist:"
            )
        }

        binding.btnActionMarkdown.setOnClickListener {
            executeTask(
                "You are a Markdown and LaTeX specialist. Format this text with clean headings, bold terms, blockquotes, and LaTeX mathematics enclosed in '$...$' or '$$...$$'. Output ONLY the formatted Markdown.",
                "Format as clean Markdown and LaTeX:"
            )
        }

        binding.btnActionCustom.setOnClickListener {
            binding.layoutCustomPrompt.visibility =
                if (binding.layoutCustomPrompt.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.btnSubmitCustomPrompt.setOnClickListener {
            val customPrompt = binding.etCustomPrompt.text.toString().trim()
            if (customPrompt.isNotBlank()) {
                executeTask(
                    "You are a versatile writing assistant. Follow the user's instructions precisely. Output ONLY the resulting text without conversational fluff.",
                    customPrompt
                )
            } else {
                Toast.makeText(requireContext(), "Please enter prompt instructions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeTask(systemInstruction: String, userPrompt: String) {
        binding.layoutAiLoading.visibility = View.VISIBLE
        binding.layoutAiResult.visibility = View.GONE
        binding.layoutCustomPrompt.visibility = View.GONE

        lifecycleScope.launch {
            val result = AiService.executeAiTask(
                requireContext(),
                systemInstruction,
                userPrompt,
                targetText
            )

            binding.layoutAiLoading.visibility = View.GONE

            result.onSuccess { output ->
                generatedResult = output
                binding.tvAiOutput.text = output
                binding.layoutAiResult.visibility = View.VISIBLE
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.localizedMessage ?: "AI execution failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupResultActions() {
        binding.btnAiReplace.setOnClickListener {
            if (generatedResult.isNotBlank()) {
                onReplaceListener?.invoke(generatedResult)
                dismiss()
            }
        }

        binding.btnAiInsertBelow.setOnClickListener {
            if (generatedResult.isNotBlank()) {
                onInsertBelowListener?.invoke(generatedResult)
                dismiss()
            }
        }

        binding.btnAiCopy.setOnClickListener {
            if (generatedResult.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AI Output", generatedResult)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
