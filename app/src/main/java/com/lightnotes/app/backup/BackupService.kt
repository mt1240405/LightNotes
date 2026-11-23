package com.lightnotes.app.backup

import com.lightnotes.app.data.FolderEntity
import com.lightnotes.app.data.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
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

    fun exportToJson(notes: List<NoteEntity>, folders: List<FolderEntity>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val notesArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("uuid", note.uuid)
            obj.put("title", note.title)
            obj.put("content", note.content)
            obj.put("previewText", note.previewText)
            obj.put("folderId", note.folderId ?: JSONObject.NULL)
            obj.put("isPinned", note.isPinned)
            obj.put("isArchived", note.isArchived)
            obj.put("isDeleted", note.isDeleted)
            obj.put("createdAt", note.createdAt)
            obj.put("updatedAt", note.updatedAt)
            obj.put("tags", note.tags)
            notesArray.put(obj)
        }
        root.put("notes", notesArray)

        val foldersArray = JSONArray()
        for (folder in folders) {
            val obj = JSONObject()
            obj.put("id", folder.id)
            obj.put("name", folder.name)
            obj.put("color", folder.color)
            obj.put("createdAt", folder.createdAt)
            foldersArray.put(obj)
        }
        root.put("folders", foldersArray)

        return root.toString(2)
    }

    fun parseFromJson(jsonString: String): Pair<List<NoteEntity>, List<FolderEntity>> {
        val root = JSONObject(jsonString)
        val notes = mutableListOf<NoteEntity>()
        val folders = mutableListOf<FolderEntity>()

        val foldersArray = root.optJSONArray("folders")
        if (foldersArray != null) {
            for (i in 0 until foldersArray.length()) {
                val obj = foldersArray.getJSONObject(i)
                folders.add(
                    FolderEntity(
                        name = obj.getString("name"),
                        color = obj.optString("color", "#FFB200"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val notesArray = root.optJSONArray("notes")
        if (notesArray != null) {
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                val folderId = if (obj.isNull("folderId")) null else obj.getLong("folderId")
                notes.add(
                    NoteEntity(
                        uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        previewText = obj.optString("previewText", ""),
                        folderId = folderId,
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        tags = obj.optString("tags", "")
                    )
                )
            }
        }

        return Pair(notes, folders)
    }

    fun exportEncrypted(
        notes: List<NoteEntity>,
        folders: List<FolderEntity>,
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
    ): Pair<List<NoteEntity>, List<FolderEntity>> {
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
