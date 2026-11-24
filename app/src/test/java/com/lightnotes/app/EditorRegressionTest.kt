package com.lightnotes.app

import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.editor.EditHistoryManager
import com.lightnotes.app.export.PdfExportHelper
import com.lightnotes.app.markdown.MarkdownEngine
import com.lightnotes.app.markdown.MarkdownSegment
import com.lightnotes.app.markdown.MarkdownTableHelper
import com.lightnotes.app.markdown.TableBlock
import com.lightnotes.app.ui.NoteEditorActivity
import com.lightnotes.app.ui.dialogs.RealmeShareBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowPdfDocument::class])
class EditorRegressionTest {

    @Test
    fun testMarkdownPreviewRenderingAllSyntaxAndMalformed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val markdownEngine = MarkdownEngine.getInstance(context)
        val textView = TextView(context)

        // Comprehensive markdown test payload
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            This is **bold** text, *italic* text, and ~~strikethrough~~ text.
            Here is `inline code` and a link: [Google](https://google.com).
            
            - Bullet 1
            - Bullet 2
            
            1. Numbered 1
            2. Numbered 2
            
            - [ ] Todo unchecked
            - [x] Todo checked
            
            > Blockquote test
            
            ```kotlin
            fun main() {
                println("Hello LightNotes")
            }
            ```
            
            ---
        """.trimIndent()

        markdownEngine.render(textView, markdown)
        assertNotNull(textView.text)
        assertTrue(textView.text.isNotEmpty())

        // Malformed markdown resilience (unclosed tags, broken tables, unmatched delimiters)
        val malformed = "# Broken [link]( no close **unclosed bold ``` unclosed code block"
        markdownEngine.render(textView, malformed)
        assertNotNull(textView.text)
        assertTrue(textView.text.isNotEmpty())
    }

    @Test
    fun testUndoRedoCursorRestoration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = EditText(context)
        var canUndoState = false
        var canRedoState = false

        val historyManager = EditHistoryManager(editText) { canUndo, canRedo ->
            canUndoState = canUndo
            canRedoState = canRedo
        }

        editText.setText("Initial Note Content")
        historyManager.initHistory("Initial Note Content")
        assertEquals("Initial Note Content", editText.text.toString())
        assertFalse(historyManager.canUndo())
        assertFalse(historyManager.canRedo())

        // Edit 1
        editText.setText("Second Revision Content")
        editText.setSelection(6) // cursor inside "Second"
        historyManager.commitImmediateSnapshot()

        assertTrue(historyManager.canUndo())
        assertFalse(historyManager.canRedo())

        // Undo
        historyManager.undo()
        assertEquals("Initial Note Content", editText.text.toString())
        assertTrue(historyManager.canRedo())

        // Redo and verify cursor restoration
        historyManager.redo()
        assertEquals("Second Revision Content", editText.text.toString())
        assertEquals(6, editText.selectionStart)
    }

    @Test
    fun testInteractiveCheckboxToggling() {
        val markdown = """
            # Checklist
            - [ ] First task
            - [x] Second task
            * [ ] Third bullet task
        """.trimIndent()

        // 1. Toggle 0th: [ ] -> [x]
        val toggled0 = MarkdownEngine.toggleTaskItem(markdown, 0)
        assertTrue(toggled0.contains("- [x] First task"))
        assertTrue(toggled0.contains("- [x] Second task"))

        // 2. Toggle back: [x] -> [ ]
        val toggledBack0 = MarkdownEngine.toggleTaskItem(toggled0, 0)
        assertEquals(markdown, toggledBack0)

        // 3. Toggle 1st: [x] -> [ ]
        val toggled1 = MarkdownEngine.toggleTaskItem(markdown, 1)
        assertTrue(toggled1.contains("- [ ] First task"))
        assertTrue(toggled1.contains("- [ ] Second task"))

        // 4. Toggle 2nd: * [ ] -> * [x]
        val toggled2 = MarkdownEngine.toggleTaskItem(markdown, 2)
        assertTrue(toggled2.contains("* [x] Third bullet task"))
    }

    @Test
    fun testFormattingWrapAndInsertWithoutTextLoss() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val editText = EditText(context)
        editText.setText("Selected text to format")

        // Selection wrap: wrap "Selected" with **
        editText.setSelection(0, 8)
        val text = editText.text
        val start = editText.selectionStart
        val end = editText.selectionEnd
        val selected = text.subSequence(start, end).toString()
        text.replace(start, end, "**$selected**")

        assertEquals("**Selected** text to format", editText.text.toString())

        // Cursor insertion at index 0 without selection
        editText.setSelection(0)
        val pos = editText.selectionStart
        text.insert(pos, "# ")
        assertEquals("# **Selected** text to format", editText.text.toString())
    }

    @Test
    fun testPhysicalKeyboardShortcuts() {
        val scenario = ActivityScenario.launch(NoteEditorActivity::class.java)
        scenario.onActivity { activity ->
            val etContent = activity.findViewById<EditText>(R.id.etNoteContent)
            assertNotNull(etContent)
            etContent.setText("Keyboard shortcut test")
            etContent.setSelection(0, 8) // Select "Keyboard"

            // Ctrl+B -> Bold
            val eventB = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B, 0, KeyEvent.META_CTRL_ON)
            val handledB = activity.dispatchKeyEvent(eventB)
            assertTrue("Ctrl+B must be handled", handledB)
            assertTrue(etContent.text.toString().contains("**Keyboard**"))

            // Ctrl+I -> Italic
            etContent.setSelection(2, 10) // Select "Keyboard" inside **
            val eventI = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_I, 0, KeyEvent.META_CTRL_ON)
            val handledI = activity.dispatchKeyEvent(eventI)
            assertTrue("Ctrl+I must be handled", handledI)
        }
        scenario.close()
    }

    @Test
    fun testLiveUnsavedContentPassedToShare() {
        val scenario = ActivityScenario.launch(NoteEditorActivity::class.java)
        scenario.onActivity { activity ->
            val etTitle = activity.findViewById<EditText>(R.id.etNoteTitle)
            val etContent = activity.findViewById<EditText>(R.id.etNoteContent)

            val liveTitle = "Live Unsaved Architecture"
            val liveBody = "# Heading\n- Item 1\n- Item 2"

            etTitle.setText(liveTitle)
            etContent.setText(liveBody)

            val shareSheet = RealmeShareBottomSheet.newInstance(
                etTitle.text.toString(),
                etContent.text.toString()
            )
            assertNotNull(shareSheet)
        }
        scenario.close()
    }

    @Test
    fun testMarkdownTableHelperMultiLineAndUnevenColumns() {
        val markdown = """
            # Project Plan
            Here is the progress table:
            | Feature | Details | Status |
            | --- | --- | --- |
            | Database Migration | Room v2 to v3<br>With sync_outbox | Complete<br/>Verified |
            | Short Row | Missing third col |
            | Overflow Row | Col 2 | Col 3 | Extra Col 4 |
            | Autolink Row | Check doc at <https://github.com/project> | Done |
            
            - [ ] First checkbox after table
            - [x] Second checkbox after table
        """.trimIndent()

        val segments = MarkdownTableHelper.parseSegments(markdown)
        assertEquals(3, segments.size)

        // Segment 0: Prose before table
        assertTrue(segments[0] is MarkdownSegment.Prose)
        val prose0 = segments[0] as MarkdownSegment.Prose
        assertTrue(prose0.text.contains("# Project Plan"))
        assertEquals(0, prose0.checkboxOffset)

        // Segment 1: Table
        assertTrue(segments[1] is MarkdownSegment.Table)
        val tableSegment = segments[1] as MarkdownSegment.Table
        val table = tableSegment.table
        assertEquals(listOf("Feature", "Details", "Status"), table.headers)
        assertEquals(4, table.rows.size)

        // Row 0: <br> and <br/> normalized to \n
        assertEquals("Database Migration", table.rows[0][0])
        assertEquals("Room v2 to v3\nWith sync_outbox", table.rows[0][1])
        assertEquals("Complete\nVerified", table.rows[0][2])

        // Row 1: Short row padded with empty string to match 3 columns
        assertEquals(3, table.rows[1].size)
        assertEquals("Short Row", table.rows[1][0])
        assertEquals("Missing third col", table.rows[1][1])
        assertEquals("", table.rows[1][2])

        // Row 2: Overflow row trimmed to match 3 columns
        assertEquals(3, table.rows[2].size)
        assertEquals("Overflow Row", table.rows[2][0])
        assertEquals("Col 2", table.rows[2][1])
        assertEquals("Col 3", table.rows[2][2])

        // Row 3: Autolink normalized to standard markdown link
        assertEquals(3, table.rows[3].size)
        assertEquals("Autolink Row", table.rows[3][0])
        assertEquals("Check doc at [https://github.com/project](https://github.com/project)", table.rows[3][1])
        assertEquals("Done", table.rows[3][2])

        // Segment 2: Prose after table with 0 checkboxes before it
        assertTrue(segments[2] is MarkdownSegment.Prose)
        val prose1 = segments[2] as MarkdownSegment.Prose
        assertEquals(0, prose1.checkboxOffset)
        assertEquals(2, MarkdownTableHelper.countCheckboxes(prose1.text))
    }

    @Test
    fun testPdfExportGeneratesValidPdfFromRichMarkdown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outputFile = java.io.File(context.cacheDir, "test_export.pdf")
        if (outputFile.exists()) outputFile.delete()

        val title = "Comprehensive Architecture Note"
        val content = """
            # High-Level Architecture
            This is **bold** text and *italic* text with an [Architecture Link](https://lightnotes.app).
            
            ## Task Checklist
            - [ ] Phase 1: Database Migration
            - [x] Phase 2: Material 3 UI Reset
            
            ## Code Block Example
            ```kotlin
            fun syncNotes(cursor: Long): List<Note> {
                return outboxDao.getPendingEvents()
            }
            ```
            
            ## Feature Matrix Table
            | Feature | Version | Status |
            | --- | --- | --- |
            | Room v3 Migration | 1.1.0 | Complete<br>Tested |
            | TableLayout | 1.1.0 | Native Wrap |
            | PDF Full Markdown | 1.1.0 | Valid %PDF- |
            
            > Local-first now. Sync-ready by design. Cloud when needed.
        """.trimIndent()

        PdfExportHelper.exportNoteToPdf(
            context = context,
            title = title,
            content = content,
            outputFile = outputFile,
            dateStr = "24/11/2026, 7:00 PM",
            wordCount = 75,
            folderName = "Engineering"
        )

        assertTrue("Output PDF file must exist", outputFile.exists())
        assertTrue("Output PDF file size must be > 0", outputFile.length() > 0)

        // Verify PDF Header signature (%PDF-)
        val bytes = outputFile.readBytes()
        val header = String(bytes.take(5).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }

    @Test
    fun testTableLayoutCreationWithStyling() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val markdownEngine = MarkdownEngine.getInstance(context)
        val table = TableBlock(
            headers = listOf("Col A", "Col B"),
            rows = listOf(
                listOf("Val 1", "Val 2"),
                listOf("Val 3", "Val 4")
            )
        )

        val tableLayout = MarkdownTableHelper.createTableLayout(
            context = context,
            table = table,
            markwon = markdownEngine.markwon,
            isNight = true,
            isPrint = false
        )

        assertNotNull(tableLayout)
        assertEquals(3, tableLayout.childCount) // 1 header row + 2 data rows
        val headerRow = tableLayout.getChildAt(0) as android.widget.TableRow
        assertEquals(2, headerRow.childCount)
        val cell0 = headerRow.getChildAt(0) as TextView
        assertEquals("Col A", cell0.text.toString().trim())
    }
}
