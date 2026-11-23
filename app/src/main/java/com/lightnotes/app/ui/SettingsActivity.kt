package com.lightnotes.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.lightnotes.app.ai.AiPreferences
import com.lightnotes.app.backup.BackupService
import com.lightnotes.app.backup.HeyTapImporter
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.AppPreferences
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.databinding.ActivitySettingsBinding
import com.lightnotes.app.security.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val database by lazy { AppDatabase.getDatabase(this) }

    private var pendingPassphrase: String = ""

    // Activity Result Launchers
    private val importHeyTapLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importHeyTapFromUri(uri)
    }

    private val exportJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) exportJsonToUri(uri)
    }

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importJsonFromUri(uri)
    }

    private val exportEncryptedLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) exportEncryptedToUri(uri, pendingPassphrase)
    }

    private val importEncryptedLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importEncryptedFromUri(uri, pendingPassphrase)
    }

    private val autoBackupTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            lifecycleScope.launch {
                PreferencesRepository.getInstance(this@SettingsActivity)
                    .setAutoBackupConfig(true, uri.toString(), "daily")
                com.lightnotes.app.backup.AutoBackupWorker.schedule(this@SettingsActivity)
                binding.tvAutoBackupSummary.text = "Enabled: Daily backup to ${uri.lastPathSegment}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupMenuComplexity()
        setupDynamicColorsSettings()
        setupMathEngineSettings()
        setupMermaidSettings()
        setupFontSizeSettings()
        setupBiometricSettings()
        setupAutoBackupSettings()
        setupAiSettings()
        setupBackupActions()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            binding.settingsToolbar.setPadding(0, systemBars.top, 0, 0)
            binding.settingsScrollView.setPadding(0, 0, 0, systemBars.bottom)

            insets
        }
    }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupMenuComplexity() {
        val prefs = PreferencesRepository.getInstance(this)
        lifecycleScope.launch {
            prefs.isAdvancedModeFlow.collect { isAdvanced ->
                binding.switchAdvancedMode.isChecked = isAdvanced
                AppPreferences.setAdvancedMode(this@SettingsActivity, isAdvanced)
                updateAdvancedModeSummary(isAdvanced)
            }
        }

        binding.btnToggleAdvancedMode.setOnClickListener {
            val newState = !binding.switchAdvancedMode.isChecked
            binding.switchAdvancedMode.isChecked = newState
            AppPreferences.setAdvancedMode(this, newState)
            lifecycleScope.launch {
                prefs.setAdvancedMode(newState)
            }
            updateAdvancedModeSummary(newState)
        }
    }

    private fun updateAdvancedModeSummary(isAdvanced: Boolean) {
        binding.tvAdvancedModeSummary.text = if (isAdvanced) {
            "Active: Full controls enabled (tables, LaTeX math, strikethrough, code blocks)"
        } else {
            "Active: Clean ColorOS layout (checklist, bold, italic, heading, AI assistant)"
        }
    }

    private fun setupDynamicColorsSettings() {
        val isDynamic = AppPreferences.isDynamicColors(this)
        binding.switchDynamicColors.isChecked = isDynamic
        updateDynamicColorsSummary(isDynamic)

        binding.btnToggleDynamicColors.setOnClickListener {
            val newState = !binding.switchDynamicColors.isChecked
            binding.switchDynamicColors.isChecked = newState
            AppPreferences.setDynamicColors(this, newState)
            if (newState) {
                com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(application)
            }
            updateDynamicColorsSummary(newState)
            Toast.makeText(this, "Dynamic colors setting saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDynamicColorsSummary(isDynamic: Boolean) {
        binding.tvDynamicColorsSummary.text = if (isDynamic) {
            "Active: System Material You Wallpaper Palette"
        } else {
            "Active: Off (Default Realme Amber #FFB200)"
        }
    }

    private fun setupMathEngineSettings() {
        val isKaTeX = AppPreferences.isKaTeXEngine(this)
        binding.switchMathEngine.isChecked = isKaTeX
        updateMathEngineSummary(isKaTeX)

        binding.btnToggleMathEngine.setOnClickListener {
            val newState = !binding.switchMathEngine.isChecked
            binding.switchMathEngine.isChecked = newState
            AppPreferences.setKaTeXEngine(this, newState)
            updateMathEngineSummary(newState)
        }
    }

    private fun updateMathEngineSummary(isKaTeX: Boolean) {
        binding.tvMathEngineSummary.text = if (isKaTeX) {
            "Active: Offline KaTeX Engine (Full LaTeX formula fidelity)"
        } else {
            "Active: Native JLatexMath (Fast, zero-latency rendering)"
        }
    }

    private fun setupMermaidSettings() {
        val prefs = PreferencesRepository.getInstance(this)
        lifecycleScope.launch {
            prefs.enableMermaidFlow.collect { enabled ->
                binding.switchMermaid.isChecked = enabled
            }
        }
        binding.btnToggleMermaid.setOnClickListener {
            val newState = !binding.switchMermaid.isChecked
            binding.switchMermaid.isChecked = newState
            lifecycleScope.launch { prefs.setEnableMermaid(newState) }
        }
    }

    private fun setupFontSizeSettings() {
        val prefs = PreferencesRepository.getInstance(this)
        lifecycleScope.launch {
            prefs.editorFontSizeFlow.collect { size ->
                binding.tvFontSizeSummary.text = "${size.toInt()} sp"
            }
        }
        binding.btnFontSize.setOnClickListener {
            val sizes = arrayOf("12 sp", "14 sp", "15 sp (Default)", "16 sp", "18 sp", "20 sp", "22 sp")
            val values = floatArrayOf(12f, 14f, 15f, 16f, 18f, 20f, 22f)
            AlertDialog.Builder(this)
                .setTitle("Select Editor Font Size")
                .setItems(sizes) { _, which ->
                    lifecycleScope.launch { prefs.setEditorFontSize(values[which]) }
                }
                .show()
        }
    }

    private fun setupBiometricSettings() {
        val prefs = PreferencesRepository.getInstance(this)
        lifecycleScope.launch {
            prefs.biometricLockEnabledFlow.collect { enabled ->
                binding.switchBiometric.isChecked = enabled
            }
        }

        val toggleBiometricAction = {
            if (!BiometricHelper.canAuthenticate(this)) {
                Toast.makeText(this, "Biometrics not setup on device", Toast.LENGTH_SHORT).show()
            } else {
                val isCurrentlyEnabled = binding.switchBiometric.isChecked
                if (isCurrentlyEnabled) {
                    // Revert visual state immediately to keep switch ON until authenticated
                    binding.switchBiometric.isChecked = true
                    BiometricHelper.authenticate(
                        this,
                        title = "Disable Biometric Lock",
                        subtitle = "Authenticate to turn off biometric protection",
                        onSuccess = {
                            binding.switchBiometric.isChecked = false
                            lifecycleScope.launch { prefs.setBiometricLockEnabled(false) }
                            Toast.makeText(this, "Biometric protection disabled", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            binding.switchBiometric.isChecked = true
                            Toast.makeText(this, "Authentication failed. Protection remains active.", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    BiometricHelper.authenticate(
                        this,
                        title = "Enable Biometric Lock",
                        subtitle = "Authenticate to enable biometric protection",
                        onSuccess = {
                            binding.switchBiometric.isChecked = true
                            lifecycleScope.launch { prefs.setBiometricLockEnabled(true) }
                            Toast.makeText(this, "Biometric protection enabled", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            binding.switchBiometric.isChecked = false
                            Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        binding.btnToggleBiometric.setOnClickListener { toggleBiometricAction() }
        binding.switchBiometric.setOnClickListener { toggleBiometricAction() }
    }

    private fun setupAutoBackupSettings() {
        val prefs = PreferencesRepository.getInstance(this)
        lifecycleScope.launch {
            prefs.autoBackupEnabledFlow.collect { enabled ->
                if (enabled) {
                    binding.tvAutoBackupSummary.text = "Enabled: Daily auto-backup active"
                } else {
                    binding.tvAutoBackupSummary.text = "Disabled (Tap to configure SAF backup folder)"
                }
            }
        }
        binding.btnAutoBackup.setOnClickListener {
            autoBackupTreeLauncher.launch(null)
        }
    }

    private fun setupAiSettings() {
        refreshAiSummaries()

        binding.btnAiEndpoint.setOnClickListener {
            val current = AiPreferences.getEndpoint(this)
            showInputDialog("AI API Endpoint", "Enter OpenAI-compatible completion URL:", current) { newEndpoint ->
                AiPreferences.setEndpoint(this, newEndpoint)
                refreshAiSummaries()
            }
        }

        binding.btnAiApiKey.setOnClickListener {
            val current = AiPreferences.getApiKey(this)
            showPasswordDialog("AI API Key", "Enter your secret API key (OpenAI / Groq / Gemini / OpenRouter):", current) { newKey ->
                AiPreferences.setApiKey(this, newKey)
                refreshAiSummaries()
            }
        }

        binding.btnAiModel.setOnClickListener {
            val current = AiPreferences.getModel(this)
            showInputDialog("AI Model Name", "Enter model identifier (e.g. gpt-4o-mini, gemini-1.5-flash):", current) { newModel ->
                AiPreferences.setModel(this, newModel)
                refreshAiSummaries()
            }
        }
    }

    private fun refreshAiSummaries() {
        binding.tvAiEndpointSummary.text = AiPreferences.getEndpoint(this)
        val key = AiPreferences.getApiKey(this)
        binding.tvAiApiKeySummary.text = if (key.isNotBlank()) "Configured (••••••••)" else "Not configured"
        binding.tvAiModelSummary.text = AiPreferences.getModel(this)
    }

    private fun setupBackupActions() {
        binding.btnImportHeyTap.setOnClickListener {
            importHeyTapLauncher.launch(arrayOf("*/*", "application/json"))
        }

        binding.btnExportJson.setOnClickListener {
            val defaultName = "lightnotes-backup-${System.currentTimeMillis()}.json"
            exportJsonLauncher.launch(defaultName)
        }

        binding.btnImportJson.setOnClickListener {
            importJsonLauncher.launch(arrayOf("*/*", "application/json"))
        }

        binding.btnExportEncrypted.setOnClickListener {
            showPasswordDialog("Export Encrypted Backup", "Enter encryption password:", "") { password ->
                pendingPassphrase = password
                val defaultName = "lightnotes-${System.currentTimeMillis()}.enotes"
                exportEncryptedLauncher.launch(defaultName)
            }
        }

        binding.btnImportEncrypted.setOnClickListener {
            showPasswordDialog("Import Encrypted Backup", "Enter decryption password:", "") { password ->
                pendingPassphrase = password
                importEncryptedLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    private fun importHeyTapFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val notes = HeyTapImporter.parseHeyTapBackup(jsonString)
                if (notes.isNotEmpty()) {
                    database.noteDao().insertAll(notes)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Successfully imported ${notes.size} notes from HeyTap!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "No valid notes found in file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Error importing HeyTap backup: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun exportJsonToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val notes = database.noteDao().getAllNotesSync()
                val folders = database.folderDao().getAllFoldersSync()
                val json = BackupService.exportToJson(notes, folders)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "JSON Export completed successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importJsonFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val (notes, folders) = BackupService.parseFromJson(json)
                if (folders.isNotEmpty()) {
                    database.folderDao().insertAll(folders)
                }
                if (notes.isNotEmpty()) {
                    database.noteDao().insertAll(notes)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Imported ${notes.size} notes and ${folders.size} folders", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportEncryptedToUri(uri: Uri, pass: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val notes = database.noteDao().getAllNotesSync()
                val folders = database.folderDao().getAllFoldersSync()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BackupService.exportEncrypted(notes, folders, pass, outputStream)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Encrypted .enotes export completed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Encryption failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importEncryptedFromUri(uri: Uri, pass: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pair = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BackupService.importEncrypted(inputStream, pass)
                }
                if (pair != null) {
                    val (notes, folders) = pair
                    if (folders.isNotEmpty()) {
                        database.folderDao().insertAll(folders)
                    }
                    if (notes.isNotEmpty()) {
                        database.noteDao().insertAll(notes)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Decrypted and restored ${notes.size} notes",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Decryption failed: Incorrect password or corrupt file",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showInputDialog(title: String, message: String, currentValue: String, onEntered: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(currentValue)
            setSelection(currentValue.length)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    onEntered(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPasswordDialog(title: String, message: String, currentValue: String, onPasswordEntered: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (currentValue.isNotEmpty()) {
                setText(currentValue)
                setSelection(currentValue.length)
            }
            hint = "Password"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val pass = input.text.toString().trim()
                if (pass.isNotEmpty()) {
                    onPasswordEntered(pass)
                } else {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
