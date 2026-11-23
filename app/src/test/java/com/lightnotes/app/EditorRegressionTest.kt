package com.lightnotes.app

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.data.AppPreferences
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.editor.EditHistoryManager
import com.lightnotes.app.export.WebViewPdfConfig
import com.lightnotes.app.export.WebViewPdfExporter
import com.lightnotes.app.markdown.MarkdownEngine
import com.lightnotes.app.ui.NoteEditorActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorRegressionTest {

    @Test
    fun testMarkdownAndLatexRendering() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val markdownEngine = MarkdownEngine.getInstance(context)
        val textView = TextView(context)

        val markdownWithLatex = """
            # Header 1
            This is **bold** text and an inline equation: ${'$'}E = mc^2${'$'}.
            
            Here is a block formula:
            $${'$'}
            \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            $${'$'}
            
            - [x] Completed task
            - [ ] Pending task
        """.trimIndent()

        // Verify that rendering does not throw any exceptions
        markdownEngine.render(textView, markdownWithLatex)
        assertNotNull(textView.text)
        assertTrue(textView.text.isNotEmpty())
    }

    @Test
    fun testUndoRedoRestoration() {
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

        // Make an edit and commit snapshot
        editText.setText("Second Revision")
        editText.setSelection(editText.text.length)
        historyManager.commitImmediateSnapshot()

        assertTrue(historyManager.canUndo())
        assertFalse(historyManager.canRedo())

        // Perform undo
        historyManager.undo()
        assertEquals("Initial Note Content", editText.text.toString())
        assertTrue(historyManager.canRedo())

        // Perform redo
        historyManager.redo()
        assertEquals("Second Revision", editText.text.toString())
        assertTrue(historyManager.canUndo())
        assertFalse(historyManager.canRedo())
    }

    @Test
    fun testAdvancedModeGating() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppPreferences.setAdvancedMode(context, false)

        val scenario = ActivityScenario.launch(NoteEditorActivity::class.java)
        scenario.onActivity { activity ->
            val advancedFormatting = activity.findViewById<View>(R.id.layoutAdvancedFormatting)
            assertNotNull(advancedFormatting)
            assertEquals(View.GONE, advancedFormatting.visibility)
        }
        scenario.close()
    }

    @Test
    fun testSinglePinIconInEditorHierarchy() {
        val scenario = ActivityScenario.launch(NoteEditorActivity::class.java)
        scenario.onActivity { activity ->
            // Verify that the duplicate btnPin in the toolbar was removed
            val btnPinId = activity.resources.getIdentifier("btnPin", "id", activity.packageName)
            assertTrue("btnPin resource ID should not exist in layouts", btnPinId == 0 || activity.findViewById<View>(btnPinId) == null)

            // Verify that ivInlinePin in the metadata row exists and is unique
            val ivInlinePin = activity.findViewById<ImageView>(R.id.ivInlinePin)
            assertNotNull("ivInlinePin must exist in metadata row", ivInlinePin)

            // Traverse the entire view hierarchy and count all pin-related views
            var pinViewCount = 0
            fun checkView(v: View) {
                if (v.id == R.id.ivInlinePin || v.contentDescription == "Pin Note" || v.contentDescription == "Pin note") {
                    pinViewCount++
                }
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        checkView(v.getChildAt(i))
                    }
                }
            }
            checkView(activity.window.decorView)

            assertEquals("There must be exactly one Pin icon in the editor view hierarchy", 1, pinViewCount)
        }
        scenario.close()
    }

    @Test
    fun testInteractiveCheckboxToggling() {
        val markdown = """
            # Project Plan
            This header and description text must remain untouched.
            
            - [ ] First uncompleted task
            - [x] Second completed task
            * [ ] Third bullet task
            + [X] Fourth plus task
            
            Concluding paragraph with **bold** and `code`.
        """.trimIndent()

        // 1. Toggle 0th task: flips from - [ ] to - [x]
        val toggled0 = MarkdownEngine.toggleTaskItem(markdown, 0)
        assertTrue(toggled0.contains("- [x] First uncompleted task"))
        assertTrue(toggled0.contains("- [x] Second completed task"))
        assertTrue(toggled0.contains("# Project Plan"))
        assertTrue(toggled0.contains("This header and description text must remain untouched."))
        assertTrue(toggled0.contains("Concluding paragraph with **bold** and `code`."))

        // 2. Toggle back 0th task: flips from - [x] to - [ ]
        val toggledBack0 = MarkdownEngine.toggleTaskItem(toggled0, 0)
        assertEquals(markdown, toggledBack0)

        // 3. Toggle 1st task: flips from - [x] to - [ ]
        val toggled1 = MarkdownEngine.toggleTaskItem(markdown, 1)
        assertTrue(toggled1.contains("- [ ] First uncompleted task"))
        assertTrue(toggled1.contains("- [ ] Second completed task"))

        // 4. Toggle 2nd task (* [ ]): flips to * [x]
        val toggled2 = MarkdownEngine.toggleTaskItem(markdown, 2)
        assertTrue(toggled2.contains("* [x] Third bullet task"))

        // 5. Toggle 3rd task (+ [X]): flips to + [ ]
        val toggled3 = MarkdownEngine.toggleTaskItem(markdown, 3)
        assertTrue(toggled3.contains("+ [ ] Fourth plus task"))
    }

    @Test
    fun testPdfExportOledFullBleed() {
        val note = NoteEntity(
            id = 1,
            title = "OLED Physics Research",
            content = "Quantum electrodynamics notes:\n$$ E = mc^2 $$",
            previewText = "Quantum electrodynamics notes",
            updatedAt = System.currentTimeMillis()
        )
        val config = WebViewPdfConfig(
            paperTheme = "Dark OLED"
        )
        val html = WebViewPdfExporter.buildHtmlDocument(note, config)

        assertTrue("Must include @page CSS directive", html.contains("@page"))
        assertTrue("Must include print-color-adjust: exact", html.contains("print-color-adjust: exact !important"))
        assertTrue("Must set background-color to pure OLED black #000000", html.contains("background-color: #000000"))
        assertTrue("Must set text color to #F2F2F5", html.contains("color: #F2F2F5"))
        assertTrue("Must set accent color to #FFB200", html.contains("#FFB200"))
    }

    @Test
    fun testPdfExportLatexAcademicPreset() {
        val note = NoteEntity(
            id = 2,
            title = "Mathematical Methods in Theoretical Physics",
            content = "# Abstract\nThis paper formalizes boundary conditions for Riemannian manifolds.\n\n$$\\nabla_\\mu F^{\\mu\\nu} = J^\\nu$$",
            previewText = "Abstract...",
            updatedAt = System.currentTimeMillis()
        )
        val config = WebViewPdfConfig(
            preset = "LaTeX Academic",
            fontFamily = "LaTeX Serif",
            textAlignment = "Justified"
        )
        val html = WebViewPdfExporter.buildHtmlDocument(note, config)

        assertTrue("Must use Latin Modern Roman or Computer Modern serif font", html.contains("Latin Modern Roman") || html.contains("Computer Modern"))
        assertTrue("Must justify text with auto hyphens", html.contains("text-align: justify") && html.contains("hyphens: auto"))
        assertTrue("Must apply LaTeX 22mm margins", html.contains("22.0mm") || html.contains("22mm"))
        assertTrue("Must include 0.6pt section rule underline", html.contains("0.6pt solid"))
        assertTrue("Must center display math formulas", html.contains(".math-block, .katex-display") && html.contains("text-align: center"))
    }

    @Test
    fun testPdfExportCustomCssInjection() {
        val note = NoteEntity(
            id = 3,
            title = "Custom Styled Paper",
            content = "Two column layout testing content.",
            previewText = "Two column...",
            updatedAt = System.currentTimeMillis()
        )
        val customCssRule = "body { column-count: 2; column-gap: 18pt; } h1, h2 { column-span: all; }"
        val config = WebViewPdfConfig(
            customCss = customCssRule
        )
        val html = WebViewPdfExporter.buildHtmlDocument(note, config)

        assertTrue("Custom CSS rules must be injected into HTML <style> block", html.contains(customCssRule))
    }

    @Test
    fun testPdfExportSvgCheckboxesAndLiveText() {
        val liveTitle = "Live Unsaved Sprint Checklist"
        val liveContent = """
            # Urgent Tasks
            - [ ] First uncompleted sprint goal
            - [x] Second completed sprint goal
            
            Paragraph text describing deliverables.
        """.trimIndent()

        val note = NoteEntity(
            id = 4,
            title = liveTitle,
            content = liveContent,
            previewText = "Urgent Tasks",
            updatedAt = System.currentTimeMillis()
        )
        val html = WebViewPdfExporter.buildHtmlDocument(note, WebViewPdfConfig())

        // 1. Live text reflected
        assertTrue("Live unsaved title must be reflected", html.contains(liveTitle))
        assertTrue("Live unsaved content must be reflected", html.contains("First uncompleted sprint goal"))
        assertTrue("Live unsaved content must be reflected", html.contains("Second completed sprint goal"))

        // 2. Inline SVG checkboxes
        assertTrue("Must render inline SVG checkbox", html.contains("<svg width=\"11pt\" height=\"11pt\""))
        assertTrue("Must have unchecked SVG checkbox", html.contains("task-checkbox unchecked"))
        assertTrue("Must have checked SVG checkbox", html.contains("task-checkbox checked"))

        // 3. Flexbox row and NO negative margins
        assertTrue("Must use flexbox row for task list items", html.contains("display: flex") && html.contains("align-items: flex-start"))
        assertFalse("Must NOT contain margin-left: -20px", html.contains("margin-left: -20px"))
    }
}
