package com.lightnotes.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.databinding.BottomSheetTableBuilderBinding

class TableBuilderBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTableBuilderBinding? = null
    private val binding get() = _binding!!

    private var rows = 3
    private var cols = 3

    var onTableGenerated: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTableBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDecRows.setOnClickListener {
            if (rows > 1) {
                rows--
                binding.tvRowCount.text = rows.toString()
            }
        }

        binding.btnIncRows.setOnClickListener {
            if (rows < 8) {
                rows++
                binding.tvRowCount.text = rows.toString()
            }
        }

        binding.btnDecCols.setOnClickListener {
            if (cols > 1) {
                cols--
                binding.tvColCount.text = cols.toString()
            }
        }

        binding.btnIncCols.setOnClickListener {
            if (cols < 6) {
                cols++
                binding.tvColCount.text = cols.toString()
            }
        }

        binding.btnInsertTable.setOnClickListener {
            val alignIndicator = when (binding.rgAlignment.checkedRadioButtonId) {
                binding.rbAlignCenter.id -> ":---:"
                binding.rbAlignRight.id -> "---:"
                else -> ":---"
            }

            val sb = StringBuilder("\n")
            // Header Row
            sb.append("|")
            for (c in 1..cols) {
                sb.append(" Header $c |")
            }
            sb.append("\n|")
            // Separator Row
            for (c in 1..cols) {
                sb.append(" $alignIndicator |")
            }
            sb.append("\n")
            // Data Rows
            for (r in 1..rows) {
                sb.append("|")
                for (c in 1..cols) {
                    sb.append(" Cell $r,$c |")
                }
                sb.append("\n")
            }
            sb.append("\n")

            onTableGenerated?.invoke(sb.toString())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
