package com.lightnotes.app.ui.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.R
import com.lightnotes.app.databinding.BottomSheetMathStudioBinding
import com.lightnotes.app.markdown.MarkdownEngine

class MathStudioBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMathStudioBinding? = null
    private val binding get() = _binding!!

    var onMathGenerated: ((String) -> Unit)? = null

    private data class MathSnippet(val label: String, val template: String)

    private val snippetCategories = mapOf(
        "sums_limits" to listOf(
            MathSnippet("\\sum_{i=1}^n x_i", "\\sum_{i=1}^{n} x_i"),
            MathSnippet("\\sum_{k=0}^\\infty a_k", "\\sum_{k=0}^{\\infty} a_k"),
            MathSnippet("\\prod_{i=1}^n x_i", "\\prod_{i=1}^{n} x_i"),
            MathSnippet("\\lim_{x \\to 0}", "\\lim_{x \\to 0} f(x)"),
            MathSnippet("\\lim_{n \\to \\infty}", "\\lim_{n \\to \\infty} a_n"),
            MathSnippet("\\limsup", "\\limsup_{n \\to \\infty} x_n"),
            MathSnippet("\\liminf", "\\liminf_{n \\to \\infty} x_n")
        ),
        "calculus" to listOf(
            MathSnippet("\\int f(x)dx", "\\int f(x) \\, dx"),
            MathSnippet("\\int_a^b f(x)dx", "\\int_{a}^{b} f(x) \\, dx"),
            MathSnippet("\\iint_D dA", "\\iint_{D} f(x, y) \\, dA"),
            MathSnippet("\\oint_C F \\cdot dr", "\\oint_{C} \\mathbf{F} \\cdot d\\mathbf{r}"),
            MathSnippet("\\frac{df}{dx}", "\\frac{df}{dx}"),
            MathSnippet("\\frac{\\partial f}{\\partial x}", "\\frac{\\partial f}{\\partial x}"),
            MathSnippet("\\nabla f", "\\nabla f"),
            MathSnippet("\\nabla \\times F", "\\nabla \\times \\mathbf{F}"),
            MathSnippet("\\nabla \\cdot F", "\\nabla \\cdot \\mathbf{F}")
        ),
        "domains_sets" to listOf(
            MathSnippet("x \\in \\mathbb{R}", "x \\in \\mathbb{R}"),
            MathSnippet("\\mathbb{N}, \\mathbb{Z}, \\mathbb{Q}, \\mathbb{R}, \\mathbb{C}", "\\mathbb{N}, \\mathbb{Z}, \\mathbb{Q}, \\mathbb{R}, \\mathbb{C}"),
            MathSnippet("A \\subset B", "A \\subset B"),
            MathSnippet("A \\cup B", "A \\cup B"),
            MathSnippet("A \\cap B", "A \\cap B"),
            MathSnippet("\\emptyset", "\\emptyset"),
            MathSnippet("\\forall x \\in S", "\\forall x \\in S"),
            MathSnippet("\\exists y", "\\exists y \\in T"),
            MathSnippet("A \\setminus B", "A \\setminus B")
        ),
        "functions" to listOf(
            MathSnippet("Piecewise", "f(x) = \\begin{cases} x^2 & x \\ge 0 \\\\ -x & x < 0 \\end{cases}"),
            MathSnippet("(f \\circ g)(x)", "(f \\circ g)(x)"),
            MathSnippet("f^{-1}(x)", "f^{-1}(x)"),
            MathSnippet("f: A \\to B", "f: A \\to B"),
            MathSnippet("\\operatorname{sgn}(x)", "\\operatorname{sgn}(x)"),
            MathSnippet("\\lfloor x \\rfloor", "\\lfloor x \\rfloor"),
            MathSnippet("\\lceil x \\rceil", "\\lceil x \\rceil")
        ),
        "polynomials" to listOf(
            MathSnippet("P(x) = \\sum a_k x^k", "P(x) = \\sum_{k=0}^{n} a_k x^k"),
            MathSnippet("Quadratic Formula", "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
            MathSnippet("Binomial Theorem", "(x + y)^n = \\sum_{k=0}^{n} \\binom{n}{k} x^{n-k} y^k"),
            MathSnippet("Polynomial General", "a_n x^n + a_{n-1} x^{n-1} + \\dots + a_1 x + a_0"),
            MathSnippet("\\binom{n}{k}", "\\binom{n}{k} = \\frac{n!}{k!(n-k)!}")
        ),
        "complex" to listOf(
            MathSnippet("z = a + bi", "z = a + bi"),
            MathSnippet("Euler's Identity", "e^{i\\pi} + 1 = 0"),
            MathSnippet("Polar Form", "z = r(\\cos \\theta + i \\sin \\theta) = r e^{i\\theta}"),
            MathSnippet("Conjugate \\bar{z}", "\\bar{z} = a - bi"),
            MathSnippet("Modulus |z|", "|z| = \\sqrt{a^2 + b^2}"),
            MathSnippet("\\operatorname{Re}(z), \\operatorname{Im}(z)", "\\operatorname{Re}(z), \\operatorname{Im}(z)"),
            MathSnippet("\\operatorname{Arg}(z)", "\\operatorname{Arg}(z)")
        )
    )

    private var activeCategoryKey = "sums_limits"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMathStudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etMathInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.rgMathMode.setOnCheckedChangeListener { _, _ ->
            updatePreview()
        }

        setupCategoryTabs()
        renderCategoryChips(activeCategoryKey)

        binding.btnClearMath.setOnClickListener {
            binding.etMathInput.setText("")
        }

        binding.btnInsertMath.setOnClickListener {
            val formula = binding.etMathInput.text.toString().trim()
            if (formula.isNotEmpty()) {
                val isBlock = binding.rbBlockMath.isChecked
                val formatted = if (isBlock) {
                    "\n$$\n$formula\n$$\n"
                } else {
                    "$$formula$"
                }
                onMathGenerated?.invoke(formatted)
            }
            dismiss()
        }
    }

    private fun setupCategoryTabs() {
        val tabs = listOf(
            binding.tabSumsLimits to "sums_limits",
            binding.tabCalculus to "calculus",
            binding.tabDomainsSets to "domains_sets",
            binding.tabFunctions to "functions",
            binding.tabPolynomials to "polynomials",
            binding.tabComplex to "complex"
        )

        for ((tabView, key) in tabs) {
            tabView.setOnClickListener {
                activeCategoryKey = key
                updateTabStyles(tabs, key)
                renderCategoryChips(key)
            }
        }
    }

    private fun updateTabStyles(tabs: List<Pair<TextView, String>>, activeKey: String) {
        val amberColor = ContextCompat.getColor(requireContext(), R.color.brand_amber)
        val textPrimaryColor = ContextCompat.getColor(requireContext(), R.color.text_primary)

        for ((tabView, key) in tabs) {
            if (key == activeKey) {
                tabView.setBackgroundResource(R.drawable.bg_preview_toggle_active)
                tabView.setTextColor(amberColor)
            } else {
                tabView.setBackgroundResource(R.drawable.bg_preview_toggle_inactive)
                tabView.setTextColor(textPrimaryColor)
            }
        }
    }

    private fun renderCategoryChips(categoryKey: String) {
        val container = binding.layoutChipsContainer
        container.removeAllViews()

        val snippets = snippetCategories[categoryKey] ?: emptyList()
        val density = resources.displayMetrics.density
        val marginEndPx = (6 * density).toInt()
        val heightPx = (34 * density).toInt()
        val paddingHorizontalPx = (10 * density).toInt()

        for (snippet in snippets) {
            val button = Button(requireContext(), null, android.R.attr.borderlessButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightPx
                ).apply {
                    marginEnd = marginEndPx
                }
                setBackgroundResource(R.drawable.bg_preview_toggle_inactive)
                setPadding(paddingHorizontalPx, 0, paddingHorizontalPx, 0)
                text = snippet.label
                isAllCaps = false
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setOnClickListener {
                    insertTemplate(snippet.template)
                }
            }
            container.addView(button)
        }
    }

    private fun insertTemplate(template: String) {
        val start = binding.etMathInput.selectionStart.coerceAtLeast(0)
        val end = binding.etMathInput.selectionEnd.coerceAtLeast(0)
        val editable = binding.etMathInput.text
        if (editable != null) {
            editable.replace(start, end, template)
            binding.etMathInput.setSelection(start + template.length)
        } else {
            binding.etMathInput.setText(template)
            binding.etMathInput.setSelection(binding.etMathInput.text.length)
        }
        binding.etMathInput.requestFocus()
    }

    private fun updatePreview() {
        val formula = binding.etMathInput.text.toString().trim()
        if (formula.isEmpty()) {
            binding.tvMathPreview.text = "(Type or tap a notation chip above to preview)"
            return
        }
        val isBlock = binding.rbBlockMath.isChecked
        val md = if (isBlock) "$$\n$formula\n$$" else "$$formula$"
        MarkdownEngine.getInstance(requireContext()).render(binding.tvMathPreview, md)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): MathStudioBottomSheet = MathStudioBottomSheet()
    }
}
