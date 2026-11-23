package com.lightnotes.app.backup

import com.lightnotes.app.core.model.Note
import com.lightnotes.app.core.model.SyncState
import com.lightnotes.app.data.NoteEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

object HeyTapImporter {

    fun parseHeyTapBackup(jsonString: String): List<NoteEntity> {
        return parseHeyTapBackupDomain(jsonString).map { NoteEntity.fromDomain(it) }
    }

    fun parseHeyTapBackupDomain(jsonString: String): List<Note> {
        val notes = mutableListOf<Note>()
        val root = JSONObject(jsonString)
        val array = root.optJSONArray("notes") ?: return notes

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val listTitle = obj.optString("listTitle", "").trim()
            val listTime = obj.optString("listTime", "")
            val fullHeaderTime = obj.optString("fullHeaderTime", "")
            val isPinned = obj.optBoolean("isPinned", false)
            val rawText = obj.optString("rawText", "")
            val htmlContent = obj.optString("htmlContent", "")

            val noteContent = if (htmlContent.isNotBlank()) {
                htmlToMarkdown(htmlContent)
            } else {
                rawText
            }

            val title = if (listTitle.isNotBlank()) {
                listTitle
            } else {
                extractTitleFromContent(noteContent)
            }

            val timestamp = parseDateTime(fullHeaderTime.ifBlank { listTime })

            notes.add(
                Note(
                    globalId = UUID.randomUUID().toString(),
                    title = title,
                    content = noteContent,
                    isPinned = isPinned,
                    createdAtUtc = timestamp,
                    updatedAtUtc = timestamp,
                    syncState = SyncState.LOCAL_ONLY
                )
            )
        }
        return notes
    }

    private fun extractTitleFromContent(content: String): String {
        val firstLine = content.lines().firstOrNull { it.isNotBlank() } ?: "Untitled Note"
        return firstLine.removePrefix("#").removePrefix("##").trim().take(40)
    }

    fun parseDateTime(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()

        var cleaned = dateStr.replace(Regex("(\\d+)(st|nd|rd|th)"), "$1")
        cleaned = cleaned.replace("Sept", "Sep").trim()

        val formats = arrayOf(
            "MMM d,yyyy HH:mm",
            "MMM d, yyyy HH:mm",
            "MMM d,yyyy",
            "MMM d, yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = true
                val date = sdf.parse(cleaned)
                if (date != null) {
                    return date.time
                }
            } catch (_: Exception) {}
        }

        return System.currentTimeMillis()
    }

    fun htmlToMarkdown(html: String): String {
        var text = html
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")

        text = text.replace(Regex("(?i)<h1[^>]*>(.*?)</h1>"), "# $1\n\n")
        text = text.replace(Regex("(?i)<h2[^>]*>(.*?)</h2>"), "## $1\n\n")
        text = text.replace(Regex("(?i)<h3[^>]*>(.*?)</h3>"), "### $1\n\n")
        text = text.replace(Regex("(?i)<h[4-6][^>]*>(.*?)</h[4-6]>"), "#### $1\n\n")

        text = text.replace(Regex("(?i)<(b|strong)[^>]*>(.*?)</\\1>"), "**$2**")
        text = text.replace(Regex("(?i)<(i|em)[^>]*>(.*?)</\\1>"), "*$2*")
        text = text.replace(Regex("(?i)<(s|del|strike)[^>]*>(.*?)</\\1>"), "~~$2~~")
        text = text.replace(Regex("(?i)<code[^>]*>(.*?)</code>"), "`$1`")
        text = text.replace(Regex("(?i)<blockquote[^>]*>(.*?)</blockquote>"), "> $1\n\n")

        text = text.replace(Regex("(?i)<li[^>]*>(.*?)</li>"), "- $1\n")
        text = text.replace(Regex("(?i)<(ul|ol)[^>]*>"), "")
        text = text.replace(Regex("(?i)</(ul|ol)>"), "\n")

        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)<p[^>]*>(.*?)</p>"), "$1\n\n")
        text = text.replace(Regex("(?i)<div[^>]*>(.*?)</div>"), "$1\n")

        text = text.replace(Regex("<[^>]+>"), "")

        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        text = text.replace(Regex("\n{3,}"), "\n\n")

        return text.trim()
    }
}
