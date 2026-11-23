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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotesFilterType {
    ALL,
    FAVORITES,
    ARCHIVE,
    TRASH,
    FOLDER
}

data class NotesFilter(
    val type: NotesFilterType = NotesFilterType.ALL,
    val folderGlobalId: String? = null,
    val folderName: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel @JvmOverloads constructor(
    application: Application,
    private val noteRepository: NoteRepository = LocalNoteRepository.getInstance(application),
    private val folderRepository: FolderRepository = LocalFolderRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _filter = MutableStateFlow(NotesFilter())
    val filter: StateFlow<NotesFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val folders: StateFlow<List<Folder>> = folderRepository.observeAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = combine(_filter, _searchQuery) { filter, query ->
        Pair(filter, query)
    }.flatMapLatest { (filter, query) ->
        if (query.isNotBlank()) {
            noteRepository.searchNotes(query)
        } else {
            when (filter.type) {
                NotesFilterType.ALL -> noteRepository.observeActiveNotes()
                NotesFilterType.FAVORITES -> noteRepository.observeFavoriteNotes()
                NotesFilterType.ARCHIVE -> noteRepository.observeArchivedNotes()
                NotesFilterType.TRASH -> noteRepository.observeDeletedNotes()
                NotesFilterType.FOLDER -> {
                    if (filter.folderGlobalId != null) {
                        noteRepository.observeNotesByFolder(filter.folderGlobalId)
                    } else {
                        noteRepository.observeActiveNotes()
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: NotesFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePin(globalId: String) {
        viewModelScope.launch {
            noteRepository.togglePin(globalId)
        }
    }

    fun toggleFavorite(globalId: String) {
        viewModelScope.launch {
            noteRepository.toggleFavorite(globalId)
        }
    }

    fun moveToTrash(globalId: String) {
        viewModelScope.launch {
            noteRepository.moveToTrash(globalId)
        }
    }

    fun restoreFromTrash(globalId: String) {
        viewModelScope.launch {
            noteRepository.restoreFromTrash(globalId)
        }
    }

    fun permanentlyDelete(globalId: String) {
        viewModelScope.launch {
            noteRepository.permanentlyDelete(globalId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            noteRepository.emptyTrash()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            folderRepository.createFolder(name)
        }
    }
}
