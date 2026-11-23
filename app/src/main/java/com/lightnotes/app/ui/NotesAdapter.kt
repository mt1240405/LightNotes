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
    private val onNoteLongClicked: (Note) -> Boolean
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private val colorOsInterpolator = PathInterpolator(0.3f, 0.0f, 0.1f, 1.0f)

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
                onNoteClicked(note, binding.cardNote)
            }
            binding.cardNote.setOnLongClickListener {
                onNoteLongClicked(note)
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
