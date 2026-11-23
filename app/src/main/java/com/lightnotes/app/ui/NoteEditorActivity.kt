package com.lightnotes.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lightnotes.app.R
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.databinding.ActivityNoteEditorBinding
import com.lightnotes.app.editor.EditHistoryManager
import com.lightnotes.app.markdown.MarkdownEngine
import com.lightnotes.app.ui.dialogs.RealmeShareBottomSheet
import com.lightnotes.app.ui.viewmodel.EditorViewModel
import io.noties.markwon.ext.tasklist.TaskListSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_GLOBAL_ID = "extra_global_id"
        const val EXTRA_FOLDER_ID = "extra_folder_id"
    }

    private lateinit var binding: ActivityNoteEditorBinding
    private val viewModel: EditorViewModel by viewModels()

    private var isPreviewMode = false
    private var isPinned = false
    private var isFavorite = false
    private var isArchived = false
    private var selectedFolderGlobalId: String? = null

    private lateinit var editHistoryManager: EditHistoryManager
    private val markdownEngine by lazy { MarkdownEngine.getInstance(this) }
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault())

    private var renderedRevision = 0L
    private var isUserTyping = false

    // Search inside note
    private val searchMatches = mutableListOf<Int>()
    private var currentSearchIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        HighRefreshRateHelper.applyHighRefreshRate(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val localId = intent.getLongExtra(EXTRA_NOTE_ID, 0L).takeIf { it > 0 }
        val globalId = intent.getStringExtra(EXTRA_GLOBAL_ID)

        initViews()
        initKeyboardInsets()
        initEditHistory()

        viewModel.initNote(globalId, localId)
        observeViewModel()
    }

    private fun initViews() {
        // Back
        binding.btnBack.setOnClickListener {
            saveAndClose()
        }

        // Preview / Edit Toggle
        binding.btnToggleMode.setOnClickListener {
            togglePreviewMode()
        }

        // Share
        binding.btnShare.setOnClickListener {
            openShareSheet()
        }

        // Overflow menu
        binding.btnOverflowMenu.setOnClickListener {
            showOverflowMenu()
        }

        // Inline Pin
        binding.ivInlinePin.setOnClickListener {
            togglePinState()
        }

        // Formatting toolbar buttons
        binding.btnFormatBold.setOnClickListener { applyFormatting("**", "**") }
        binding.btnFormatItalic.setOnClickListener { applyFormatting("*", "*") }
        binding.btnFormatHeading.setOnClickListener { applyLinePrefix("# ") }
        binding.btnFormatBullet.setOnClickListener { applyLinePrefix("- ") }
        binding.btnFormatNumber.setOnClickListener { applyLinePrefix("1. ") }
        binding.btnFormatChecklist.setOnClickListener { applyLinePrefix("- [ ] ") }
        binding.btnFormatLink.setOnClickListener { applyFormatting("[", "](url)", "text") }
        binding.btnFormatCode.setOnClickListener { applyFormatting("```\n", "\n```") }
        binding.btnFormatQuote.setOnClickListener { applyLinePrefix("> ") }

        binding.btnUndo.setOnClickListener { editHistoryManager.undo() }
        binding.btnRedo.setOnClickListener { editHistoryManager.redo() }

        // Content & Title change watchers
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUserTyping) {
                    val title = binding.etNoteTitle.text.toString()
                    val content = binding.etNoteContent.text.toString()
                    updateMetadataBar(content)
                    viewModel.onContentChanged(title, content)
                }
            }
        }

        binding.etNoteTitle.addTextChangedListener(textWatcher)
        binding.etNoteContent.addTextChangedListener(textWatcher)

        // Find in note search
        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearchInNote(s?.toString().orEmpty())
            }
        })

        binding.btnSearchNext.setOnClickListener { navigateSearchMatch(1) }
        binding.btnSearchPrev.setOnClickListener { navigateSearchMatch(-1) }
        binding.btnCloseSearch.setOnClickListener {
            binding.layoutSearchBar.visibility = View.GONE
            binding.etSearchQuery.setText("")
        }

        // Interactive checkbox touch in preview
        setupPreviewCheckboxToggle()
    }

    private fun initEditHistory() {
        editHistoryManager = EditHistoryManager(binding.etNoteContent) { canUndo, canRedo ->
            binding.btnUndo.isEnabled = canUndo
            binding.btnUndo.alpha = if (canUndo) 1.0f else 0.4f
            binding.btnRedo.isEnabled = canRedo
            binding.btnRedo.alpha = if (canRedo) 1.0f else 0.4f
        }
    }

    private fun initKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.editorRoot) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sysBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            if (!isPreviewMode) {
                binding.bottomToolbar.visibility = View.VISIBLE
                val translation = if (imeVisible) -(imeHeight - sysBarBottom).coerceAtLeast(0).toFloat() else 0f
                binding.bottomToolbar.translationY = translation
            }
            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentNote.collect { note ->
                    if (note != null && !isUserTyping) {
                        populateNoteUI(note)
                    }
                }
            }
        }
    }

    private fun populateNoteUI(note: Note) {
        isUserTyping = false
        binding.etNoteTitle.setText(note.title)
        binding.etNoteContent.setText(note.content)
        editHistoryManager.initHistory(note.content)

        isPinned = note.isPinned
        isFavorite = note.isFavorite
        isArchived = note.isArchived
        selectedFolderGlobalId = note.folderGlobalId

        updatePinIcon()
        updateMetadataBar(note.content, note.updatedAtUtc)
        isUserTyping = true
    }

    private fun updateMetadataBar(content: String, timestamp: Long = System.currentTimeMillis()) {
        val dateStr = dateFormat.format(Date(timestamp))
        val words = if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size
        val folderName = getFolderName(selectedFolderGlobalId)
        val meta = if (folderName.isNotBlank()) {
            "$dateStr | $words words | $folderName"
        } else {
            "$dateStr | $words words"
        }
        binding.tvEditorDate.text = meta
    }

    private fun getFolderName(folderGlobalId: String?): String {
        if (folderGlobalId.isNullOrBlank()) return ""
        val folders = viewModel.folders.value
        return folders.firstOrNull { it.globalId == folderGlobalId }?.name ?: ""
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

    private fun togglePinState() {
        isPinned = !isPinned
        updatePinIcon()
        viewModel.togglePin(
            binding.etNoteTitle.text.toString(),
            binding.etNoteContent.text.toString()
        )
    }

    private fun togglePreviewMode() {
        isPreviewMode = !isPreviewMode
        if (isPreviewMode) {
            hideKeyboard()
            binding.etNoteContent.visibility = View.GONE
            binding.tvNoteRendered.visibility = View.VISIBLE
            binding.bottomToolbar.visibility = View.GONE

            binding.btnToggleMode.text = getString(R.string.edit_label)
            binding.btnToggleMode.setIconResource(R.drawable.ic_edit)

            renderMarkdownPreview()
        } else {
            binding.tvNoteRendered.visibility = View.GONE
            binding.etNoteContent.visibility = View.VISIBLE
            binding.bottomToolbar.visibility = View.VISIBLE

            binding.btnToggleMode.text = getString(R.string.preview_label)
            binding.btnToggleMode.setIconResource(R.drawable.ic_eye)

            binding.etNoteContent.requestFocus()
        }
    }

    private fun renderMarkdownPreview() {
        val content = binding.etNoteContent.text.toString()
        val currentRev = viewModel.documentRevision.value
        lifecycleScope.launch {
            val spanned = markdownEngine.parseMarkdownAsync(content)
            if (currentRev >= renderedRevision) {
                renderedRevision = currentRev
                binding.tvNoteRendered.text = spanned
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPreviewCheckboxToggle() {
        binding.tvNoteRendered.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val tv = binding.tvNoteRendered
                val x = event.x.toInt() - tv.totalPaddingLeft + tv.scrollX
                val y = event.y.toInt() - tv.totalPaddingTop + tv.scrollY
                val layout = tv.layout ?: return@setOnTouchListener false
                val line = layout.getLineForVertical(y)
                val lineStart = layout.getLineStart(line)
                val lineEnd = layout.getLineEnd(line)

                val spanned = tv.text as? Spanned
                val taskSpans = spanned?.getSpans(lineStart, lineEnd, TaskListSpan::class.java)
                if (!taskSpans.isNullOrEmpty()) {
                    // Find which task list item index was clicked
                    val allTaskSpans = spanned.getSpans(0, spanned.length, TaskListSpan::class.java)
                    val clickedSpan = taskSpans.first()
                    val targetIndex = allTaskSpans.indexOf(clickedSpan)
                    if (targetIndex >= 0) {
                        val currentText = binding.etNoteContent.text.toString()
                        val updated = MarkdownEngine.toggleTaskItem(currentText, targetIndex)
                        if (updated != currentText) {
                            binding.etNoteContent.setText(updated)
                            renderMarkdownPreview()
                            val title = binding.etNoteTitle.text.toString()
                            viewModel.onContentChanged(title, updated)
                            return@setOnTouchListener true
                        }
                    }
                }
            }
            false
        }
    }

    private fun applyFormatting(prefix: String, suffix: String, defaultPlaceholder: String = "") {
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val text = binding.etNoteContent.text ?: return

        if (start >= 0 && end >= 0 && start != end) {
            val min = Math.min(start, end)
            val max = Math.max(start, end)
            val selected = text.subSequence(min, max).toString()
            val replacement = "$prefix$selected$suffix"
            text.replace(min, max, replacement)
            binding.etNoteContent.setSelection(min + prefix.length, min + prefix.length + selected.length)
        } else {
            val pos = if (start >= 0) start else text.length
            val insertText = "$prefix$defaultPlaceholder$suffix"
            text.insert(pos, insertText)
            binding.etNoteContent.setSelection(pos + prefix.length)
        }
    }

    private fun applyLinePrefix(linePrefix: String) {
        val start = binding.etNoteContent.selectionStart
        val text = binding.etNoteContent.text ?: return
        val pos = if (start >= 0) start else 0
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        text.insert(lineStart, linePrefix)
        binding.etNoteContent.setSelection(pos + linePrefix.length)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_B -> {
                    applyFormatting("**", "**")
                    return true
                }
                KeyEvent.KEYCODE_I -> {
                    applyFormatting("*", "*")
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) {
                        editHistoryManager.redo()
                    } else {
                        editHistoryManager.undo()
                    }
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    editHistoryManager.redo()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openShareSheet() {
        val title = binding.etNoteTitle.text.toString()
        val content = binding.etNoteContent.text.toString()
        val sheet = RealmeShareBottomSheet.newInstance(title, content)
        sheet.show(supportFragmentManager, "RealmeShareBottomSheet")
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(this, binding.btnOverflowMenu)
        popup.menu.apply {
            add(0, 1, 0, if (isPinned) "Unpin Note" else "Pin Note")
            add(0, 2, 1, if (isFavorite) "Remove from Favorites" else "Add to Favorites")
            add(0, 3, 2, "Move to Folder...")
            add(0, 4, 3, "Find in Note")
            add(0, 5, 4, "Archive Note")
            add(0, 6, 5, "Delete Note")
        }

        popup.setOnMenuItemClickListener { item ->
            val title = binding.etNoteTitle.text.toString()
            val content = binding.etNoteContent.text.toString()
            when (item.itemId) {
                1 -> togglePinState()
                2 -> {
                    isFavorite = !isFavorite
                    viewModel.toggleFavorite(title, content)
                }
                3 -> showMoveToFolderDialog(title, content)
                4 -> {
                    binding.layoutSearchBar.visibility = View.VISIBLE
                    binding.etSearchQuery.requestFocus()
                }
                5 -> {
                    viewModel.archiveNote(title, content) {
                        Toast.makeText(this, "Note archived", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                6 -> {
                    viewModel.moveToTrash(title, content) {
                        Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun showMoveToFolderDialog(title: String, content: String) {
        val folders = viewModel.folders.value
        val folderNames = mutableListOf("No Folder (Root)")
        folderNames.addAll(folders.map { it.name })

        AlertDialog.Builder(this)
            .setTitle("Move to Folder")
            .setItems(folderNames.toTypedArray()) { _, which ->
                val selectedId = if (which == 0) null else folders[which - 1].globalId
                selectedFolderGlobalId = selectedId
                updateMetadataBar(content)
                viewModel.setFolder(selectedId, title, content)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSearchInNote(query: String) {
        searchMatches.clear()
        currentSearchIndex = -1
        if (query.isBlank()) {
            binding.tvSearchMatches.text = ""
            return
        }

        val text = binding.etNoteContent.text.toString()
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            searchMatches.add(index)
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }

        if (searchMatches.isNotEmpty()) {
            navigateSearchMatch(1)
        } else {
            binding.tvSearchMatches.text = "0/0"
        }
    }

    private fun navigateSearchMatch(direction: Int) {
        if (searchMatches.isEmpty()) return
        currentSearchIndex = (currentSearchIndex + direction).let {
            if (it < 0) searchMatches.size - 1
            else if (it >= searchMatches.size) 0
            else it
        }
        val pos = searchMatches[currentSearchIndex]
        binding.tvSearchMatches.text = "${currentSearchIndex + 1}/${searchMatches.size}"
        binding.etNoteContent.setSelection(pos, pos + binding.etSearchQuery.text.length)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun saveAndClose() {
        val title = binding.etNoteTitle.text.toString()
        val content = binding.etNoteContent.text.toString()
        lifecycleScope.launch {
            viewModel.flushSync(title, content)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        val title = binding.etNoteTitle.text.toString()
        val content = binding.etNoteContent.text.toString()
        lifecycleScope.launch {
            viewModel.flushSync(title, content)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideKeyboard()
    }
}
