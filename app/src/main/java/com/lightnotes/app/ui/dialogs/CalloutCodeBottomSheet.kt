package com.lightnotes.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.databinding.BottomSheetCalloutCodeBinding

class CalloutCodeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCalloutCodeBinding? = null
    private val binding get() = _binding!!

    var onInsertTemplate: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCalloutCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Callouts
        binding.btnCalloutNote.setOnClickListener { insertAndDismiss("\n> **Note:** ") }
        binding.btnCalloutTip.setOnClickListener { insertAndDismiss("\n> **Tip:** ") }
        binding.btnCalloutImportant.setOnClickListener { insertAndDismiss("\n> **Important:** ") }
        binding.btnCalloutWarning.setOnClickListener { insertAndDismiss("\n> **Warning:** ") }

        // Code Blocks
        binding.btnCodeKotlin.setOnClickListener { insertAndDismiss("\n```kotlin\n\n```\n") }
        binding.btnCodePython.setOnClickListener { insertAndDismiss("\n```python\n\n```\n") }
        binding.btnCodeCpp.setOnClickListener { insertAndDismiss("\n```cpp\n\n```\n") }
        binding.btnCodeJava.setOnClickListener { insertAndDismiss("\n```java\n\n```\n") }
        binding.btnCodeSql.setOnClickListener { insertAndDismiss("\n```sql\n\n```\n") }
        binding.btnCodeJson.setOnClickListener { insertAndDismiss("\n```json\n\n```\n") }
        binding.btnCodeBash.setOnClickListener { insertAndDismiss("\n```bash\n\n```\n") }

        // Inline & Footnotes
        binding.btnInlineCode.setOnClickListener { insertAndDismiss("`code`") }
        binding.btnStrikethrough.setOnClickListener { insertAndDismiss("~~strikethrough~~") }
        binding.btnFootnote.setOnClickListener { insertAndDismiss("[^1]\n\n[^1]: Footnote details") }
    }

    private fun insertAndDismiss(template: String) {
        onInsertTemplate?.invoke(template)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
