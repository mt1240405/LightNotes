package com.lightnotes.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.backup.BackupService
import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.platform.DeviceIdentityProvider
import com.lightnotes.app.storage.repository.LocalNoteRepository
import com.lightnotes.app.ui.SettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAndSecurityTest {

    @Test
    fun testBackupServiceJsonRoundTrip() {
        val notes = listOf(
            Note(
                globalId = "test-uuid-1",
                title = "Meeting Architecture",
                content = "# Architecture\n- Item 1\n- Item 2",
                folderGlobalId = "folder-uuid-10",
                isPinned = true,
                isFavorite = false,
                isArchived = false
            ),
            Note(
                globalId = "test-uuid-2",
                title = "Personal Journal",
                content = "Daily reflections...",
                folderGlobalId = null,
                isPinned = false,
                isFavorite = true,
                isArchived = false
            )
        )

        val folders = listOf(
            Folder(globalId = "folder-uuid-10", name = "Work", color = "#FFB200")
        )

        val exportedJson = BackupService.exportToJson(notes, folders)
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("lightnotes-backup"))
        assertTrue(exportedJson.contains("Meeting Architecture"))
        assertTrue(exportedJson.contains("Personal Journal"))
        assertTrue(exportedJson.contains("Work"))

        val (importedNotes, importedFolders) = BackupService.parseFromJson(exportedJson)
        assertEquals(2, importedNotes.size)
        assertEquals(1, importedFolders.size)

        assertEquals("Meeting Architecture", importedNotes[0].title)
        assertEquals("# Architecture\n- Item 1\n- Item 2", importedNotes[0].content)
        assertEquals(true, importedNotes[0].isPinned)
        assertEquals("Work", importedFolders[0].name)
        assertEquals("folder-uuid-10", importedNotes[0].folderGlobalId)
    }

    @Test
    fun testBackupServiceAesGcmEncryptedRoundTrip() {
        val notes = listOf(
            Note(
                globalId = "secret-uuid-101",
                title = "Confidential Credentials",
                content = "Top secret credentials and keys: API_KEY=xyz123",
                isPinned = true
            )
        )
        val folders = listOf(
            Folder(globalId = "vault-folder-1", name = "Vault", color = "#FF453A")
        )

        val passphrase = "MasterPassphrase2026!"
        val outputStream = ByteArrayOutputStream()

        BackupService.exportEncrypted(notes, folders, passphrase, outputStream)
        val encryptedBytes = outputStream.toByteArray()
        assertTrue(encryptedBytes.isNotEmpty())

        val inputStream = ByteArrayInputStream(encryptedBytes)
        val (restoredNotes, restoredFolders) = BackupService.importEncrypted(inputStream, passphrase)

        assertEquals(1, restoredNotes.size)
        assertEquals("Confidential Credentials", restoredNotes[0].title)
        assertEquals("Top secret credentials and keys: API_KEY=xyz123", restoredNotes[0].content)
        assertEquals(1, restoredFolders.size)
        assertEquals("Vault", restoredFolders[0].name)

        // Invalid passphrase must fail authentication tag check
        val badInputStream = ByteArrayInputStream(encryptedBytes)
        try {
            BackupService.importEncrypted(badInputStream, "IncorrectPassphrase!")
            fail("Expected cryptographic exception on bad passphrase")
        } catch (_: Exception) {}
    }

    @Test
    fun testNonOverwriteConflictSafetyOnImport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = AppDatabase.getDatabase(context)
        val deviceIdProvider = DeviceIdentityProvider.getInstance(context)
        val noteRepo = LocalNoteRepository(db, deviceIdProvider)

        val localNote = noteRepo.saveNote(
            Note(
                globalId = "conflict-note-uuid",
                title = "Local Newer Title",
                content = "Local edited content",
                updatedAtUtc = 5000L
            )
        )

        val incomingStaleNote = Note(
            globalId = "conflict-note-uuid",
            title = "Stale Backup Title",
            content = "Old content",
            updatedAtUtc = 2000L // Stale timestamp
        )

        // Importing stale note should not overwrite newer local note
        noteRepo.importNotes(listOf(incomingStaleNote), emptyList())

        val currentNote = noteRepo.getNoteByGlobalId("conflict-note-uuid")
        assertNotNull(currentNote)
        assertEquals("Local Newer Title", currentNote?.title)
        assertEquals("Local edited content", currentNote?.content)
    }

    @Test
    fun testBiometricProtectionGateInSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesRepository.getInstance(context)

        prefs.setBiometricLockEnabled(true)
        assertTrue(prefs.biometricLockEnabledFlow.first())

        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        scenario.onActivity { activity ->
            val switchBiometric = activity.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchBiometricLock)
            assertNotNull(switchBiometric)
            assertTrue(switchBiometric.isChecked)

            // When switch is clicked to disable, it must not disable without authentication
            val btnToggle = activity.findViewById<android.view.View>(R.id.btnToggleBiometricLock)
            btnToggle.performClick()

            // Since biometric hardware is not present/enrolled in JVM test environment, switch stays checked
            assertTrue(switchBiometric.isChecked)
        }
        scenario.close()

        assertTrue(prefs.biometricLockEnabledFlow.first())
    }
}
