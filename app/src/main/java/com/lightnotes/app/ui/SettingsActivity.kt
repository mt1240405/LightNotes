package com.lightnotes.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lightnotes.app.data.PreferencesRepository
import com.lightnotes.app.databinding.ActivitySettingsBinding
import com.lightnotes.app.security.BiometricHelper
import com.lightnotes.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private val prefsRepo by lazy { PreferencesRepository.getInstance(this) }

    private var pendingPassphrase: String = ""

    // SAF Launchers
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

    private val importHeyTapLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importHeyTapFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        HighRefreshRateHelper.applyHighRefreshRate(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupThemeSettings()
        setupBiometricSettings()
        setupBackupActions()
        setupAiSettings()
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

    private fun setupThemeSettings() {
        lifecycleScope.launch {
            val mode = prefsRepo.themeModeFlow.first()
            binding.tvThemeSummary.text = when (mode) {
                1 -> "Light"
                2 -> "OLED Dark"
                else -> "System Default"
            }
        }

        binding.btnThemeMode.setOnClickListener {
            val options = arrayOf("System Default", "Light", "OLED Dark")
            lifecycleScope.launch {
                val currentMode = prefsRepo.themeModeFlow.first()
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Select Theme")
                    .setSingleChoiceItems(options, currentMode) { dialog, which ->
                        lifecycleScope.launch {
                            prefsRepo.setThemeMode(which)
                            binding.tvThemeSummary.text = options[which]
                            when (which) {
                                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun setupBiometricSettings() {
        val initialEnabled = kotlinx.coroutines.runBlocking { prefsRepo.biometricLockEnabledFlow.first() }
        binding.switchBiometricLock.isChecked = initialEnabled

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.biometricLockEnabled.collect { isEnabled ->
                    binding.switchBiometricLock.isChecked = isEnabled
                }
            }
        }

        binding.btnToggleBiometricLock.setOnClickListener {
            val willEnable = !binding.switchBiometricLock.isChecked
            if (willEnable) {
                if (BiometricHelper.canAuthenticate(this)) {
                    BiometricHelper.authenticate(
                        activity = this,
                        title = "Enable App Lock",
                        subtitle = "Authenticate to confirm biometric lock",
                        onSuccess = {
                            viewModel.setBiometricLockEnabled(true)
                            binding.switchBiometricLock.isChecked = true
                            Toast.makeText(this, "Biometric lock enabled", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Toast.makeText(this, "Biometric authentication not available on this device", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Critical security gate: authenticate BEFORE disabling!
                if (BiometricHelper.canAuthenticate(this)) {
                    BiometricHelper.authenticate(
                        activity = this,
                        title = "Disable App Lock",
                        subtitle = "Authenticate to disable biometric lock",
                        onSuccess = {
                            viewModel.setBiometricLockEnabled(false)
                            binding.switchBiometricLock.isChecked = false
                            Toast.makeText(this, "Biometric lock disabled", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            Toast.makeText(this, "Authentication required to disable lock", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    viewModel.setBiometricLockEnabled(false)
                    binding.switchBiometricLock.isChecked = false
                }
            }
        }
    }

    private fun setupBackupActions() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        // 1. Export JSON
        binding.btnExportJson.setOnClickListener {
            val fileName = "LightNotes_Backup_${dateFormat.format(Date())}.json"
            exportJsonLauncher.launch(fileName)
        }

        // 2. Restore JSON
        binding.btnImportJson.setOnClickListener {
            importJsonLauncher.launch(arrayOf("application/json", "text/plain"))
        }

        // 3. Export Encrypted
        binding.btnExportEncrypted.setOnClickListener {
            promptPassphrase(isExport = true) { passphrase ->
                pendingPassphrase = passphrase
                val fileName = "LightNotes_Backup_${dateFormat.format(Date())}.enotes"
                exportEncryptedLauncher.launch(fileName)
            }
        }

        // 4. Restore Encrypted
        binding.btnImportEncrypted.setOnClickListener {
            promptPassphrase(isExport = false) { passphrase ->
                pendingPassphrase = passphrase
                importEncryptedLauncher.launch(arrayOf("*/*"))
            }
        }

        // 5. Import HeyTap
        binding.btnImportHeyTap.setOnClickListener {
            importHeyTapLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }

    private fun exportJsonToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val jsonString = viewModel.exportBackupJson()
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this@SettingsActivity, "Backup exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importJsonFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("Could not read file")

                val count = viewModel.importBackupJson(jsonString)
                Toast.makeText(this@SettingsActivity, "Imported/merged $count notes", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportEncryptedToUri(uri: Uri, passphrase: String) {
        lifecycleScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    viewModel.exportEncryptedBackup(passphrase, os)
                }
                Toast.makeText(this@SettingsActivity, "Encrypted backup exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Encrypted export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                pendingPassphrase = ""
            }
        }
    }

    private fun importEncryptedFromUri(uri: Uri, passphrase: String) {
        lifecycleScope.launch {
            try {
                val count = contentResolver.openInputStream(uri)?.use { inputStream ->
                    viewModel.importEncryptedBackup(passphrase, inputStream)
                } ?: 0
                Toast.makeText(this@SettingsActivity, "Decrypted and imported $count notes", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Decryption/import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                pendingPassphrase = ""
            }
        }
    }

    private fun importHeyTapFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("Could not read file")

                val count = viewModel.importHeyTapBackup(jsonString)
                Toast.makeText(this@SettingsActivity, "Imported $count Realme/HeyTap notes", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "HeyTap import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptPassphrase(isExport: Boolean, onConfirmed: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter passphrase"
        }

        AlertDialog.Builder(this)
            .setTitle(if (isExport) "Set Backup Passphrase" else "Enter Backup Passphrase")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val pass = input.text.toString().trim()
                if (pass.isNotBlank()) {
                    onConfirmed(pass)
                } else {
                    Toast.makeText(this, "Passphrase cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupAiSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    prefsRepo.aiApiKeyFlow.collect { key ->
                        binding.tvAiApiKeySummary.text = if (key.isBlank()) {
                            "Disabled (Not configured)"
                        } else {
                            "Configured (••••${key.takeLast(4)})"
                        }
                    }
                }
                launch {
                    prefsRepo.aiEndpointFlow.collect { endpoint ->
                        binding.tvAiEndpointSummary.text = endpoint
                    }
                }
                launch {
                    prefsRepo.aiModelFlow.collect { model ->
                        binding.tvAiModelSummary.text = model
                    }
                }
            }
        }

        binding.btnAiApiKey.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "Enter API key (e.g. sk-...)"
            }
            AlertDialog.Builder(this)
                .setTitle("AI API Key")
                .setMessage("Enter your API key. Leave blank and confirm to disable.")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val key = input.text.toString().trim()
                    lifecycleScope.launch {
                        prefsRepo.setAiApiKey(key)
                        Toast.makeText(this@SettingsActivity, if (key.isBlank()) "AI helper disabled" else "API key saved", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnAiEndpoint.setOnClickListener {
            lifecycleScope.launch {
                val currentEndpoint = prefsRepo.aiEndpointFlow.first()
                val input = EditText(this@SettingsActivity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setText(currentEndpoint)
                }
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("API Endpoint URL")
                    .setMessage("Standard OpenAI chat completions compatible endpoint.")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val url = input.text.toString().trim()
                        if (url.isNotBlank()) {
                            lifecycleScope.launch { prefsRepo.setAiEndpoint(url) }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        binding.btnAiModel.setOnClickListener {
            lifecycleScope.launch {
                val currentModel = prefsRepo.aiModelFlow.first()
                val input = EditText(this@SettingsActivity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT
                    setText(currentModel)
                }
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Model Name")
                    .setMessage("e.g. gpt-4o-mini, deepseek-chat, llama-3.1-8b")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val model = input.text.toString().trim()
                        if (model.isNotBlank()) {
                            lifecycleScope.launch { prefsRepo.setAiModel(model) }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
