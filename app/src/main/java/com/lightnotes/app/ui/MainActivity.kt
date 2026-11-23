package com.lightnotes.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lightnotes.app.R
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.FolderEntity
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.databinding.ActivityMainBinding
import com.lightnotes.app.search.NativeSearchOptimizer
import com.lightnotes.app.security.BiometricHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var folderTabAdapter: FolderTabAdapter

    private var allNotes = listOf<NoteEntity>()
    private var allFolders = listOf<FolderEntity>()
    private var currentFilterId: Long = -1 // -1 = All, -2 = Pinned, -3 = Trash, -4 = Favorites, -5 = Archived
    private var searchQuery: String = ""
    private var isGridView: Boolean = true
    private var isAuthenticating: Boolean = false

    private val colorOsInterpolator = PathInterpolator(0.3f, 0.0f, 0.1f, 1.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        HighRefreshRateHelper.applyHighRefreshRate(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRecyclerViews()
        setupListeners()
        observeData()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        HighRefreshRateHelper.applyHighRefreshRate(this)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                systemBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            binding.rvNotes.setPadding(
                binding.rvNotes.paddingLeft,
                binding.rvNotes.paddingTop,
                binding.rvNotes.paddingRight,
                systemBars.bottom + 90
            )

            val fabParams = binding.fabAddNote.layoutParams as ViewGroup.MarginLayoutParams
            fabParams.bottomMargin = systemBars.bottom + 24
            binding.fabAddNote.layoutParams = fabParams

            insets
        }
    }

    private fun setupRecyclerViews() {
        folderTabAdapter = FolderTabAdapter { selectedTab ->
            currentFilterId = selectedTab.id
            applyFilterAndSearch()
        }
        binding.rvFolderTabs.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFolderTabs.adapter = folderTabAdapter

        notesAdapter = NotesAdapter(
            onNoteClicked = { note, cardView ->
                if (isAuthenticating) return@NotesAdapter
                if (note.isLocked && BiometricHelper.canAuthenticate(this)) {
                    isAuthenticating = true
                    BiometricHelper.authenticate(
                        this,
                        title = "Protected Note",
                        subtitle = "Authenticate to access ${note.title}",
                        onSuccess = {
                            isAuthenticating = false
                            openNoteEditor(note, cardView, biometricVerified = true)
                        },
                        onError = {
                            isAuthenticating = false
                            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    openNoteEditor(note, cardView, biometricVerified = false)
                }
            },
            onNoteLongClicked = { note ->
                showNoteOptionsDialog(note)
                true
            }
        )

        updateLayoutManager()
        binding.rvNotes.adapter = notesAdapter
        HighRefreshRateHelper.optimizeScrollingContainer(binding.rvNotes)
    }

    private fun openNoteEditor(note: NoteEntity, cardView: View, biometricVerified: Boolean = false) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
            putExtra(NoteEditorActivity.EXTRA_BIOMETRIC_VERIFIED, biometricVerified)
        }
        val options = ActivityOptionsCompat.makeScaleUpAnimation(
            cardView, 0, 0, cardView.width, cardView.height
        )
        startActivity(intent, options.toBundle())
    }

    private fun updateLayoutManager() {
        if (isGridView) {
            binding.rvNotes.layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            binding.btnViewToggle.setImageResource(R.drawable.ic_list_view)
        } else {
            binding.rvNotes.layoutManager = LinearLayoutManager(this)
            binding.btnViewToggle.setImageResource(R.drawable.ic_grid_view)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.fabAddNote.setOnClickListener {
            val intent = Intent(this, NoteEditorActivity::class.java)
            if (currentFilterId >= 0) {
                intent.putExtra(NoteEditorActivity.EXTRA_FOLDER_ID, currentFilterId)
            }
            val options = ActivityOptionsCompat.makeScaleUpAnimation(
                binding.fabAddNote, 0, 0, binding.fabAddNote.width, binding.fabAddNote.height
            )
            startActivity(intent, options.toBundle())
        }

        setupPressMicroAnimation(binding.fabAddNote)
        setupPressMicroAnimation(binding.btnViewToggle)
        setupPressMicroAnimation(binding.btnSettings)

        binding.btnViewToggle.setOnClickListener {
            isGridView = !isGridView
            updateLayoutManager()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPressMicroAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(120).setInterpolator(colorOsInterpolator).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).setInterpolator(colorOsInterpolator).start()
                }
            }
            false
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            database.noteDao().getActiveNotes().collectLatest { notes ->
                allNotes = notes
                updateFolderTabs()
                applyFilterAndSearch()
            }
        }

        lifecycleScope.launch {
            database.folderDao().getAllFolders().collectLatest { folders ->
                allFolders = folders
                updateFolderTabs()
            }
        }
    }

    private fun updateFolderTabs() {
        val tabs = mutableListOf<FolderTabItem>()
        tabs.add(FolderTabItem(-1, getString(R.string.folder_all), allNotes.size))

        val pinnedCount = allNotes.count { it.isPinned }
        if (pinnedCount > 0) {
            tabs.add(FolderTabItem(-2, getString(R.string.folder_pinned), pinnedCount))
        }

        val favCount = allNotes.count { it.isFavorite }
        if (favCount > 0) {
            tabs.add(FolderTabItem(-4, "Favorites", favCount))
        }

        for (folder in allFolders) {
            val count = allNotes.count { it.folderId == folder.id }
            tabs.add(FolderTabItem(folder.id, folder.name, count))
        }

        val archCount = allNotes.count { it.isArchived }
        if (archCount > 0) {
            tabs.add(FolderTabItem(-5, "Archived", archCount))
        }

        tabs.add(FolderTabItem(-3, getString(R.string.folder_trash)))

        folderTabAdapter.submitTabs(tabs)
    }

    private fun applyFilterAndSearch() {
        var filtered = when (currentFilterId) {
            -1L -> allNotes
            -2L -> allNotes.filter { it.isPinned }
            -4L -> allNotes.filter { it.isFavorite }
            -5L -> allNotes.filter { it.isArchived }
            -3L -> emptyList()
            else -> allNotes.filter { it.folderId == currentFilterId }
        }

        if (searchQuery.isNotEmpty()) {
            filtered = NativeSearchOptimizer.rankNotes(searchQuery, filtered)
        }

        notesAdapter.submitList(filtered)
        binding.tvNoteCount.text = getString(R.string.notes_count, filtered.size)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNoteOptionsDialog(note: NoteEntity) {
        val pinAction = if (note.isPinned) "Unpin Note" else "Pin to Top"
        val favAction = if (note.isFavorite) "Remove from Favorites" else "Mark Favorite"
        val lockAction = if (note.isLocked) "Unlock Note (Remove Protection)" else "Lock Note (Biometric)"
        val deleteAction = if (note.isDeleted) "Restore Note" else "Move to Trash"
        val options = arrayOf(pinAction, favAction, lockAction, deleteAction)

        AlertDialog.Builder(this)
            .setTitle(note.title.ifBlank { "Note Options" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            database.noteDao().update(note.copy(isPinned = !note.isPinned))
                        }
                    }
                    1 -> {
                        lifecycleScope.launch {
                            database.noteDao().update(note.copy(isFavorite = !note.isFavorite))
                        }
                    }
                    2 -> {
                        if (BiometricHelper.canAuthenticate(this)) {
                            BiometricHelper.authenticate(
                                this,
                                title = if (note.isLocked) "Unlock Note" else "Lock Note",
                                onSuccess = {
                                    lifecycleScope.launch {
                                        database.noteDao().update(note.copy(isLocked = !note.isLocked))
                                    }
                                }
                            )
                        } else {
                            Toast.makeText(this, "Biometrics not available on device", Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> {
                        lifecycleScope.launch {
                            database.noteDao().update(note.copy(isDeleted = !note.isDeleted))
                        }
                    }
                }
            }
            .show()
    }
}
