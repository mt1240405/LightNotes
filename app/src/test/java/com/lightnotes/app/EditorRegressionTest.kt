package com.lightnotes.app

import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.editor.EditHistoryManager
import com.lightnotes.app.markdown.MarkdownEngine
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
@Config(sdk = [34])
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
}
