package com.lightnotes.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lightnotes.app.R
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.databinding.BottomSheetBacklinksTocBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class TocItem(
    val level: Int,
    val text: String,
    val lineIndex: Int
)

class BacklinksTocBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBacklinksTocBinding? = null
    private val binding get() = _binding!!

    private var noteId: Long = 0
    private var noteTitle: String = ""
    private var noteContent: String = ""

    var onTocItemClicked: ((TocItem) -> Unit)? = null
    var onBacklinkNoteClicked: ((NoteEntity) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBacklinksTocBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noteId = arguments?.getLong(ARG_NOTE_ID, 0) ?: 0
        noteTitle = arguments?.getString(ARG_NOTE_TITLE, "") ?: ""
        noteContent = arguments?.getString(ARG_NOTE_CONTENT, "") ?: ""

        setupTabs()
        setupToc()
        setupBacklinks()
    }

    private fun setupTabs() {
        binding.tabToc.setOnClickListener {
            binding.tabToc.setBackgroundResource(R.drawable.bg_preview_toggle_active)
            binding.tabToc.setTextColor(resources.getColor(R.color.brand_amber, null))
            binding.tabBacklinks.setBackgroundResource(0)
            binding.tabBacklinks.setTextColor(resources.getColor(R.color.text_secondary, null))

            binding.layoutToc.visibility = View.VISIBLE
            binding.layoutBacklinks.visibility = View.GONE
        }

        binding.tabBacklinks.setOnClickListener {
            binding.tabBacklinks.setBackgroundResource(R.drawable.bg_preview_toggle_active)
            binding.tabBacklinks.setTextColor(resources.getColor(R.color.brand_amber, null))
            binding.tabToc.setBackgroundResource(0)
            binding.tabToc.setTextColor(resources.getColor(R.color.text_secondary, null))

            binding.layoutBacklinks.visibility = View.VISIBLE
            binding.layoutToc.visibility = View.GONE
        }
    }

    private fun setupToc() {
        val tocItems = mutableListOf<TocItem>()
        val lines = noteContent.lines()
        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("### ")) {
                tocItems.add(TocItem(3, trimmed.removePrefix("### ").trim(), idx))
            } else if (trimmed.startsWith("## ")) {
                tocItems.add(TocItem(2, trimmed.removePrefix("## ").trim(), idx))
            } else if (trimmed.startsWith("# ")) {
                tocItems.add(TocItem(1, trimmed.removePrefix("# ").trim(), idx))
            }
        }

        binding.tvTocEmpty.visibility = if (tocItems.isEmpty()) View.VISIBLE else View.GONE
        binding.rvToc.layoutManager = LinearLayoutManager(requireContext())
        binding.rvToc.adapter = TocAdapter(tocItems) { item ->
            onTocItemClicked?.invoke(item)
            dismiss()
        }
    }

    private fun setupBacklinks() {
        binding.rvBacklinks.layoutManager = LinearLayoutManager(requireContext())
        val db = AppDatabase.getDatabase(requireContext())

        lifecycleScope.launch {
            db.noteDao().getBacklinkSourceNotes(noteTitle, noteId).collectLatest { sourceNotes ->
                binding.tabBacklinks.text = "Backlinks (${sourceNotes.size})"
                binding.tvBacklinksEmpty.visibility = if (sourceNotes.isEmpty()) View.VISIBLE else View.GONE

                binding.rvBacklinks.adapter = BacklinksAdapter(sourceNotes) { note ->
                    onBacklinkNoteClicked?.invoke(note)
                    dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_NOTE_ID = "arg_note_id"
        private const val ARG_NOTE_TITLE = "arg_note_title"
        private const val ARG_NOTE_CONTENT = "arg_note_content"

        fun newInstance(noteId: Long, noteTitle: String, noteContent: String): BacklinksTocBottomSheet {
            return BacklinksTocBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_NOTE_ID, noteId)
                    putString(ARG_NOTE_TITLE, noteTitle)
                    putString(ARG_NOTE_CONTENT, noteContent)
                }
            }
        }
    }

    private class TocAdapter(
        private val items: List<TocItem>,
        private val onClick: (TocItem) -> Unit
    ) : RecyclerView.Adapter<TocAdapter.ViewHolder>() {

        class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 14f
                setPadding(24, 16, 24, 16)
                setTextColor(resources.getColor(R.color.text_primary, null))
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val indent = when (item.level) {
                1 -> ""
                2 -> "    "
                else -> "        "
            }
            val prefix = when (item.level) {
                1 -> "H1: "
                2 -> "H2: "
                else -> "H3: "
            }
            holder.view.text = "$indent$prefix${item.text}"
            holder.view.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class BacklinksAdapter(
        private val notes: List<NoteEntity>,
        private val onClick: (NoteEntity) -> Unit
    ) : RecyclerView.Adapter<BacklinksAdapter.ViewHolder>() {

        class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 14f
                setPadding(24, 16, 24, 16)
                setTextColor(resources.getColor(R.color.brand_amber, null))
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = notes[position]
            holder.view.text = "[[ ${note.title.ifBlank { "Untitled Note" }} ]]\n${note.previewText.take(60)}"
            holder.view.setOnClickListener { onClick(note) }
        }

        override fun getItemCount(): Int = notes.size
    }
}
