package com.lightnotes.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lightnotes.app.R
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.databinding.ActivityMainBinding
import com.lightnotes.app.security.BiometricHelper
import com.lightnotes.app.ui.viewmodel.NotesFilter
import com.lightnotes.app.ui.viewmodel.NotesFilterType
import com.lightnotes.app.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NotesViewModel by viewModels()
    private val prefsRepo by lazy { PreferencesRepository.getInstance(this) }

    private lateinit var notesAdapter: NotesAdapter
    private lateinit var folderTabAdapter: FolderTabAdapter

    private var isGridView: Boolean = false // Linear list is default per Lite spec
    private var isAppUnlocked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        HighRefreshRateHelper.applyHighRefreshRate(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRecyclerViews()
        setupListeners()
        checkAppLock()
        observeData()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        HighRefreshRateHelper.applyHighRefreshRate(this)
    }

    private fun checkAppLock() {
        lifecycleScope.launch {
            val isLockEnabled = prefsRepo.biometricLockEnabledFlow.first()
            if (isLockEnabled && !isAppUnlocked && BiometricHelper.canAuthenticate(this@MainActivity)) {
                BiometricHelper.authenticate(
                    activity = this@MainActivity,
                    title = "LightNotes Locked",
                    subtitle = "Verify identity to view notes",
                    onSuccess = {
                        isAppUnlocked = true
                    },
                    onError = {
                        finish()
                    }
                )
            } else {
                isAppUnlocked = true
            }
        }
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
            val filter = when (selectedTab.filterType) {
                NotesFilterType.FOLDER -> NotesFilter(
                    type = NotesFilterType.FOLDER,
                    folderGlobalId = selectedTab.folderGlobalId,
                    folderName = selectedTab.name
                )
                else -> NotesFilter(type = selectedTab.filterType)
            }
            viewModel.setFilter(filter)
        }
        binding.rvFolderTabs.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFolderTabs.adapter = folderTabAdapter

        notesAdapter = NotesAdapter(
            onNoteClicked = { note, _ ->
                openNoteEditor(note)
            },
            onNoteLongClicked = { note ->
                showNoteContextMenu(note)
                true
            }
        )

        // Default: Linear list per Lite specification
        updateLayoutManager()
        binding.rvNotes.adapter = notesAdapter
    }

    private fun updateLayoutManager() {
        if (isGridView) {
            binding.rvNotes.layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                    gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                }
            binding.btnViewToggle.setImageResource(R.drawable.ic_list_view)
        } else {
            binding.rvNotes.layoutManager = LinearLayoutManager(this)
            binding.btnViewToggle.setImageResource(R.drawable.ic_grid_view)
        }
    }

    private fun setupListeners() {
        binding.btnViewToggle.setOnClickListener {
            isGridView = !isGridView
            updateLayoutManager()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.fabAddNote.setOnClickListener {
            val intent = Intent(this, NoteEditorActivity::class.java)
            val currentFilter = viewModel.filter.value
            if (currentFilter.type == NotesFilterType.FOLDER && currentFilter.folderGlobalId != null) {
                intent.putExtra(NoteEditorActivity.EXTRA_FOLDER_ID, currentFilter.folderGlobalId)
            }
            startActivity(intent)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                viewModel.setSearchQuery(query)
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notes.collect { notes ->
                        notesAdapter.submitList(notes)
                        val count = notes.size
                        binding.tvNoteCount.text = "$count ${if (count == 1) "note" else "notes"}"
                        binding.layoutEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.folders.collect { folders ->
                        updateFolderTabs(folders)
                    }
                }
            }
        }
    }

    private fun updateFolderTabs(folders: List<Folder>) {
        val tabs = mutableListOf<FolderTabItem>()
        tabs.add(FolderTabItem(NotesFilterType.ALL, null, "All Notes"))
        tabs.add(FolderTabItem(NotesFilterType.FAVORITES, null, "Favorites"))

        for (folder in folders) {
            tabs.add(FolderTabItem(NotesFilterType.FOLDER, folder.globalId, folder.name))
        }

        tabs.add(FolderTabItem(NotesFilterType.ARCHIVE, null, "Archive"))
        tabs.add(FolderTabItem(NotesFilterType.TRASH, null, "Trash"))

        folderTabAdapter.submitTabs(tabs)
    }

    private fun openNoteEditor(note: Note) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            putExtra(NoteEditorActivity.EXTRA_GLOBAL_ID, note.globalId)
            putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.localId)
        }
        startActivity(intent)
    }

    private fun showNoteContextMenu(note: Note) {
        val currentFilter = viewModel.filter.value
        val isTrash = currentFilter.type == NotesFilterType.TRASH || note.isDeleted

        val popup = PopupMenu(this, binding.root)
        if (isTrash) {
            popup.menu.add(0, 1, 0, "Restore Note")
            popup.menu.add(0, 2, 1, "Delete Permanently")
        } else {
            popup.menu.add(0, 3, 0, if (note.isPinned) "Unpin Note" else "Pin Note")
            popup.menu.add(0, 4, 1, if (note.isFavorite) "Remove from Favorites" else "Add to Favorites")
            popup.menu.add(0, 5, 2, "Move to Trash")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    viewModel.restoreFromTrash(note.globalId)
                    Toast.makeText(this, "Note restored", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Permanently?")
                        .setMessage("This action cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.permanentlyDelete(note.globalId)
                            Toast.makeText(this, "Note permanently deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                3 -> viewModel.togglePin(note.globalId)
                4 -> viewModel.toggleFavorite(note.globalId)
                5 -> {
                    viewModel.moveToTrash(note.globalId)
                    Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }
}
