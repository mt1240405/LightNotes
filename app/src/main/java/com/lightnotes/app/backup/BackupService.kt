package com.lightnotes.app.backup

import com.lightnotes.app.core.model.Folder
import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.model.SyncState
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupService {

    private const val ENOTES_MAGIC = "ENOTES1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_LEN = 256
    private const val ITERATIONS = 10000

    fun exportToJson(notes: List<Note>, folders: List<Folder>): String {
        val root = JSONObject()
        root.put("format", "lightnotes-backup")
        root.put("version", 1)

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        root.put("exportedAt", isoFormat.format(Date()))

        // Map folderGlobalId to folder name
        val folderMap = folders.associateBy({ it.globalId }, { it.name })

        val notesArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("id", note.globalId)
            obj.put("title", note.title)
            obj.put("content", note.content)
            obj.put("createdAt", note.createdAtUtc)
            obj.put("updatedAt", note.updatedAtUtc)
            val folderName = if (note.folderGlobalId != null) folderMap[note.folderGlobalId] ?: "" else ""
            obj.put("folder", folderName)
            obj.put("pinned", note.isPinned)
            obj.put("favorite", note.isFavorite)
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        // Also preserve folders metadata
        val foldersArray = JSONArray()
        for (folder in folders) {
            val obj = JSONObject()
            obj.put("id", folder.globalId)
            obj.put("name", folder.name)
            obj.put("color", folder.color)
            obj.put("createdAt", folder.createdAtUtc)
            foldersArray.put(obj)
        }
        root.put("folders", foldersArray)

        return root.toString(2)
    }

    fun parseFromJson(jsonString: String): Pair<List<Note>, List<Folder>> {
        val root = JSONObject(jsonString)

        val notes = mutableListOf<Note>()
        val folders = mutableListOf<Folder>()
        val folderNameToGlobalId = mutableMapOf<String, String>()

        val foldersArray = root.optJSONArray("folders")
        if (foldersArray != null) {
            for (i in 0 until foldersArray.length()) {
                val obj = foldersArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.getString("name")
                val color = obj.optString("color", "#FFB200")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val folder = Folder(
                    globalId = id,
                    name = name,
                    color = color,
                    createdAtUtc = createdAt,
                    updatedAtUtc = createdAt,
                    revision = 1L,
                    syncState = SyncState.LOCAL_ONLY
                )
                folders.add(folder)
                folderNameToGlobalId[name] = id
            }
        }

        val notesArray = root.optJSONArray("notes")
        if (notesArray != null) {
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                val globalId = obj.optString("id", obj.optString("uuid", UUID.randomUUID().toString()))
                val title = obj.optString("title", "")
                val content = obj.optString("content", "")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                val folderName = obj.optString("folder", "")
                val folderGlobalId = if (folderName.isNotBlank()) {
                    folderNameToGlobalId.getOrPut(folderName) {
                        val newFolderId = UUID.randomUUID().toString()
                        folders.add(
                            Folder(
                                globalId = newFolderId,
                                name = folderName,
                                createdAtUtc = createdAt,
                                updatedAtUtc = createdAt
                            )
                        )
                        newFolderId
                    }
                } else null

                val isPinned = obj.optBoolean("pinned", obj.optBoolean("isPinned", false))
                val isFavorite = obj.optBoolean("favorite", obj.optBoolean("isFavorite", false))
                val isArchived = obj.optBoolean("archived", obj.optBoolean("isArchived", false))
                val isDeleted = obj.optBoolean("deleted", obj.optBoolean("isDeleted", false))
                val deletedAtUtc = if (isDeleted) updatedAt else null

                notes.add(
                    Note(
                        globalId = globalId,
                        title = title,
                        content = content,
                        folderGlobalId = folderGlobalId,
                        createdAtUtc = createdAt,
                        updatedAtUtc = updatedAt,
                        isPinned = isPinned,
                        isFavorite = isFavorite,
                        isArchived = isArchived,
                        deletedAtUtc = deletedAtUtc,
                        schemaVersion = 1,
                        revision = 1L,
                        syncState = SyncState.LOCAL_ONLY
                    )
                )
            }
        }

        return Pair(notes, folders)
    }

    fun exportEncrypted(
        notes: List<Note>,
        folders: List<Folder>,
        passphrase: String,
        outputStream: OutputStream
    ) {
        val plainText = exportToJson(notes, folders)
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)

        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN)
        random.nextBytes(salt)

        val iv = ByteArray(IV_LEN)
        random.nextBytes(iv)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val cipherText = cipher.doFinal(plainBytes)

        val magicBytes = ENOTES_MAGIC.toByteArray(StandardCharsets.US_ASCII)
        outputStream.write(magicBytes)
        outputStream.write(salt)
        outputStream.write(iv)
        outputStream.write(cipherText)
        outputStream.flush()
    }

    fun importEncrypted(
        inputStream: InputStream,
        passphrase: String
    ): Pair<List<Note>, List<Folder>> {
        val allBytes = inputStream.readBytes()
        val magicBytes = ENOTES_MAGIC.toByteArray(StandardCharsets.US_ASCII)
        val headerLen = magicBytes.size + SALT_LEN + IV_LEN

        if (allBytes.size < headerLen) {
            throw IllegalArgumentException("Invalid encrypted backup: file is too small")
        }

        val magicRead = allBytes.copyOfRange(0, magicBytes.size)
        if (!magicRead.contentEquals(magicBytes)) {
            throw IllegalArgumentException("Invalid file format: missing ENOTES magic")
        }

        var offset = magicBytes.size
        val salt = allBytes.copyOfRange(offset, offset + SALT_LEN)
        offset += SALT_LEN
        val iv = allBytes.copyOfRange(offset, offset + IV_LEN)
        offset += IV_LEN
        val cipherText = allBytes.copyOfRange(offset, allBytes.size)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val plainBytes = cipher.doFinal(cipherText)

        val jsonString = String(plainBytes, StandardCharsets.UTF_8)
        return parseFromJson(jsonString)
    }
}
