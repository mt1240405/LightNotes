package com.lightnotes.app.core.search

import com.lightnotes.app.core.model.Note

object SearchRanker {

    fun rankNotes(query: String, notes: List<Note>): List<Note> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || notes.isEmpty()) return notes

        val queryLower = trimmed.lowercase()
        val terms = queryLower.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return notes

        val scored = mutableListOf<Pair<Note, Long>>()

        for (note in notes) {
            val titleLower = note.title.lowercase()
            val contentLower = note.content.lowercase()

            var score = 0L
            var allMatched = true

            for (term in terms) {
                val inTitle = titleLower.indexOf(term)
                val inContent = contentLower.indexOf(term)

                if (inTitle == -1 && inContent == -1) {
                    allMatched = false
                    break
                }

                if (inTitle != -1) {
                    // Title match: highest priority
                    score += if (inTitle == 0) 1000L else 600L
                }
                if (inContent != -1) {
                    // Body match
                    score += 150L
                }
            }

            if (allMatched && score > 0) {
                // Exact title match bonus
                if (titleLower == queryLower) score += 2000L
                // Pinned note priority
                if (note.isPinned) score += 300L
                // Favorite priority
                if (note.isFavorite) score += 100L
                // Recency factor (normalized)
                val recencyScore = (note.updatedAtUtc / 1_000_000L) % 1000L
                score += recencyScore

                scored.add(Pair(note, score))
            }
        }

        scored.sortByDescending { it.second }
        return scored.map { it.first }
    }
}
