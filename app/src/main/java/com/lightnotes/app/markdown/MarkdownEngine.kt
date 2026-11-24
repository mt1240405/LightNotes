package com.lightnotes.app.markdown

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.Spanned
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lightnotes.app.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.DefaultDownScalingMediaDecoder
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarkdownEngine private constructor(private val context: Context) {

    val markwon: Markwon by lazy {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val density = context.resources.displayMetrics.density

        Markwon.builder(context)
            .usePlugin(TablePlugin.create { builder ->
                builder
                    .tableCellPadding((12 * density).toInt())
                    .tableBorderWidth((1 * density).toInt())
                    .tableBorderColor(if (isNight) Color.parseColor("#2C2C2E") else Color.parseColor("#D1D1D6"))
                    .tableHeaderRowBackgroundColor(if (isNight) Color.parseColor("#1C1C1E") else Color.parseColor("#F2F2F7"))
                    .tableOddRowBackgroundColor(if (isNight) Color.parseColor("#0D0D0F") else Color.parseColor("#FAFAFC"))
                    .tableEvenRowBackgroundColor(if (isNight) Color.parseColor("#000000") else Color.parseColor("#FFFFFF"))
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeBackgroundColor(if (isNight) Color.parseColor("#1C1C1E") else Color.parseColor("#F0F1F5"))
                        .codeBlockBackgroundColor(if (isNight) Color.parseColor("#121214") else Color.parseColor("#F6F7F9"))
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(
                TaskListPlugin.create(
                    ContextCompat.getColor(context, R.color.brand_amber),
                    ContextCompat.getColor(context, R.color.text_hint),
                    ContextCompat.getColor(context, R.color.card_bg)
                )
            )
            .usePlugin(ImagesPlugin.create { plugin ->
                val screenWidth = context.resources.displayMetrics.widthPixels
                plugin
                    .addSchemeHandler(FileSchemeHandler.create())
                    .defaultMediaDecoder(DefaultDownScalingMediaDecoder.create(context.resources, screenWidth, screenWidth))
            })
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    val printMarkwon: Markwon by lazy {
        val density = context.resources.displayMetrics.density
        Markwon.builder(context)
            .usePlugin(TablePlugin.create { builder ->
                builder
                    .tableCellPadding((12 * density).toInt())
                    .tableBorderWidth((1 * density).toInt())
                    .tableBorderColor(Color.parseColor("#D1D1D6"))
                    .tableHeaderRowBackgroundColor(Color.parseColor("#F2F2F7"))
                    .tableOddRowBackgroundColor(Color.parseColor("#FAFAFC"))
                    .tableEvenRowBackgroundColor(Color.parseColor("#FFFFFF"))
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeBackgroundColor(Color.parseColor("#F0F1F5"))
                        .codeBlockBackgroundColor(Color.parseColor("#F6F7F9"))
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(
                TaskListPlugin.create(
                    ContextCompat.getColor(context, R.color.brand_amber),
                    Color.parseColor("#8E8E93"),
                    Color.parseColor("#FFFFFF")
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
