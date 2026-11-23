package com.lightnotes.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.repository.FolderRepository
import com.lightnotes.app.core.repository.NoteRepository
import com.lightnotes.app.storage.repository.LocalFolderRepository
import com.lightnotes.app.storage.repository.LocalNoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class EditorViewModel @JvmOverloads constructor(
    application: Application,
    private val noteRepository: NoteRepository = LocalNoteRepository.getInstance(application),
    private val folderRepository: FolderRepository = LocalFolderRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _documentRevision = MutableStateFlow(1L)
    val documentRevision: StateFlow<Long> = _documentRevision.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepository.observeAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var autosaveJob: Job? = null

    fun initNote(globalId: String?, localId: Long?) {
        viewModelScope.launch {
            val existing = when {
                !globalId.isNullOrBlank() -> noteRepository.getNoteByGlobalId(globalId)
                localId != null && localId > 0 -> noteRepository.getNoteByLocalId(localId)
                else -> null
            }

            if (existing != null) {
                _currentNote.value = existing
                _documentRevision.value = existing.revision
            } else {
                val newNote = Note(
                    globalId = UUID.randomUUID().toString(),
                    title = "",
                    content = "",
                    createdAtUtc = System.currentTimeMillis(),
                    updatedAtUtc = System.currentTimeMillis()
                )
                _currentNote.value = newNote
                _documentRevision.value = 1L
            }
        }
    }

    fun onContentChanged(title: String, content: String) {
        _documentRevision.value += 1
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(400) // Debounce ~400ms
            persistNote(title, content)
        }
    }

    suspend fun flushSync(title: String, content: String): Note? {
        autosaveJob?.cancel()
        return persistNote(title, content)
    }

    private suspend fun persistNote(title: String, content: String): Note? {
        val current = _currentNote.value ?: return null
        if (title.isBlank() && content.isBlank() && current.localId == 0L) {
            return null // Do not persist empty initial notes
        }

        val updated = current.copy(
            title = title.trim(),
            content = content
        )

        val saved = noteRepository.saveNote(updated)
        _currentNote.value = saved
        return saved
    }

    fun togglePin(title: String, content: String) {
        viewModelScope.launch {
            val current = _currentNote.value ?: return@launch
            val noteToSave = current.copy(
                title = title.trim(),
                content = content,
                isPinned = !current.isPinned
            )
            val saved = noteRepository.saveNote(noteToSave)
            _currentNote.value = saved
        }
    }

    fun toggleFavorite(title: String, content: String) {
        viewModelScope.launch {
            val current = _currentNote.value ?: return@launch
            val noteToSave = current.copy(
                title = title.trim(),
                content = content,
                isFavorite = !current.isFavorite
            )
            val saved = noteRepository.saveNote(noteToSave)
            _currentNote.value = saved
        }
    }

    fun setFolder(folderGlobalId: String?, title: String, content: String) {
        viewModelScope.launch {
            val current = _currentNote.value ?: return@launch
            val noteToSave = current.copy(
                title = title.trim(),
                content = content,
                folderGlobalId = folderGlobalId
            )
            val saved = noteRepository.saveNote(noteToSave)
            _currentNote.value = saved
        }
    }

    fun archiveNote(title: String, content: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val current = _currentNote.value ?: return@launch
            val noteToSave = current.copy(
                title = title.trim(),
                content = content,
                isArchived = true
            )
            noteRepository.saveNote(noteToSave)
            onComplete()
        }
    }

    fun moveToTrash(title: String, content: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val current = _currentNote.value ?: return@launch
            if (current.localId > 0L) {
                noteRepository.moveToTrash(current.globalId)
            }
            onComplete()
        }
    }
}
