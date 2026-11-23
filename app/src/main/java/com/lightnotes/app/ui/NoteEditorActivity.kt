package com.lightnotes.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.lightnotes.app.R
import com.lightnotes.app.ai.AiAssistBottomSheet
import com.lightnotes.app.audio.VoiceNoteRecorder
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.AppPreferences
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.data.NoteLinkEntity
import com.lightnotes.app.data.NoteTagEntity
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.databinding.ActivityNoteEditorBinding
import com.lightnotes.app.editor.EditHistoryManager
import com.lightnotes.app.export.MarkdownMirrorService
import com.lightnotes.app.export.NativePdfExporter
import com.lightnotes.app.export.PdfExportConfig
import com.lightnotes.app.export.WebViewPdfConfig
import com.lightnotes.app.export.WebViewPdfExporter
import com.lightnotes.app.markdown.MarkdownEngine
import io.noties.markwon.ext.tasklist.TaskListSpan
import com.lightnotes.app.security.BiometricHelper
import com.lightnotes.app.sharing.QrCodeHelper
import com.lightnotes.app.ui.dialogs.BacklinksTocBottomSheet
import com.lightnotes.app.ui.dialogs.CalloutCodeBottomSheet
import com.lightnotes.app.ui.dialogs.DevToolsBottomSheet
import com.lightnotes.app.ui.dialogs.MathStudioBottomSheet
import com.lightnotes.app.ui.dialogs.PdfExportBottomSheet
import com.lightnotes.app.ui.dialogs.RealmeShareBottomSheet
import com.lightnotes.app.ui.dialogs.TableBuilderBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        const val EXTRA_BIOMETRIC_VERIFIED = "extra_biometric_verified"
    }

    private lateinit var binding: ActivityNoteEditorBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val prefsRepo by lazy { PreferencesRepository.getInstance(this) }
    private var currentNote: NoteEntity? = null
    private var noteId: Long = 0
    private var folderId: Long? = null

    private var isPreviewMode = false
    private var isPinned = false
    private var isFavorite = false
    private var isLocked = false
    private var isArchived = false
    private var isAdvancedMode = false

    private lateinit var editHistoryManager: EditHistoryManager
    private val voiceRecorder by lazy { VoiceNoteRecorder(this) }

    private val searchMatches = mutableListOf<Int>()
    private var currentSearchIndex = -1
    private var slashPopupWindow: ListPopupWindow? = null
    private var activePrintWebView: WebView? = null

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault())
    private val colorOsInterpolator = PathInterpolator(0.3f, 0.0f, 0.1f, 1.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        HighRefreshRateHelper.applyHighRefreshRate(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0)
        if (intent.hasExtra(EXTRA_FOLDER_ID)) {
            folderId = intent.getLongExtra(EXTRA_FOLDER_ID, 0)
        }

        isAdvancedMode = AppPreferences.isAdvancedMode(this)

        setupWindowInsets()
        setupUI()
        setupFormattingToolbar()
        updateMenuComplexity(isAdvancedMode, animate = false)
        lifecycleScope.launch {
            prefsRepo.isAdvancedModeFlow.collect { isAdvanced ->
                isAdvancedMode = isAdvanced
                AppPreferences.setAdvancedMode(this@NoteEditorActivity, isAdvanced)
                updateMenuComplexity(isAdvanced, animate = false)
            }
        }
        HighRefreshRateHelper.optimizeScrollingContainer(binding.scrollView)
        loadNote()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        HighRefreshRateHelper.applyHighRefreshRate(this)
    }

    private fun setupWindowInsets() {
        val initialScrollLeft = binding.scrollView.paddingLeft
        val initialScrollTop = binding.scrollView.paddingTop
        val initialScrollRight = binding.scrollView.paddingRight
        val fixedBottomPadding = (96 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.editorRoot) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeOpen = insets.isVisible(WindowInsetsCompat.Type.ime()) || ime.bottom > 0

            binding.editorToolbar.setPadding(0, systemBars.top, 0, 0)

            val bottomInset = if (isImeOpen) ime.bottom else systemBars.bottom
            val layoutParams = binding.bottomBarContainer.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.bottomMargin = bottomInset
            binding.bottomBarContainer.layoutParams = layoutParams

            updateToolbarState(isImeOpen)

            binding.scrollView.setPadding(
                initialScrollLeft,
                initialScrollTop,
                initialScrollRight,
                fixedBottomPadding
            )

            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            binding.editorRoot,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val isImeOpen = insets.isVisible(WindowInsetsCompat.Type.ime()) || ime.bottom > 0
                    val bottom = if (isImeOpen) ime.bottom else systemBars.bottom

                    val layoutParams = binding.bottomBarContainer.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.bottomMargin = bottom
                    binding.bottomBarContainer.layoutParams = layoutParams

                    updateToolbarState(isImeOpen)
                    return insets
                }
            }
        )
    }

    private fun updateToolbarState(isImeOpen: Boolean) {
        if (isPreviewMode) {
            binding.bottomBarContainer.visibility = View.GONE
            return
        }
        binding.bottomBarContainer.visibility = View.VISIBLE
        if (isImeOpen) {
            binding.layoutFloatingToolbar.visibility = View.GONE
            binding.layoutDockedToolbarContainer.visibility = View.VISIBLE
        } else {
            binding.layoutFloatingToolbar.visibility = View.VISIBLE
            binding.layoutDockedToolbarContainer.visibility = View.GONE
            binding.layoutFormattingRow.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        editHistoryManager = EditHistoryManager(binding.etNoteContent) { canUndo, canRedo ->
            binding.btnUndo.isEnabled = canUndo
            binding.btnUndo.alpha = if (canUndo) 1.0f else 0.35f
            binding.btnRedo.isEnabled = canRedo
            binding.btnRedo.alpha = if (canRedo) 1.0f else 0.35f
        }

        binding.btnBack.setOnClickListener {
            saveNoteAndFinish()
        }

        binding.ivInlinePin.setOnClickListener {
            togglePin()
        }

        binding.btnShare.setOnClickListener {
            val liveNote = getLiveNote()
            syncLiveNoteToDb(liveNote)
            val sheet = RealmeShareBottomSheet.newInstance(liveNote)
            sheet.onOpenPdfExport = { showPdfExportDialog() }
            sheet.show(supportFragmentManager, "RealmeShareBottomSheet")
        }

        binding.btnToggleMode.setOnClickListener {
            togglePreviewMode()
        }

        binding.btnOverflowMenu.setOnClickListener { view ->
            showOverflowMenu(view)
        }

        binding.btnUndo.setOnClickListener {
            editHistoryManager.undo()
        }

        binding.btnRedo.setOnClickListener {
            editHistoryManager.redo()
        }

        // State A: Floating Toolbar Click Handlers
        binding.btnAiAssistFloating.setOnClickListener { openAiAssistant() }
        binding.btnTaskFloating.setOnClickListener { insertFormattingPrefix("\n- [ ] ") }
        binding.btnVoiceFloating.setOnClickListener { toggleVoiceRecording() }
        binding.btnInsertFloating.setOnClickListener { showQuickInsertDialog() }

        // State B: Docked Toolbar Click Handlers
        binding.btnAiAssistDocked.setOnClickListener { openAiAssistant() }
        binding.btnSlashCommand.setOnClickListener { showSlashCommandPopup(binding.etNoteContent.selectionStart) }
        binding.btnTaskDocked.setOnClickListener { insertFormattingPrefix("\n- [ ] ") }
        binding.btnFormattingAa.setOnClickListener {
            val isRowVisible = binding.layoutFormattingRow.visibility == View.VISIBLE
            binding.layoutFormattingRow.visibility = if (isRowVisible) View.GONE else View.VISIBLE
        }
        binding.btnVoiceDocked.setOnClickListener { toggleVoiceRecording() }
        binding.btnInsertDocked.setOnClickListener { showQuickInsertDialog() }

        // Setup Wikilink navigation
        MarkdownEngine.getInstance(this).onWikilinkClicked = { targetTitle ->
            handleWikilinkClick(targetTitle)
        }

        // Search Bar Handlers
        binding.btnCloseSearch.setOnClickListener {
            binding.layoutSearchBar.visibility = View.GONE
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etSearchQuery.windowToken, 0)
        }
        binding.btnSearchNext.setOnClickListener {
            if (searchMatches.isNotEmpty()) {
                currentSearchIndex = (currentSearchIndex + 1) % searchMatches.size
                jumpToSearchMatch(binding.etSearchQuery.text.length)
            }
        }
        binding.btnSearchPrev.setOnClickListener {
            if (searchMatches.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex <= 0) searchMatches.size - 1 else currentSearchIndex - 1
                jumpToSearchMatch(binding.etSearchQuery.text.length)
            }
        }
        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Floating Selection Bar & ActionMode
        binding.etNoteContent.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                binding.layoutSelectionBar.visibility = View.VISIBLE
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = true
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) {
                binding.layoutSelectionBar.visibility = View.GONE
            }
        }

        binding.swatchAmber.setOnClickListener { applyHighlight("#FFB20040") }
        binding.swatchCoral.setOnClickListener { applyHighlight("#FF453A40") }
        binding.swatchBlue.setOnClickListener { applyHighlight("#0A84FF40") }
        binding.swatchGreen.setOnClickListener { applyHighlight("#30D15840") }
        binding.swatchGray.setOnClickListener { applyHighlight("#8E8E9340") }

        binding.btnSelBold.setOnClickListener { wrapSelection("**", "**") }
        binding.btnSelUnderline.setOnClickListener { wrapSelection("<u>", "</u>") }
        binding.btnSelStrike.setOnClickListener { wrapSelection("~~", "~~") }
        binding.btnSelCode.setOnClickListener { wrapSelection("`", "`") }

        // Tactile press micro-animations
        setupPressMicroAnimation(binding.btnUndo)
        setupPressMicroAnimation(binding.btnRedo)
        setupPressMicroAnimation(binding.btnShare)
        setupPressMicroAnimation(binding.btnOverflowMenu)
        setupPressMicroAnimation(binding.btnToggleMode)

        binding.etNoteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateWordCount()
                checkForSlashCommand(s?.toString() ?: "", binding.etNoteContent.selectionStart)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Interactive Checkboxes in Preview Mode
        binding.tvNoteRendered.isClickable = true
        var downX = 0f
        var downY = 0f
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        binding.tvNoteRendered.setOnTouchListener { view, event ->
            val textView = view as TextView
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    val layout = textView.layout
                    val spanned = textView.text as? Spanned
                    if (layout != null && spanned != null) {
                        val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY
                        if (y in 0 until layout.height) {
                            val line = layout.getLineForVertical(y)
                            val lineStart = layout.getLineStart(line)
                            val lineEnd = layout.getLineEnd(line)
                            val taskSpans = spanned.getSpans(lineStart, lineEnd, TaskListSpan::class.java)
                            if (taskSpans.isNotEmpty()) {
                                return@setOnTouchListener true
                            }
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = Math.abs(event.x - downX)
                    val deltaY = Math.abs(event.y - downY)
                    if (deltaX <= touchSlop && deltaY <= touchSlop) {
                        val layout = textView.layout
                        val spanned = textView.text as? Spanned
                        if (layout != null && spanned != null) {
                            val x = event.x.toInt() - textView.totalPaddingLeft + textView.scrollX
                            val y = event.y.toInt() - textView.totalPaddingTop + textView.scrollY
                            if (y in 0 until layout.height) {
                                val line = layout.getLineForVertical(y)
                                val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                                val clickables = spanned.getSpans(offset, offset, ClickableSpan::class.java)
                                val urlSpans = spanned.getSpans(offset, offset, URLSpan::class.java)
                                if (clickables.isEmpty() && urlSpans.isEmpty()) {
                                    val lineStart = layout.getLineStart(line)
                                    val lineEnd = layout.getLineEnd(line)
                                    val taskSpans = spanned.getSpans(lineStart, lineEnd, TaskListSpan::class.java)
                                    if (taskSpans.isNotEmpty()) {
                                        val tappedSpan = taskSpans.first()
                                        val allTaskSpans = spanned.getSpans(0, spanned.length, TaskListSpan::class.java)
                                            .sortedBy { spanned.getSpanStart(it) }
                                        val targetIndex = allTaskSpans.indexOf(tappedSpan)
                                        if (targetIndex >= 0) {
                                            handleTaskItemToggle(targetIndex)
                                            return@setOnTouchListener true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    false
                }
                else -> false
            }
        }

        updatePinIcon()
    }

    private fun handleTaskItemToggle(taskIndex: Int) {
        val currentContent = binding.etNoteContent.text.toString()
        val updatedContent = MarkdownEngine.toggleTaskItem(currentContent, taskIndex)
        if (updatedContent == currentContent) return

        val scrollY = binding.scrollView.scrollY
        binding.etNoteContent.setText(updatedContent)
        editHistoryManager.commitImmediateSnapshot()

        // Re-render preview immediately
        MarkdownEngine.getInstance(this).render(binding.tvNoteRendered, updatedContent)
        binding.scrollView.post {
            binding.scrollView.scrollY = scrollY
        }

        // Persist to Room so the checked state survives closing and reopening
        val note = currentNote
        if (note != null && note.id > 0) {
            lifecycleScope.launch {
                val updatedNote = note.copy(
                    content = updatedContent,
                    previewText = updatedContent.take(150).replace("\n", " ").trim(),
                    updatedAt = System.currentTimeMillis()
                )
                database.noteDao().update(updatedNote)
                currentNote = updatedNote
            }
        }
    }

    fun exportPdfWithWebView(note: NoteEntity, config: WebViewPdfConfig) {
        val noteToExport = if (note.content.isBlank()) getLiveNote() else note
        Toast.makeText(this, "Preparing PDF export...", Toast.LENGTH_SHORT).show()
        val exporter = WebViewPdfExporter(this)
        exporter.exportToPdf(noteToExport, config) { webView ->
            activePrintWebView = webView
        }
    }

    private fun handleWikilinkClick(targetTitle: String) {
        lifecycleScope.launch {
            val target = database.noteDao().findNoteByTitle(targetTitle)
            if (target != null) {
                val intent = Intent(this@NoteEditorActivity, NoteEditorActivity::class.java).apply {
                    putExtra(EXTRA_NOTE_ID, target.id)
                }
                startActivity(intent)
            } else {
                AlertDialog.Builder(this@NoteEditorActivity)
                    .setTitle("Note Not Found")
                    .setMessage("Note '$targetTitle' does not exist yet. Create it?")
                    .setPositiveButton("Create") { _, _ ->
                        val newNote = NoteEntity(
                            title = targetTitle,
                            content = "# $targetTitle\n\n",
                            previewText = "# $targetTitle",
                            folderId = folderId
                        )
                        lifecycleScope.launch {
                            val newId = database.noteDao().insert(newNote)
                            val intent = Intent(this@NoteEditorActivity, NoteEditorActivity::class.java).apply {
                                putExtra(EXTRA_NOTE_ID, newId)
                            }
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPressMicroAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).setInterpolator(colorOsInterpolator).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).setInterpolator(colorOsInterpolator).start()
                }
            }
            false
        }
    }

    private fun setupFormattingToolbar() {
        // Basic Formatting Row Buttons
        binding.fmtHeading.setOnClickListener { view -> showHeadingMenu(view) }
        binding.fmtBold.setOnClickListener { wrapSelection("**", "**") }
        binding.fmtItalic.setOnClickListener { wrapSelection("*", "*") }
        binding.fmtStrikethrough.setOnClickListener { wrapSelection("~~", "~~") }
        binding.fmtList.setOnClickListener { insertFormattingPrefix("\n- ") }
        binding.fmtNumberedList.setOnClickListener { insertFormattingPrefix("\n1. ") }
        binding.fmtTask.setOnClickListener { insertFormattingPrefix("\n- [ ] ") }
        binding.fmtQuote.setOnClickListener { insertFormattingPrefix("\n> ") }
        binding.fmtLink.setOnClickListener { showLinkDialog() }
        binding.fmtDivider.setOnClickListener { insertTextAtCursor("\n---\n") }

        // Advanced Mode Formatting Buttons
        binding.fmtTable.setOnClickListener {
            val sheet = TableBuilderBottomSheet()
            sheet.onTableGenerated = { tableMarkdown ->
                editHistoryManager.commitImmediateSnapshot()
                insertTextAtCursor(tableMarkdown)
            }
            sheet.show(supportFragmentManager, "TableBuilderBottomSheet")
        }

        binding.fmtMathStudio.setOnClickListener {
            val sheet = MathStudioBottomSheet()
            sheet.onMathGenerated = { mathMarkdown ->
                editHistoryManager.commitImmediateSnapshot()
                insertTextAtCursor(mathMarkdown)
            }
            sheet.show(supportFragmentManager, "MathStudioBottomSheet")
        }

        binding.fmtCalloutsCode.setOnClickListener {
            val sheet = CalloutCodeBottomSheet()
            sheet.onInsertTemplate = { templateMarkdown ->
                editHistoryManager.commitImmediateSnapshot()
                insertTextAtCursor(templateMarkdown)
            }
            sheet.show(supportFragmentManager, "CalloutCodeBottomSheet")
        }

        binding.fmtMermaid.setOnClickListener {
            val mermaidTemplate = "\n```mermaid\ngraph TD\n    A[Start] --> B(Process)\n    B --> C{Decision}\n    C -->|Yes| D[Done]\n    C -->|No| B\n```\n"
            editHistoryManager.commitImmediateSnapshot()
            insertTextAtCursor(mermaidTemplate)
        }
    }

    private fun showHeadingMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Heading 1 ( # )")
        popup.menu.add(0, 2, 1, "Heading 2 ( ## )")
        popup.menu.add(0, 3, 2, "Heading 3 ( ### )")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { insertFormattingPrefix("\n# "); true }
                2 -> { insertFormattingPrefix("\n## "); true }
                3 -> { insertFormattingPrefix("\n### "); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showLinkDialog() {
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val hasSelection = start in 0 until end
        val selectedText = if (hasSelection) {
            binding.etNoteContent.text.subSequence(start, end).toString()
        } else ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        val etText = EditText(this).apply {
            hint = "Link Text"
            setText(selectedText)
            isSingleLine = true
        }

        val etUrl = EditText(this).apply {
            hint = "URL or [[Note Title]]"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }

        if (!hasSelection) layout.addView(etText)
        layout.addView(etUrl)

        AlertDialog.Builder(this)
            .setTitle("Insert Link / Wikilink")
            .setView(layout)
            .setPositiveButton("Insert") { _, _ ->
                val linkTitle = if (hasSelection) selectedText else etText.text.toString().ifBlank { "link" }
                val linkUrl = etUrl.text.toString().trim().ifBlank { "https://" }
                val markdown = if (linkUrl.startsWith("[[") && linkUrl.endsWith("]]")) {
                    linkUrl
                } else {
                    "[$linkTitle]($linkUrl)"
                }

                editHistoryManager.commitImmediateSnapshot()
                val editable = binding.etNoteContent.text ?: return@setPositiveButton
                if (hasSelection) {
                    editable.replace(start, end, markdown)
                    binding.etNoteContent.setSelection(start + markdown.length)
                } else {
                    editable.insert(start, markdown)
                    binding.etNoteContent.setSelection(start + markdown.length)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuickInsertDialog() {
        val options = mutableListOf(
            "Task / Checklist Item",
            "Heading Menu",
            "Divider Line",
            "Link or Wikilink",
            "Voice Note",
            "Insert Timestamp"
        )
        if (isAdvancedMode) {
            options.add("Table Builder")
            options.add("Math Formula Studio")
            options.add("Callout / Code Block")
            options.add("Mermaid Diagram")
        }

        AlertDialog.Builder(this)
            .setTitle("Quick Insert")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Task / Checklist Item" -> insertFormattingPrefix("\n- [ ] ")
                    "Heading Menu" -> showHeadingMenu(binding.bottomBarContainer)
                    "Divider Line" -> insertTextAtCursor("\n---\n")
                    "Link or Wikilink" -> showLinkDialog()
                    "Voice Note" -> toggleVoiceRecording()
                    "Insert Timestamp" -> insertTextAtCursor(dateFormat.format(Date()))
                    "Table Builder" -> {
                        val sheet = TableBuilderBottomSheet()
                        sheet.onTableGenerated = { tableMarkdown ->
                            editHistoryManager.commitImmediateSnapshot()
                            insertTextAtCursor(tableMarkdown)
                        }
                        sheet.show(supportFragmentManager, "TableBuilderBottomSheet")
                    }
                    "Math Formula Studio" -> {
                        val sheet = MathStudioBottomSheet()
                        sheet.onMathGenerated = { mathMarkdown ->
                            editHistoryManager.commitImmediateSnapshot()
                            insertTextAtCursor(mathMarkdown)
                        }
                        sheet.show(supportFragmentManager, "MathStudioBottomSheet")
                    }
                    "Callout / Code Block" -> {
                        val sheet = CalloutCodeBottomSheet()
                        sheet.onInsertTemplate = { templateMarkdown ->
                            editHistoryManager.commitImmediateSnapshot()
                            insertTextAtCursor(templateMarkdown)
                        }
                        sheet.show(supportFragmentManager, "CalloutCodeBottomSheet")
                    }
                    "Mermaid Diagram" -> {
                        val mermaidTemplate = "\n```mermaid\ngraph TD\n    A[Start] --> B(Process)\n    B --> C{Decision}\n    C -->|Yes| D[Done]\n    C -->|No| B\n```\n"
                        editHistoryManager.commitImmediateSnapshot()
                        insertTextAtCursor(mermaidTemplate)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMenuComplexity(isAdvanced: Boolean, animate: Boolean = true) {
        isAdvancedMode = isAdvanced

        if (isAdvanced) {
            if (animate) {
                binding.layoutAdvancedFormatting.alpha = 0f
                binding.layoutAdvancedFormatting.visibility = View.VISIBLE
                binding.layoutAdvancedFormatting.animate().alpha(1f).setDuration(220).setInterpolator(colorOsInterpolator).start()
            } else {
                binding.layoutAdvancedFormatting.visibility = View.VISIBLE
            }
        } else {
            if (animate) {
                binding.layoutAdvancedFormatting.animate().alpha(0f).setDuration(180).withEndAction {
                    binding.layoutAdvancedFormatting.visibility = View.GONE
                }.start()
            } else {
                binding.layoutAdvancedFormatting.visibility = View.GONE
            }
        }
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add(0, 1, 0, "Find in note")
        popup.menu.add(0, 2, 1, if (isPinned) "Unpin note" else "Pin note")
        popup.menu.add(0, 3, 2, "Paper Skin / Tint")
        popup.menu.add(0, 4, 3, "Move to folder")
        popup.menu.add(0, 5, 4, if (isLocked) "Unlock note (Remove protection)" else "Lock note (Biometric)")
        popup.menu.add(0, 6, 5, "Settings")

        val trashTitle = SpannableString("Move to Trash")
        trashTitle.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.color_trash_red)),
            0,
            trashTitle.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        popup.menu.add(0, 7, 6, trashTitle)

        if (isAdvancedMode) {
            popup.menu.add(0, 8, 7, "Developer Workspace (Diff/JSON/Regex)")
            popup.menu.add(0, 9, 8, "Outline & Backlinks")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { showSearchBar(); true }
                2 -> { togglePin(); true }
                3 -> { showPaperSkinDialog(); true }
                4 -> { showMoveFolderDialog(); true }
                5 -> { toggleNoteLock(); true }
                6 -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                7 -> { confirmDeleteNote(); true }
                8 -> { openDevTools(); true }
                9 -> { openBacklinksToc(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showSearchBar() {
        binding.layoutSearchBar.visibility = View.VISIBLE
        binding.etSearchQuery.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
        performSearch(binding.etSearchQuery.text.toString())
    }

    private fun performSearch(query: String) {
        searchMatches.clear()
        currentSearchIndex = -1
        if (query.isBlank()) {
            binding.tvSearchMatches.text = "0/0"
            return
        }
        val content = binding.etNoteContent.text.toString()
        var index = content.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            searchMatches.add(index)
            index = content.indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        if (searchMatches.isNotEmpty()) {
            currentSearchIndex = 0
            jumpToSearchMatch(query.length)
        } else {
            binding.tvSearchMatches.text = "0/0"
        }
    }

    private fun jumpToSearchMatch(queryLen: Int) {
        if (currentSearchIndex in 0 until searchMatches.size) {
            val offset = searchMatches[currentSearchIndex]
            binding.etNoteContent.setSelection(offset, offset + queryLen)
            binding.tvSearchMatches.text = "${currentSearchIndex + 1}/${searchMatches.size}"
            binding.etNoteContent.requestFocus()
        }
    }

    private fun showPaperSkinDialog() {
        val themes = arrayOf(
            "Default Canvas",
            "Warm Sepia (#FAF6EE)",
            "Sage Mint (#F0F7F4)",
            "Lavender Mist (#F0F4FA)",
            "Soft Rose (#FAF0F2)"
        )
        val colorsLight = arrayOf(
            ContextCompat.getColor(this, R.color.bg_main),
            Color.parseColor("#FAF6EE"),
            Color.parseColor("#F0F7F4"),
            Color.parseColor("#F0F4FA"),
            Color.parseColor("#FAF0F2")
        )
        val colorsDark = arrayOf(
            ContextCompat.getColor(this, R.color.bg_main),
            Color.parseColor("#1F1D19"),
            Color.parseColor("#161F1B"),
            Color.parseColor("#181C24"),
            Color.parseColor("#22181B")
        )
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val palette = if (isDark) colorsDark else colorsLight

        AlertDialog.Builder(this)
            .setTitle("Paper Skin / Tint")
            .setItems(themes) { _, which ->
                val chosenColor = palette[which]
                binding.editorRoot.setBackgroundColor(chosenColor)
                binding.scrollView.setBackgroundColor(chosenColor)
                Toast.makeText(this, "Paper theme applied: ${themes[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showMoveFolderDialog() {
        lifecycleScope.launch {
            val folders = database.folderDao().getAllFoldersSync()
            val folderNames = mutableListOf("No Folder / Root")
            folderNames.addAll(folders.map { it.name })

            AlertDialog.Builder(this@NoteEditorActivity)
                .setTitle("Move to Folder")
                .setItems(folderNames.toTypedArray()) { _, which ->
                    folderId = if (which == 0) null else folders[which - 1].id
                    updateWordCount()
                    Toast.makeText(this@NoteEditorActivity, "Moved to ${folderNames[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun applyHighlight(colorHex: String) {
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        if (start in 0 until end) {
            editHistoryManager.commitImmediateSnapshot()
            val editable = binding.etNoteContent.text ?: return
            val selected = editable.subSequence(start, end).toString()
            val replacement = "<mark style=\"background-color: $colorHex;\">$selected</mark>"
            editable.replace(start, end, replacement)
            binding.etNoteContent.setSelection(start + replacement.length)
            binding.layoutSelectionBar.visibility = View.GONE
        }
    }

    private fun checkForSlashCommand(text: String, cursorPos: Int) {
        if (cursorPos <= 0 || cursorPos > text.length) {
            dismissSlashPopup()
            return
        }

        if (text[cursorPos - 1] == '/') {
            val isStartOfLineOrSpace = (cursorPos == 1 || text[cursorPos - 2] == '\n' || text[cursorPos - 2] == ' ')
            if (isStartOfLineOrSpace) {
                showSlashCommandPopup(cursorPos)
                return
            }
        }
        dismissSlashPopup()
    }

    private fun showSlashCommandPopup(cursorPos: Int) {
        if (slashPopupWindow?.isShowing == true) return

        val commands = listOf(
            "H1 - Large Heading",
            "H2 - Medium Heading",
            "To-dos - Checklist Item",
            "Bullet List",
            "Image - Embed Image",
            "Code Block",
            "Divider - Horizontal Rule"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, commands)
        val popup = ListPopupWindow(this).apply {
            anchorView = binding.bottomBarContainer
            setAdapter(adapter)
            width = (240 * resources.displayMetrics.density).toInt()
            isModal = false
            setOnItemClickListener { _, _, position, _ ->
                val editable = binding.etNoteContent.text ?: return@setOnItemClickListener
                val cur = binding.etNoteContent.selectionStart
                if (cur > 0 && cur <= editable.length && editable[cur - 1] == '/') {
                    editable.delete(cur - 1, cur)
                }
                when (position) {
                    0 -> insertTextAtCursor("# ")
                    1 -> insertTextAtCursor("## ")
                    2 -> insertTextAtCursor("- [ ] ")
                    3 -> insertTextAtCursor("- ")
                    4 -> insertTextAtCursor("![Image Description](https://)")
                    5 -> insertTextAtCursor("```\n\n```")
                    6 -> insertTextAtCursor("\n---\n")
                }
                dismiss()
            }
        }
        slashPopupWindow = popup
        popup.show()
    }

    private fun dismissSlashPopup() {
        slashPopupWindow?.dismiss()
        slashPopupWindow = null
    }

    private fun togglePin() {
        isPinned = !isPinned
        updatePinIcon()
        if (currentNote != null) {
            lifecycleScope.launch {
                val updated = currentNote!!.copy(isPinned = isPinned, updatedAt = System.currentTimeMillis())
                database.noteDao().update(updated)
                currentNote = updated
            }
        }
        Toast.makeText(this, if (isPinned) "Note pinned to top" else "Note unpinned", Toast.LENGTH_SHORT).show()
    }

    private fun openDevTools() {
        val sheet = DevToolsBottomSheet.newInstance(binding.etNoteContent.text.toString())
        sheet.onInsertBlockListener = { block ->
            editHistoryManager.commitImmediateSnapshot()
            insertTextAtCursor(block)
        }
        sheet.show(supportFragmentManager, "DevToolsBottomSheet")
    }

    private fun openBacklinksToc() {
        val sheet = BacklinksTocBottomSheet.newInstance(
            noteId,
            binding.etNoteTitle.text.toString(),
            binding.etNoteContent.text.toString()
        )
        sheet.onTocItemClicked = { tocItem ->
            val content = binding.etNoteContent.text.toString()
            val lines = content.lines()
            var offset = 0
            for (i in 0 until tocItem.lineIndex.coerceAtMost(lines.size)) {
                offset += lines[i].length + 1
            }
            binding.etNoteContent.setSelection(offset.coerceIn(0, content.length))
            binding.etNoteContent.requestFocus()
        }
        sheet.onBacklinkNoteClicked = { note ->
            val intent = Intent(this, NoteEditorActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, note.id)
            }
            startActivity(intent)
        }
        sheet.show(supportFragmentManager, "BacklinksTocBottomSheet")
    }

    fun getLiveNote(): NoteEntity {
        val rawTitle = binding.etNoteTitle.text?.toString()?.trim() ?: ""
        val rawContent = if (isPreviewMode) {
            val editContent = binding.etNoteContent.text?.toString() ?: ""
            if (editContent.isNotBlank()) editContent else binding.tvNoteRendered.text?.toString() ?: ""
        } else {
            binding.etNoteContent.text?.toString() ?: ""
        }

        val title = if (rawTitle.isNotEmpty()) {
            rawTitle
        } else {
            val firstLine = rawContent.lines().firstOrNull { it.isNotBlank() } ?: "Untitled Note"
            firstLine.removePrefix("#").removePrefix("##").trim().take(40).ifBlank { "Untitled Note" }
        }

        val preview = rawContent.take(150).replace("\n", " ").trim()
        val now = System.currentTimeMillis()

        val foundTags = Regex("#([A-Za-z0-9_-]+)").findAll(rawContent).map { it.groupValues[1] }.distinct().toList()
        val tagsCsv = foundTags.joinToString(",")

        val note = currentNote?.copy(
            title = title,
            content = rawContent,
            previewText = preview,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isLocked = isLocked,
            isArchived = isArchived,
            tagsCsv = tagsCsv,
            folderId = folderId,
            updatedAt = now
        ) ?: NoteEntity(
            id = noteId,
            title = title,
            content = rawContent,
            previewText = preview,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isLocked = isLocked,
            isArchived = isArchived,
            tagsCsv = tagsCsv,
            folderId = folderId,
            createdAt = now,
            updatedAt = now
        )
        return note
    }

    private fun syncLiveNoteToDb(note: NoteEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedId: Long
                if (note.id > 0) {
                    database.noteDao().update(note)
                    savedId = note.id
                    currentNote = note
                } else {
                    savedId = database.noteDao().insert(note)
                    noteId = savedId
                    currentNote = note.copy(id = savedId)
                }

                // Sync tags
                val foundTags = Regex("#([A-Za-z0-9_-]+)").findAll(note.content).map { it.groupValues[1] }.distinct().toList()
                database.noteDao().deleteTagsForNote(savedId)
                if (foundTags.isNotEmpty()) {
                    database.noteDao().insertTags(foundTags.map { NoteTagEntity(noteId = savedId, tag = it) })
                }

                // Sync backlinks
                val foundLinks = Regex("\\[\\[(.*?)\\]\\]").findAll(note.content).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().toList()
                database.noteDao().deleteLinksForNote(savedId)
                if (foundLinks.isNotEmpty()) {
                    val linkEntities = foundLinks.map { targetTitle ->
                        val target = database.noteDao().findNoteByTitle(targetTitle)
                        NoteLinkEntity(sourceNoteId = savedId, targetTitle = targetTitle, targetNoteId = target?.id)
                    }
                    database.noteDao().insertLinks(linkEntities)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showPdfExportDialog() {
        val liveNote = getLiveNote()
        syncLiveNoteToDb(liveNote)
        val sheet = PdfExportBottomSheet.newInstance(liveNote)
        sheet.show(supportFragmentManager, "PdfExportBottomSheet")
    }

    private fun showQrCodeDialog() {
        val content = binding.etNoteContent.text.toString().take(1200)
        val bitmap = QrCodeHelper.generateQrCode(content, 600)
        if (bitmap != null) {
            val iv = ImageView(this).apply {
                setImageBitmap(bitmap)
                setPadding(32, 32, 32, 32)
            }
            AlertDialog.Builder(this)
                .setTitle("QR Code Snippet")
                .setView(iv)
                .setPositiveButton("Close", null)
                .show()
        } else {
            Toast.makeText(this, "Content too large for QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleVoiceRecording() {
        if (!voiceRecorder.isRecording) {
            try {
                voiceRecorder.startRecording()
                Toast.makeText(this, "Recording voice note...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Mic error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            val duration = voiceRecorder.stopRecording()
            val sec = duration / 1000
            insertTextAtCursor("\n[Voice Note: ${sec}s]\n")
            Toast.makeText(this, "Voice note attached ($sec seconds)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleNoteLock() {
        if (BiometricHelper.canAuthenticate(this)) {
            BiometricHelper.authenticate(
                this,
                title = if (isLocked) "Unlock Note" else "Lock Note",
                onSuccess = {
                    isLocked = !isLocked
                    if (currentNote != null) {
                        lifecycleScope.launch {
                            val updated = currentNote!!.copy(isLocked = isLocked, updatedAt = System.currentTimeMillis())
                            database.noteDao().update(updated)
                            currentNote = updated
                        }
                    }
                    Toast.makeText(this, if (isLocked) "Note is now locked" else "Note unlocked", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(this, "Device biometrics not setup", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAiAssistant() {
        val fullContent = binding.etNoteContent.text.toString()
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd

        val isSelection = (start != end && start >= 0 && end <= fullContent.length)
        val targetText = if (isSelection) fullContent.substring(start, end) else fullContent

        val sheet = AiAssistBottomSheet.newInstance(targetText, isSelection)
        sheet.onReplaceListener = { replacement ->
            editHistoryManager.commitImmediateSnapshot()
            val editable = binding.etNoteContent.text
            if (isSelection && editable != null) {
                editable.replace(start, end, replacement)
            } else {
                binding.etNoteContent.setText(replacement)
            }
        }
        sheet.onInsertBelowListener = { insertText ->
            editHistoryManager.commitImmediateSnapshot()
            val editable = binding.etNoteContent.text
            if (editable != null) {
                val insertPosition = if (isSelection) end else binding.etNoteContent.selectionStart
                editable.insert(insertPosition, "\n\n$insertText")
            }
        }
        sheet.show(supportFragmentManager, "AiAssistBottomSheet")
    }

    private fun loadNote() {
        if (noteId > 0) {
            lifecycleScope.launch {
                val note = database.noteDao().getNoteById(noteId)
                if (note != null) {
                    currentNote = note
                    isPinned = note.isPinned
                    isFavorite = note.isFavorite
                    isLocked = note.isLocked
                    isArchived = note.isArchived
                    folderId = note.folderId

                    val alreadyVerified = intent.getBooleanExtra(EXTRA_BIOMETRIC_VERIFIED, false)
                    if (note.isLocked && !alreadyVerified && BiometricHelper.canAuthenticate(this@NoteEditorActivity)) {
                        BiometricHelper.authenticate(
                            this@NoteEditorActivity,
                            title = "Protected Note",
                            subtitle = "Authenticate to access ${note.title}",
                            onSuccess = {
                                populateNoteUI(note)
                            },
                            onError = {
                                Toast.makeText(this@NoteEditorActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        )
                    } else {
                        populateNoteUI(note)
                    }
                }
            }
        } else {
            binding.tvEditorDate.text = dateFormat.format(Date())
            editHistoryManager.initHistory("")
        }
    }

    private fun populateNoteUI(note: NoteEntity) {
        binding.etNoteTitle.setText(note.title)
        binding.etNoteContent.setText(note.content)
        editHistoryManager.initHistory(note.content)
        updatePinIcon()
        updateWordCount()
    }

    private fun updatePinIcon() {
        if (isPinned) {
            binding.ivInlinePin.setImageResource(R.drawable.ic_pin)
            binding.ivInlinePin.setColorFilter(ContextCompat.getColor(this, R.color.brand_amber))
        } else {
            binding.ivInlinePin.setImageResource(R.drawable.ic_pin_outline)
            binding.ivInlinePin.setColorFilter(ContextCompat.getColor(this, R.color.text_hint))
        }
    }

    private fun togglePreviewMode() {
        isPreviewMode = !isPreviewMode
        if (isPreviewMode) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val focusView = currentFocus ?: binding.etNoteContent
            imm?.hideSoftInputFromWindow(focusView.windowToken, 0)
            binding.etNoteContent.clearFocus()
            binding.etNoteTitle.clearFocus()

            // Contextual Top App Bar for Reading / Preview Mode
            binding.btnUndo.visibility = View.GONE
            binding.btnRedo.visibility = View.GONE
            binding.btnShare.visibility = View.VISIBLE
            binding.btnToggleMode.text = getString(R.string.edit_label)
            binding.btnToggleMode.setIconResource(R.drawable.ic_edit)

            // Hide bottom toolbar completely during reading / preview
            binding.bottomBarContainer.visibility = View.GONE

            val markdownText = binding.etNoteContent.text.toString()
            binding.etNoteContent.animate().alpha(0f).setDuration(120).withEndAction {
                binding.etNoteContent.visibility = View.GONE
                binding.tvNoteRendered.alpha = 0f
                binding.tvNoteRendered.visibility = View.VISIBLE
                MarkdownEngine.getInstance(this).render(binding.tvNoteRendered, markdownText)
                binding.tvNoteRendered.animate().alpha(1f).setDuration(160).setInterpolator(colorOsInterpolator).start()
            }.start()
        } else {
            // Contextual Top App Bar for Editing Mode
            binding.btnUndo.visibility = View.VISIBLE
            binding.btnRedo.visibility = View.VISIBLE
            binding.btnShare.visibility = View.GONE
            binding.btnToggleMode.text = getString(R.string.preview_label)
            binding.btnToggleMode.setIconResource(R.drawable.ic_eye)

            // Restore bottom toolbar
            binding.bottomBarContainer.visibility = View.VISIBLE
            updateToolbarState(isImeOpen = false)

            binding.tvNoteRendered.animate().alpha(0f).setDuration(120).withEndAction {
                binding.tvNoteRendered.visibility = View.GONE
                binding.etNoteContent.alpha = 0f
                binding.etNoteContent.visibility = View.VISIBLE
                binding.etNoteContent.animate().alpha(1f).setDuration(160).setInterpolator(colorOsInterpolator).start()
            }.start()
        }
    }

    private fun insertFormattingPrefix(prefix: String) {
        editHistoryManager.commitImmediateSnapshot()
        val start = binding.etNoteContent.selectionStart
        val editable = binding.etNoteContent.text ?: return
        editable.insert(start, prefix)
        binding.etNoteContent.setSelection(start + prefix.length)
    }

    private fun insertTextAtCursor(text: String) {
        editHistoryManager.commitImmediateSnapshot()
        val start = binding.etNoteContent.selectionStart
        val editable = binding.etNoteContent.text ?: return
        editable.insert(start, text)
        binding.etNoteContent.setSelection(start + text.length)
    }

    private fun wrapSelection(prefix: String, suffix: String) {
        editHistoryManager.commitImmediateSnapshot()
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val editable = binding.etNoteContent.text ?: return

        if (start < end) {
            val selectedText = editable.subSequence(start, end).toString()
            val replacement = prefix + selectedText + suffix
            editable.replace(start, end, replacement)
            binding.etNoteContent.setSelection(start + prefix.length, end + prefix.length)
        } else {
            val placeholder = "text"
            val textToInsert = prefix + placeholder + suffix
            editable.insert(start, textToInsert)
            binding.etNoteContent.setSelection(start + prefix.length, start + prefix.length + placeholder.length)
        }
    }

    private fun updateWordCount() {
        val content = binding.etNoteContent.text.toString()
        val words = if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size
        val timestamp = currentNote?.updatedAt ?: System.currentTimeMillis()
        val dateStr = dateFormat.format(Date(timestamp))

        lifecycleScope.launch {
            val fId = folderId
            val folderName = if (fId != null && fId > 0) {
                database.folderDao().getFolderById(fId)?.name ?: "Personal"
            } else {
                "Uncategorized"
            }
            binding.tvEditorDate.text = "$dateStr | $words words | $folderName"
        }
    }

    private fun showNoteDetailsDialog() {
        val content = binding.etNoteContent.text.toString()
        val words = if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size
        val chars = content.length
        val created = dateFormat.format(Date(currentNote?.createdAt ?: System.currentTimeMillis()))
        val updated = dateFormat.format(Date(currentNote?.updatedAt ?: System.currentTimeMillis()))

        val message = "Words: $words\nCharacters: $chars\nLocked: $isLocked\nFavorite: $isFavorite\nCreated: $created\nLast Modified: $updated"

        AlertDialog.Builder(this)
            .setTitle(binding.etNoteTitle.text.toString().ifBlank { "Untitled Note" })
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveNoteAndFinish() {
        val rawTitle = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()

        if (rawTitle.isEmpty() && content.isEmpty()) {
            if (currentNote != null && currentNote!!.id > 0) {
                lifecycleScope.launch {
                    database.noteDao().delete(currentNote!!)
                    finish()
                }
            } else {
                finish()
            }
            return
        }

        val title = if (rawTitle.isNotEmpty()) {
            rawTitle
        } else {
            val firstLine = content.lines().firstOrNull { it.isNotBlank() } ?: "Untitled Note"
            firstLine.removePrefix("#").removePrefix("##").trim().take(40)
        }

        val preview = content.take(150).replace("\n", " ").trim()
        val now = System.currentTimeMillis()

        // Extract #tags and [[wikilinks]]
        val foundTags = Regex("#([A-Za-z0-9_-]+)").findAll(content).map { it.groupValues[1] }.distinct().toList()
        val tagsCsv = foundTags.joinToString(",")
        val foundLinks = Regex("\\[\\[(.*?)\\]\\]").findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.distinct().toList()

        lifecycleScope.launch {
            val savedId: Long
            if (currentNote != null) {
                val updated = currentNote!!.copy(
                    title = title,
                    content = content,
                    previewText = preview,
                    isPinned = isPinned,
                    isFavorite = isFavorite,
                    isLocked = isLocked,
                    isArchived = isArchived,
                    tagsCsv = tagsCsv,
                    folderId = folderId,
                    updatedAt = now
                )
                database.noteDao().update(updated)
                savedId = updated.id
            } else {
                val newNote = NoteEntity(
                    title = title,
                    content = content,
                    previewText = preview,
                    isPinned = isPinned,
                    isFavorite = isFavorite,
                    isLocked = isLocked,
                    isArchived = isArchived,
                    tagsCsv = tagsCsv,
                    folderId = folderId,
                    createdAt = now,
                    updatedAt = now
                )
                savedId = database.noteDao().insert(newNote)
            }

            // Sync tags table
            database.noteDao().deleteTagsForNote(savedId)
            if (foundTags.isNotEmpty()) {
                database.noteDao().insertTags(foundTags.map { NoteTagEntity(noteId = savedId, tag = it) })
            }

            // Sync links table
            database.noteDao().deleteLinksForNote(savedId)
            if (foundLinks.isNotEmpty()) {
                val linkEntities = foundLinks.map { targetTitle ->
                    val target = database.noteDao().findNoteByTitle(targetTitle)
                    NoteLinkEntity(sourceNoteId = savedId, targetTitle = targetTitle, targetNoteId = target?.id)
                }
                database.noteDao().insertLinks(linkEntities)
            }

            // Conflict-safe live folder mirror if enabled
            try {
                val autoUri = prefsRepo.autoBackupUriFlow.first()
                if (autoUri.isNotBlank()) {
                    val noteForMirror = currentNote ?: database.noteDao().getNoteById(savedId)
                    if (noteForMirror != null) {
                        MarkdownMirrorService.mirrorNoteToDirectory(this@NoteEditorActivity, noteForMirror, Uri.parse(autoUri))
                    }
                }
            } catch (_: Exception) {}

            finish()
        }
    }

    private fun confirmDeleteNote() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_note_confirm)
            .setMessage("Do you want to move this note to the trash?")
            .setPositiveButton("Move to Trash") { _, _ ->
                if (currentNote != null) {
                    lifecycleScope.launch {
                        database.noteDao().update(currentNote!!.copy(isDeleted = true))
                        finish()
                    }
                } else {
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveNoteAndFinish()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecorder.cleanup()
    }
}
