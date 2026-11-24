package com.lightnotes.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lightnotes.app.R
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.databinding.ItemNoteCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val onNoteClicked: (Note, View) -> Unit,
    private val onNoteLongClicked: ((Note) -> Boolean)? = null
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private val colorOsInterpolator = PathInterpolator(0.3f, 0.0f, 0.1f, 1.0f)

    var isSelectionMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    val selectedNoteIds = mutableSetOf<String>()
    var onSelectionChanged: ((count: Int) -> Unit)? = null

    fun startSelection(note: Note) {
        isSelectionMode = true
        selectedNoteIds.clear()
        selectedNoteIds.add(note.globalId)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedNoteIds.size)
    }

    fun toggleSelection(note: Note) {
        if (selectedNoteIds.contains(note.globalId)) {
            selectedNoteIds.remove(note.globalId)
        } else {
            selectedNoteIds.add(note.globalId)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedNoteIds.size)
    }

    fun selectAll(notes: List<Note>) {
        if (selectedNoteIds.size == notes.size) {
            selectedNoteIds.clear()
        } else {
            selectedNoteIds.clear()
            selectedNoteIds.addAll(notes.map { it.globalId })
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedNoteIds.size)
    }

    fun clearSelection() {
        selectedNoteIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedNotes(): List<Note> {
        return currentList.filter { selectedNoteIds.contains(it.globalId) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
        animateItem(holder.itemView, position)
    }

    private fun animateItem(view: View, position: Int) {
        view.alpha = 0f
        view.translationY = 30f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240)
            .setStartDelay((position % 10) * 20L)
            .setInterpolator(colorOsInterpolator)
            .start()
    }

    inner class NoteViewHolder(private val binding: ItemNoteCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        @SuppressLint("ClickableViewAccessibility")
        fun bind(note: Note) {
            val context = binding.root.context
            val displayTitle = if (note.title.isNotBlank()) note.title else "Untitled Note"
            binding.tvNoteTitle.text = displayTitle

            if (note.previewText.isNotBlank()) {
                binding.tvNotePreview.visibility = View.VISIBLE
                binding.tvNotePreview.text = note.previewText
            } else if (note.content.isNotBlank()) {
                binding.tvNotePreview.visibility = View.VISIBLE
                binding.tvNotePreview.text = note.content.take(150).replace("\n", " ")
            } else {
                binding.tvNotePreview.visibility = View.GONE
            }

            binding.tvNoteDate.text = dateFormat.format(Date(note.updatedAtUtc))

            // Folded-corner Amber Bookmark Badge on Pinned Cards
            binding.ivPinnedBadge.visibility = if (note.isPinned) View.VISIBLE else View.GONE

            // Checklist Progress Calculation
            val content = note.content
            val uncheckedCount = countMatches(content, "- [ ]")
            val checkedCount = countMatches(content, "- [x]") + countMatches(content, "- [X]")
            val totalChecklist = uncheckedCount + checkedCount

            if (totalChecklist > 0) {
                binding.layoutChecklistChip.visibility = View.VISIBLE
                binding.tvChecklistProgress.text = "$checkedCount/$totalChecklist"

                if (checkedCount == totalChecklist) {
                    binding.layoutChecklistChip.setBackgroundResource(R.drawable.bg_checklist_chip_done)
                    binding.tvChecklistProgress.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_checklist_done_text)
                    )
                    binding.ivChecklistIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.badge_checklist_done_text)
                    )
                } else {
                    binding.layoutChecklistChip.setBackgroundResource(R.drawable.bg_checklist_chip)
                    binding.tvChecklistProgress.setTextColor(
                        ContextCompat.getColor(context, R.color.badge_checklist_text)
                    )
                    binding.ivChecklistIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.badge_checklist_text)
                    )
                }
            } else {
                binding.layoutChecklistChip.visibility = View.GONE
            }

            // Selection state styling
            val isSelected = selectedNoteIds.contains(note.globalId)
            val density = context.resources.displayMetrics.density
            val pad14 = (14 * density).toInt()

            if (isSelectionMode) {
                binding.ivSelectionCheck.visibility = View.VISIBLE
                val padStart = (38 * density).toInt()
                binding.layoutCardContent.setPaddingRelative(padStart, pad14, pad14, pad14)

                if (isSelected) {
                    binding.ivSelectionCheck.setImageResource(R.drawable.ic_check_circle)
                    binding.ivSelectionCheck.setColorFilter(ContextCompat.getColor(context, R.color.brand_amber))
                    binding.cardNote.strokeWidth = (2 * density).toInt()
                    binding.cardNote.strokeColor = ContextCompat.getColor(context, R.color.brand_amber)
                } else {
                    binding.ivSelectionCheck.setImageResource(R.drawable.ic_check_circle_outline)
                    binding.ivSelectionCheck.setColorFilter(ContextCompat.getColor(context, R.color.text_hint))
                    binding.cardNote.strokeWidth = (1 * density).toInt()
                    binding.cardNote.strokeColor = ContextCompat.getColor(context, R.color.card_border)
                }
            } else {
                binding.ivSelectionCheck.visibility = View.GONE
                binding.layoutCardContent.setPaddingRelative(pad14, pad14, pad14, pad14)
                binding.cardNote.strokeWidth = (1 * density).toInt()
                binding.cardNote.strokeColor = ContextCompat.getColor(context, R.color.card_border)
            }

            // Tactile press-scale micro-animation
            binding.cardNote.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(120)
                            .setInterpolator(colorOsInterpolator)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .setInterpolator(colorOsInterpolator)
                            .start()
                    }
                }
                false
            }

            binding.cardNote.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(note)
                } else {
                    onNoteClicked(note, binding.cardNote)
                }
            }
            binding.cardNote.setOnLongClickListener {
                if (isSelectionMode) {
                    toggleSelection(note)
                    true
                } else {
                    if (onNoteLongClicked != null) {
                        onNoteLongClicked.invoke(note)
                    } else {
                        startSelection(note)
                        true
                    }
                }
            }
        }

        private fun countMatches(text: String, pattern: String): Int {
            var count = 0
            var index = 0
            while (true) {
                index = text.indexOf(pattern, index)
                if (index == -1) break
                count++
                index += pattern.length
            }
            return count
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.globalId == newItem.globalId
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}
