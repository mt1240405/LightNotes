package com.lightnotes.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lightnotes.app.backup.BackupService
import com.lightnotes.app.data.FolderEntity
import com.lightnotes.app.data.NoteEntity
import com.lightnotes.app.data.PreferencesRepository
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
class SecurityAndBackupTest {

    @Test
    fun testBackupServiceJsonRoundTrip() {
        val notes = listOf(
            NoteEntity(
                id = 1,
                uuid = "test-uuid-1",
                title = "Meeting Architecture",
                content = "# Architecture\n- Item 1\n- Item 2\n$$ E = mc^2 $$",
                previewText = "Architecture preview",
                folderId = 10,
                isPinned = true,
                isArchived = false,
                isDeleted = false,
                tags = "arch,android"
            ),
            NoteEntity(
                id = 2,
                uuid = "test-uuid-2",
                title = "Personal Journal",
                content = "Daily reflections...",
                previewText = "Daily reflections...",
                folderId = null,
                isPinned = false,
                isArchived = false,
                isDeleted = false,
                tags = "journal"
            )
        )

        val folders = listOf(
            FolderEntity(id = 10, name = "Work", color = "#FFB200")
        )

        val exportedJson = BackupService.exportToJson(notes, folders)
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("Meeting Architecture"))
        assertTrue(exportedJson.contains("Personal Journal"))
        assertTrue(exportedJson.contains("Work"))

        val (importedNotes, importedFolders) = BackupService.parseFromJson(exportedJson)
        assertEquals(2, importedNotes.size)
        assertEquals(1, importedFolders.size)

        assertEquals("Meeting Architecture", importedNotes[0].title)
        assertEquals("# Architecture\n- Item 1\n- Item 2\n$$ E = mc^2 $$", importedNotes[0].content)
        assertEquals(true, importedNotes[0].isPinned)
        assertEquals("Work", importedFolders[0].name)
    }

    @Test
    fun testBackupServiceAesGcmEncryptedRoundTrip() {
        val notes = listOf(
            NoteEntity(
                id = 101,
                uuid = "secret-uuid-101",
                title = "Confidential Credentials",
                content = "Top secret credentials and keys: API_KEY=xyz123",
                previewText = "Top secret credentials...",
                folderId = null,
                isPinned = true,
                isLocked = true,
                tags = "security,keys"
            )
        )
        val folders = listOf(
            FolderEntity(id = 1, name = "Vault", color = "#FF453A")
        )

        val passphrase = "MasterPassphrase2026!"
        val outputStream = ByteArrayOutputStream()

        // Encrypt with AES-GCM
        BackupService.exportEncrypted(notes, folders, passphrase, outputStream)
        val encryptedBytes = outputStream.toByteArray()
        assertTrue(encryptedBytes.isNotEmpty())

        // Decrypt with correct passphrase
        val inputStream = ByteArrayInputStream(encryptedBytes)
        val (restoredNotes, restoredFolders) = BackupService.importEncrypted(inputStream, passphrase)

        assertEquals(1, restoredNotes.size)
        assertEquals("Confidential Credentials", restoredNotes[0].title)
        assertEquals("Top secret credentials and keys: API_KEY=xyz123", restoredNotes[0].content)
        assertEquals(1, restoredFolders.size)
        assertEquals("Vault", restoredFolders[0].name)

        // Attempt decrypt with incorrect passphrase should fail
        val badInputStream = ByteArrayInputStream(encryptedBytes)
        try {
            BackupService.importEncrypted(badInputStream, "IncorrectPassword!")
            fail("Expected exception when decrypting with incorrect password")
        } catch (_: Exception) {
            // Expected cryptographic authentication tag failure
        }
    }

    @Test
    fun testBiometricProtectionGateInSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesRepository.getInstance(context)

        // Set biometric lock enabled
        prefs.setBiometricLockEnabled(true)
        assertTrue(prefs.biometricLockEnabledFlow.first())

        // Launch SettingsActivity
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        scenario.onActivity { activity ->
            val switchBiometric = activity.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchBiometric)
            assertNotNull(switchBiometric)
            assertTrue(switchBiometric.isChecked)

            // When switch is toggled, it must not disable biometric protection without successful authentication
            // Since biometric hardware is not present/enrolled in unit test environment, the switch reverts to checked
            val btnToggle = activity.findViewById<android.view.View>(R.id.btnToggleBiometric)
            btnToggle.performClick()

            // State should remain active/protected
            assertTrue(switchBiometric.isChecked)
        }
        scenario.close()

        // Verify DataStore preference remains protected
        assertTrue(prefs.biometricLockEnabledFlow.first())
    }
}
