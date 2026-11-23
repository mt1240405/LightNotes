package com.lightnotes.app.search

import com.lightnotes.app.data.NoteEntity

object NativeSearchOptimizer {

    private var isNativeAvailable = false

    init {
        try {
            System.loadLibrary("notes_search")
            isNativeAvailable = true
        } catch (_: Throwable) {
            isNativeAvailable = false
        }
    }

    private external fun nativeRankNotes(
        query: String,
        titles: Array<String>,
        contents: Array<String>
    ): IntArray

    fun rankNotes(query: String, notes: List<NoteEntity>): List<NoteEntity> {
        if (query.isBlank() || notes.isEmpty()) return notes

        if (isNativeAvailable) {
            try {
                val titles = Array(notes.size) { notes[it].title }
                val contents = Array(notes.size) { notes[it].content }
                val rankedIndices = nativeRankNotes(query, titles, contents)
                if (rankedIndices.isNotEmpty()) {
                    return rankedIndices.map { notes[it] }
                }
            } catch (_: Throwable) {
                // Seamless fallback to pure Kotlin fast ranker
            }
        }

        return fallbackKotlinRank(query, notes)
    }

    private fun fallbackKotlinRank(query: String, notes: List<NoteEntity>): List<NoteEntity> {
        val queryLower = query.lowercase().trim()
        val terms = queryLower.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return notes

        val scored = mutableListOf<Pair<NoteEntity, Int>>()

        for (note in notes) {
            val titleLower = note.title.lowercase()
            val contentLower = note.content.lowercase()

            var score = 0
            var allMatched = true

            for (term in terms) {
                val inTitle = titleLower.indexOf(term)
                val inContent = contentLower.indexOf(term)

                if (inTitle == -1 && inContent == -1) {
                    allMatched = false
                    break
                }

                if (inTitle != -1) {
                    score += if (inTitle == 0) 60 else 35
                }
                if (inContent != -1) {
                    score += 15
                }
            }

            if (allMatched && score > 0) {
                if (titleLower == queryLower) score += 100
                if (note.isPinned) score += 20
                scored.add(Pair(note, score))
            }
        }

        scored.sortByDescending { it.second }
        return scored.map { it.first }
    }
}
