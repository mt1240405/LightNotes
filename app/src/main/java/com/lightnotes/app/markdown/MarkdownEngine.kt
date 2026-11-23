package com.lightnotes.app.markdown

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lightnotes.app.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarkdownEngine private constructor(private val context: Context) {

    val markwon: Markwon by lazy {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(
                TaskListPlugin.create(
                    ContextCompat.getColor(context, R.color.brand_amber),
                    ContextCompat.getColor(context, R.color.text_hint),
                    ContextCompat.getColor(context, R.color.card_bg)
                )
            )
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    fun render(textView: TextView, markdown: String) {
        if (markdown.isBlank()) {
            textView.text = ""
            return
        }
        try {
            markwon.setMarkdown(textView, markdown)
        } catch (_: Throwable) {
            textView.text = markdown
        }
    }

    suspend fun parseMarkdownAsync(markdown: String): Spanned = withContext(Dispatchers.Default) {
        if (markdown.isBlank()) {
            return@withContext android.text.SpannableString("")
        }
        try {
            markwon.toMarkdown(markdown)
        } catch (_: Throwable) {
            android.text.SpannableString(markdown)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MarkdownEngine? = null

        fun getInstance(context: Context): MarkdownEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = MarkdownEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun toggleTaskItem(markdown: String, targetIndex: Int): String {
            val regex = Regex("(?m)^(\\s*[-*+]\\s+\\[)([ xX])(\\])")
            var currentIndex = 0
            return regex.replace(markdown) { matchResult ->
                if (currentIndex == targetIndex) {
                    currentIndex++
                    val prefix = matchResult.groupValues[1]
                    val currentState = matchResult.groupValues[2]
                    val suffix = matchResult.groupValues[3]
                    val newState = if (currentState == " ") "x" else " "
                    "$prefix$newState$suffix"
                } else {
                    currentIndex++
                    matchResult.value
                }
            }
        }
    }
}
