package com.lightnotes.app

import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.search.SearchRanker
import com.lightnotes.app.markdown.MarkdownEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceBenchmarkTest {

    @Test
    fun testSearchRankingPerformance1000Notes() {
        val notes = ArrayList<Note>(1200)
        for (i in 0 until 1200) {
            val title = if (i % 50 == 0) "Android Architecture Spec $i" else "Daily Scratchpad Note $i"
            val content = "Extensive body text for note index $i discussing SQLite performance, coroutines, and offline-first storage."
            notes.add(
                Note(
                    globalId = UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    updatedAtUtc = System.currentTimeMillis() - (i * 1000L),
                    isPinned = (i % 20 == 0)
                )
            )
        }

        // Measure search ranking latency across 1,200 notes
        val startTime = System.nanoTime()
        val results = SearchRanker.rankNotes("Architecture", notes)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        assertTrue("Search results must be non-empty", results.isNotEmpty())
        assertTrue("1200 notes ranking must execute in under 100ms (actual: ${elapsedMs}ms)", elapsedMs < 100)
        assertTrue("Top ranked note must match title", results[0].title.contains("Architecture"))
    }

    @Test
    fun testLargeMarkdownRenderingPerformance5000Chars() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val markdownEngine = MarkdownEngine.getInstance(context)
        val textView = TextView(context)

        val sb = StringBuilder()
        sb.append("# High Volume Document Benchmark\n\n")
        for (i in 1..80) {
            sb.append("### Section $i: Performance Evaluation\n")
            sb.append("This is paragraph $i containing **bold formatting**, *italic emphasis*, and `inline code tokens`.\n")
            sb.append("- [ ] Checklist item $i.A\n")
            sb.append("- [x] Completed item $i.B\n")
            sb.append("> Blockquote evaluation statement for section $i.\n\n")
        }
        val largeMarkdown = sb.toString()
        assertTrue("Document must exceed 5,000 characters", largeMarkdown.length >= 5000)

        val startTime = System.nanoTime()
        markdownEngine.render(textView, largeMarkdown)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        assertNotNull(textView.text)
        assertTrue("TextView must contain rendered text", textView.text.isNotEmpty())
        assertTrue("5000-char markdown render must execute in under 1000ms (actual: ${elapsedMs}ms)", elapsedMs < 1000)
    }
}
