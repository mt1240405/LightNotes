package com.lightnotes.app.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lightnotes.app.data.AppDatabase
import com.lightnotes.app.data.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = PreferencesRepository.getInstance(context)

        val isEnabled = prefs.autoBackupEnabledFlow.first()
        val treeUriStr = prefs.autoBackupUriFlow.first()
        if (!isEnabled || treeUriStr.isBlank()) {
            return Result.success()
        }

        try {
            val db = AppDatabase.getDatabase(context)
            val notes = db.noteDao().getAllNotesSync()
            val manifest = BackupManifest(
                version = 2,
                notesCount = notes.size
            )
            val payload = NoteBackupPayload(
                manifest = manifest,
                notes = notes
            )
            val jsonContent = json.encodeToString(payload)

            val treeUri = Uri.parse(treeUriStr)
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            if (dir != null && dir.canWrite()) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val fileName = "LightNotes_Backup_${dateFormat.format(Date())}.json"
                val newFile = dir.createFile("application/json", fileName)
                if (newFile != null) {
                    context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                        os.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                }
            }

            // Cleanup temp files in cache
            cleanTempCache(context)

            return Result.success()
        } catch (t: Throwable) {
            t.printStackTrace()
            return Result.retry()
        }
    }

    private fun cleanTempCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "export")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 3600 * 1000) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val WORK_NAME = "LightNotes_AutoBackup"

        fun schedule(context: Context, isWeekly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val interval = if (isWeekly) 7L else 1L
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(interval, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
