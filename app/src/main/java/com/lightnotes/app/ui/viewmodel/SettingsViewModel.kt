package com.lightnotes.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightnotes.app.backup.BackupService
import com.lightnotes.app.backup.HeyTapImporter
import com.lightnotes.app.core.repository.FolderRepository
import com.lightnotes.app.core.repository.NoteRepository
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.storage.repository.LocalFolderRepository
import com.lightnotes.app.storage.repository.LocalNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val prefsRepo: PreferencesRepository = PreferencesRepository.getInstance(application),
    private val noteRepository: NoteRepository = LocalNoteRepository.getInstance(application),
    private val folderRepository: FolderRepository = LocalFolderRepository.getInstance(application)
) : AndroidViewModel(application) {

    val biometricLockEnabled: StateFlow<Boolean> = prefsRepo.biometricLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setBiometricLockEnabled(enabled)
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val notes = noteRepository.getAllNotesSync()
        val folders = folderRepository.getAllFoldersSync()
        BackupService.exportToJson(notes, folders)
    }

    suspend fun importBackupJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        val (notes, folders) = BackupService.parseFromJson(jsonString)
        noteRepository.importNotes(notes, folders)
    }

    suspend fun exportEncryptedBackup(passphrase: String, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val notes = noteRepository.getAllNotesSync()
        val folders = folderRepository.getAllFoldersSync()
        BackupService.exportEncrypted(notes, folders, passphrase, outputStream)
    }

    suspend fun importEncryptedBackup(passphrase: String, inputStream: InputStream): Int = withContext(Dispatchers.IO) {
        val (notes, folders) = BackupService.importEncrypted(inputStream, passphrase)
        noteRepository.importNotes(notes, folders)
    }

    suspend fun importHeyTapBackup(jsonString: String): Int = withContext(Dispatchers.IO) {
        val notes = HeyTapImporter.parseHeyTapBackupDomain(jsonString)
        noteRepository.importNotes(notes, emptyList())
    }
}
