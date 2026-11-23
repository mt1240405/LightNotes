package com.lightnotes.app.markdown

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.Coil
import com.lightnotes.app.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import ru.noties.jlatexmath.JLatexMathAndroid

class MarkdownEngine private constructor(private val context: Context) {

    var onWikilinkClicked: ((String) -> Unit)? = null

    private val prism4j = Prism4j(LightNotesGrammarLocator())

    private val markwon: Markwon by lazy {
        try {
            JLatexMathAndroid.init(context)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        val textSize = 15f * context.resources.displayMetrics.density
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(
                ContextCompat.getColor(context, R.color.brand_amber),
                ContextCompat.getColor(context, R.color.text_hint),
                ContextCompat.getColor(context, R.color.card_bg)
            ))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context, Coil.imageLoader(context)))
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.blocksEnabled(true)
                builder.errorHandler { _, _ -> null }
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        if (link.startsWith("wikilink://")) {
                            val target = link.removePrefix("wikilink://")
                            onWikilinkClicked?.invoke(target)
                        } else {
                            LinkResolverDef().resolve(view, link)
                        }
                    }
                }
            })
            .build()
    }

    fun preprocessMarkdown(input: String): String {
        // Preprocess [[WikiLinks]] -> [Title](wikilink://Title)
        return input.replace(Regex("\\[\\[(.*?)\\]\\]")) { matchResult ->
            val title = matchResult.groupValues[1]
            "[$title](wikilink://$title)"
        }
    }

    fun render(textView: TextView, markdown: String) {
        if (markdown.isBlank()) {
            textView.text = ""
            return
        }
        val processed = preprocessMarkdown(markdown)
        try {
            markwon.setMarkdown(textView, processed)
        } catch (t: Throwable) {
            t.printStackTrace()
            textView.text = processed
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
