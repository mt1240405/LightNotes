package com.lightnotes.app.export

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.lightnotes.app.data.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WebViewPdfConfig(
    val preset: String = "LaTeX Academic", // "LaTeX Academic", "Clean Modern", "Technical Spec"
    val fontFamily: String = "LaTeX Serif", // "LaTeX Serif", "Modern Sans", "Technical Mono"
    val fontSizePt: Int = 11,
    val lineHeight: Float = 1.6f,
    val paragraphSpacingPt: Float = 8f,
    val textAlignment: String = "Justified", // "Left", "Justified"
    val pageSize: String = "A4", // "A4", "Letter", "Legal", "A5", "Custom"
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val marginTopMm: Float = 22f,
    val marginRightMm: Float = 22f,
    val marginBottomMm: Float = 22f,
    val marginLeftMm: Float = 22f,
    val paperTheme: String = "Light", // "Light", "Warm Sepia", "Dark OLED"
    val showHeader: Boolean = true,
    val showPageNumbers: Boolean = true,
    val customCss: String = ""
) {
    // Secondary constructor for backwards compatibility with earlier 8-9 parameter configurations
    constructor(
        fontFamily: String = "Modern Sans",
        fontSizePt: Int = 12,
        lineHeight: Float = 1.55f,
        pageSize: String = "A4",
        customWidthMm: Float = 210f,
        customHeightMm: Float = 297f,
        marginMm: Float = 16f,
        paperTheme: String = "Light",
        customCss: String = ""
    ) : this(
        preset = if (fontFamily.contains("Serif", ignoreCase = true)) "LaTeX Academic" else "Clean Modern",
        fontFamily = fontFamily,
        fontSizePt = fontSizePt,
        lineHeight = lineHeight,
        paragraphSpacingPt = 8f,
        textAlignment = if (fontFamily.contains("Serif", ignoreCase = true)) "Justified" else "Left",
        pageSize = pageSize,
        customWidthMm = customWidthMm,
        customHeightMm = customHeightMm,
        marginTopMm = marginMm,
        marginRightMm = marginMm,
        marginBottomMm = marginMm,
        marginLeftMm = marginMm,
        paperTheme = paperTheme,
        showHeader = true,
        showPageNumbers = true,
        customCss = customCss
    )
}

class WebViewPdfExporter(private val context: Context? = null) {

    companion object {
        // Strong reference to prevent premature GC before printing completes
        var activePrintWebView: WebView? = null

        fun buildHtmlDocument(note: NoteEntity, config: WebViewPdfConfig = WebViewPdfConfig()): String {
            return WebViewPdfExporter(null).generateHtml(note, config)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun buildHtmlDocument(note: NoteEntity, config: WebViewPdfConfig = WebViewPdfConfig()): String {
        return generateHtml(note, config)
    }

    fun exportToPdf(
        note: NoteEntity,
        config: WebViewPdfConfig = WebViewPdfConfig(),
        onWebViewCreated: ((WebView) -> Unit)? = null
    ) {
        val ctx = context ?: return
        try {
            val html = generateHtml(note, config)

            val webView = WebView(ctx)
            activePrintWebView = webView
            onWebViewCreated?.invoke(webView)

            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.domStorageEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    mainHandler.postDelayed({
                        try {
                            val printManager = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            if (printManager != null && view != null) {
                                val cleanTitle = note.title.ifBlank { "Untitled" }
                                    .replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                                    .take(50)
                                val jobName = "LightNotes_$cleanTitle"
                                val printAdapter = view.createPrintDocumentAdapter(jobName)

                                val mediaSize = when (config.pageSize.uppercase()) {
                                    "LETTER" -> PrintAttributes.MediaSize.NA_LETTER
                                    "LEGAL" -> PrintAttributes.MediaSize.NA_LEGAL
                                    "A5" -> PrintAttributes.MediaSize.ISO_A5
                                    "CUSTOM" -> {
                                        val widthMils = (config.customWidthMm * 39.3701).toInt().coerceAtLeast(1000)
                                        val heightMils = (config.customHeightMm * 39.3701).toInt().coerceAtLeast(1000)
                                        PrintAttributes.MediaSize("CUSTOM", "Custom Size", widthMils, heightMils)
                                    }
                                    else -> PrintAttributes.MediaSize.ISO_A4
                                }

                                // 0,0,0,0 print spooler margins allow full-bleed background fills to paper edge;
                                // internal margins are handled by CSS @page
                                val margins = PrintAttributes.Margins(0, 0, 0, 0)

                                val attributes = PrintAttributes.Builder()
                                    .setMediaSize(mediaSize)
                                    .setMinMargins(margins)
                                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                                    .build()

                                printManager.print(jobName, printAdapter, attributes)
                            } else {
                                Toast.makeText(ctx, "Print service is unavailable", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Print preparation error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, 300)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(ctx, "Failed to render PDF page", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Failed to initialize PDF export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateHtml(note: NoteEntity, config: WebViewPdfConfig): String {
        val isLatexPreset = config.preset.contains("LaTeX", ignoreCase = true)
        val isJustified = config.textAlignment.equals("Justified", ignoreCase = true) || isLatexPreset

        val fontCss = when {
            config.fontFamily.contains("Serif", ignoreCase = true) || isLatexPreset ->
                "\"Latin Modern Roman\", \"Computer Modern\", Georgia, \"Noto Serif\", serif"
            config.fontFamily.contains("Mono", ignoreCase = true) ->
                "'JetBrains Mono', 'Fira Code', 'Courier New', monospace"
            else ->
                "Inter, Roboto, -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif"
        }

        val themeColors = when (config.paperTheme.lowercase()) {
            "warm sepia", "sepia" -> ThemeColors(
                bg = "#FAF6EE",
                text = "#2C2824",
                heading = "#1E1A17",
                border = "#E4DCD0",
                codeBg = "#F0EAE0",
                meta = "#7C746B",
                accent = "#C47D00"
            )
            "dark oled", "dark", "oled black", "oled" -> ThemeColors(
                bg = "#000000",
                text = "#F2F2F5",
                heading = "#FFFFFF",
                border = "#2C2C2E",
                codeBg = "#141416",
                meta = "#8E929E",
                accent = "#FFB200"
            )
            else -> ThemeColors(
                bg = "#FFFFFF",
                text = "#1A1B1E",
                heading = "#111214",
                border = "#E5E7EB",
                codeBg = "#F3F4F6",
                meta = "#6B7280",
                accent = "#FFB200"
            )
        }

        val sizeRule = when (config.pageSize.uppercase()) {
            "LETTER" -> "letter portrait"
            "LEGAL" -> "legal portrait"
            "A5" -> "A5 portrait"
            "CUSTOM" -> "${config.customWidthMm}mm ${config.customHeightMm}mm"
            else -> "A4 portrait"
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy, h:mm a", Locale.getDefault())
        val dateStr = dateFormat.format(Date(note.updatedAt))
        val bodyHtml = convertMarkdownToHtml(note.content, themeColors)

        val headerHtml = if (config.showHeader) {
            """
            <div class="note-header">
                <h1 class="note-title">${escapeHtml(note.title.ifBlank { "Untitled Note" })}</h1>
                <div class="metadata-bar">
                    $dateStr | ${countWords(note.content)} words${if (note.isPinned) " <span class=\"pin-badge\">★ Pinned</span>" else ""}
                </div>
            </div>
            """.trimIndent()
        } else ""

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="file:///android_asset/katex/katex.min.css">
<script src="file:///android_asset/katex/katex.min.js"></script>
<style>
* {
    box-sizing: border-box;
    -webkit-print-color-adjust: exact !important;
    print-color-adjust: exact !important;
}
@page {
    size: $sizeRule;
    margin: ${config.marginTopMm}mm ${config.marginRightMm}mm ${config.marginBottomMm}mm ${config.marginLeftMm}mm;
    background-color: ${themeColors.bg};
    ${if (config.showPageNumbers) """
    @bottom-right {
        content: counter(page) " / " counter(pages);
        font-family: $fontCss;
        font-size: 8.5pt;
        color: ${themeColors.meta};
    }
    """ else ""}
}
html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    min-height: 100%;
    background-color: ${themeColors.bg} !important;
    color: ${themeColors.text} !important;
}
body {
    font-family: $fontCss;
    font-size: ${config.fontSizePt}pt;
    line-height: ${config.lineHeight};
    text-align: ${if (isJustified) "justify; hyphens: auto; text-justify: inter-word;" else "left;"};
    padding: 0;
}
.note-header {
    border-bottom: ${if (isLatexPreset) "0.6pt" else "1.5pt"} solid ${themeColors.border};
    padding-bottom: 12px;
    margin-bottom: 20px;
    ${if (isLatexPreset) "text-align: center;" else ""}
}
h1.note-title {
    font-size: ${if (isLatexPreset) "20pt" else "22pt"};
    font-weight: 700;
    margin: 0 0 6px 0;
    color: ${themeColors.heading};
    ${if (isLatexPreset) "text-align: center;" else ""}
}
.metadata-bar {
    font-size: 9pt;
    color: ${themeColors.meta};
    ${if (isLatexPreset) "text-align: center; font-style: italic;" else ""}
}
.metadata-bar .pin-badge {
    color: ${themeColors.accent};
    font-weight: bold;
    margin-left: 6px;
}
h1, h2, h3, h4 { color: ${themeColors.heading}; margin-top: 1.5em; margin-bottom: 0.5em; }
h1 {
    font-size: 16pt;
    border-bottom: 0.6pt solid ${themeColors.border};
    padding-bottom: 3pt;
    text-align: left;
}
h2 { font-size: 14pt; }
h3 { font-size: 12pt; }
p {
    margin: 0 0 ${config.paragraphSpacingPt}pt 0;
    overflow-wrap: anywhere;
    word-break: break-word;
}
li, blockquote {
    overflow-wrap: anywhere;
    word-break: break-word;
}
table {
    border-collapse: collapse;
    width: 100%;
    max-width: 100%;
    table-layout: fixed;
    word-wrap: break-word;
    margin: 14px 0;
    page-break-inside: avoid;
}
th, td {
    border: 0.75pt solid ${themeColors.border};
    padding: 6pt 9pt;
    text-align: left;
    overflow-wrap: anywhere;
    word-break: break-word;
}
th {
    background-color: ${themeColors.codeBg};
    font-weight: 600;
}
code {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    background-color: ${themeColors.codeBg};
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 0.9em;
    white-space: pre-wrap;
    word-break: break-word;
    max-width: 100%;
}
pre {
    background-color: ${themeColors.codeBg};
    border: 0.75pt solid ${themeColors.border};
    padding: 10pt;
    border-radius: 4px;
    overflow-x: hidden;
    white-space: pre-wrap;
    word-break: break-word;
    max-width: 100%;
    page-break-inside: avoid;
}
pre code {
    background: none;
    padding: 0;
    border: none;
}
blockquote {
    border-left: 3pt solid ${themeColors.accent};
    margin: 10pt 0;
    padding-left: 12pt;
    color: ${themeColors.meta};
    font-style: italic;
}
hr {
    border: none;
    border-top: 0.6pt solid ${themeColors.border};
    margin: 18pt 0;
}
ul, ol {
    padding-left: 20pt;
    margin: 0 0 10pt 0;
}
li { margin-bottom: 3pt; }
.task-item {
    display: flex;
    align-items: flex-start;
    gap: 8pt;
    margin: 4pt 0;
    list-style: none;
}
.task-text {
    flex: 1;
    overflow-wrap: anywhere;
    word-break: break-word;
}
img, svg, .katex-display {
    max-width: 100% !important;
    overflow-x: hidden;
}
.math-block, .katex-display {
    margin: 18pt 0;
    text-align: center;
}
mark {
    background-color: rgba(255, 178, 0, 0.25);
    padding: 1px 4px;
    border-radius: 3px;
}
${config.customCss}
</style>
</head>
<body>
    $headerHtml
    <div id="content">
        $bodyHtml
    </div>

    <script>
    function renderLatexFormulas() {
        var contentEl = document.getElementById("content");
        if (!contentEl || typeof katex === "undefined") return;

        var html = contentEl.innerHTML;
        // Block math: $$ ... $$
        html = html.replace(/\$\$([\s\S]+?)\$\$/g, function(match, formula) {
            try {
                return katex.renderToString(formula.trim(), { displayMode: true, throwOnError: false });
            } catch(e) {
                return match;
            }
        });

        // Inline math: $ ... $
        html = html.replace(/\$([^\$\n]+?)\$/g, function(match, formula) {
            try {
                return katex.renderToString(formula.trim(), { displayMode: false, throwOnError: false });
            } catch(e) {
                return match;
            }
        });

        contentEl.innerHTML = html;
    }
    document.addEventListener("DOMContentLoaded", renderLatexFormulas);
    renderLatexFormulas();
    window.onload = renderLatexFormulas;
    </script>
</body>
</html>
        """.trimIndent()
    }

    private data class ThemeColors(
        val bg: String,
        val text: String,
        val heading: String,
        val border: String,
        val codeBg: String,
        val meta: String,
        val accent: String
    )

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split("\\s+".toRegex()).size
    }

    private fun convertMarkdownToHtml(markdown: String, themeColors: ThemeColors): String {
        val lines = markdown.lines()
        val sb = StringBuilder()
        var inCodeBlock = false
        var inTable = false
        var inList = false
        var inOrderedList = false
        var inMathBlock = false

        for (line in lines) {
            val trimmed = line.trim()

            // Code blocks
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append("</code></pre>\n")
                    inCodeBlock = false
                } else {
                    closeLists(sb, inList, inOrderedList)
                    inList = false
                    inOrderedList = false
                    val lang = trimmed.removePrefix("```").trim()
                    sb.append("<pre><code class=\"language-$lang\">")
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                sb.append(escapeHtml(line)).append("\n")
                continue
            }

            // Math blocks
            if (trimmed == "$$") {
                closeLists(sb, inList, inOrderedList)
                inList = false
                inOrderedList = false
                if (inMathBlock) {
                    sb.append("$$\n</div>\n")
                    inMathBlock = false
                } else {
                    sb.append("<div class=\"math-block\">\n$$")
                    inMathBlock = true
                }
                continue
            }

            if (inMathBlock) {
                sb.append(escapeHtml(line)).append("\n")
                continue
            }

            // Single line block math: $$ formula $$
            if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
                closeLists(sb, inList, inOrderedList)
                inList = false
                inOrderedList = false
                val formula = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                sb.append("<div class=\"math-block\">$$").append(escapeHtml(formula)).append("$$</div>\n")
                continue
            }

            // Tables
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (!inTable) {
                    closeLists(sb, inList, inOrderedList)
                    inList = false
                    inOrderedList = false
                    sb.append("<table>\n")
                    inTable = true
                    sb.append("<thead><tr>")
                    val cells = trimmed.split("|").filter { it.isNotEmpty() }
                    for (c in cells) sb.append("<th>").append(formatInline(c.trim())).append("</th>")
                    sb.append("</tr></thead>\n<tbody>\n")
                    continue
                } else {
                    if (trimmed.contains("---")) {
                        continue
                    }
                    sb.append("<tr>")
                    val cells = trimmed.split("|").filter { it.isNotEmpty() }
                    for (c in cells) sb.append("<td>").append(formatInline(c.trim())).append("</td>")
                    sb.append("</tr>\n")
                    continue
                }
            } else if (inTable) {
                sb.append("</tbody></table>\n")
                inTable = false
            }

            // Task list: checked
            if (trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
                trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ") ||
                trimmed.startsWith("+ [x] ") || trimmed.startsWith("+ [X] ")) {
                closeLists(sb, inList, inOrderedList)
                inList = false
                inOrderedList = false
                val item = trimmed.substring(6)
                sb.append("<div class=\"task-item\"><svg width=\"11pt\" height=\"11pt\" viewBox=\"0 0 16 16\" class=\"task-checkbox checked\" style=\"flex-shrink: 0; margin-top: 2pt;\"><rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" rx=\"3\" fill=\"${themeColors.accent}\" stroke=\"${themeColors.accent}\" stroke-width=\"1.6\"/><path d=\"M4 8l3 3 5-6\" fill=\"none\" stroke=\"#000000\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg><span class=\"task-text\">")
                    .append(formatInline(item))
                    .append("</span></div>\n")
                continue
            }
            // Task list: unchecked
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") || trimmed.startsWith("+ [ ] ")) {
                closeLists(sb, inList, inOrderedList)
                inList = false
                inOrderedList = false
                val item = trimmed.substring(6)
                sb.append("<div class=\"task-item\"><svg width=\"11pt\" height=\"11pt\" viewBox=\"0 0 16 16\" class=\"task-checkbox unchecked\" style=\"flex-shrink: 0; margin-top: 2pt;\"><rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" rx=\"3\" fill=\"none\" stroke=\"${themeColors.accent}\" stroke-width=\"1.6\"/></svg><span class=\"task-text\">")
                    .append(formatInline(item))
                    .append("</span></div>\n")
                continue
            }

            // Bullet list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                if (!inList) {
                    closeLists(sb, false, inOrderedList)
                    inOrderedList = false
                    sb.append("<ul>\n")
                    inList = true
                }
                sb.append("<li>").append(formatInline(trimmed.substring(2))).append("</li>\n")
                continue
            }

            // Numbered list
            if (trimmed.matches("^\\d+\\.\\s+.*".toRegex())) {
                if (!inOrderedList) {
                    closeLists(sb, inList, false)
                    inList = false
                    sb.append("<ol>\n")
                    inOrderedList = true
                }
                val text = trimmed.replaceFirst("^\\d+\\.\\s+".toRegex(), "")
                sb.append("<li>").append(formatInline(text)).append("</li>\n")
                continue
            }

            closeLists(sb, inList, inOrderedList)
            inList = false
            inOrderedList = false

            // Headings
            if (trimmed.startsWith("# ")) {
                sb.append("<h1>").append(formatInline(trimmed.removePrefix("# "))).append("</h1>\n")
            } else if (trimmed.startsWith("## ")) {
                sb.append("<h2>").append(formatInline(trimmed.removePrefix("## "))).append("</h2>\n")
            } else if (trimmed.startsWith("### ")) {
                sb.append("<h3>").append(formatInline(trimmed.removePrefix("### "))).append("</h3>\n")
            } else if (trimmed.startsWith("#### ")) {
                sb.append("<h4>").append(formatInline(trimmed.removePrefix("#### "))).append("</h4>\n")
            } else if (trimmed.startsWith("> ")) {
                sb.append("<blockquote>").append(formatInline(trimmed.removePrefix("> "))).append("</blockquote>\n")
            } else if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                sb.append("<hr>\n")
            } else if (trimmed.isEmpty()) {
                sb.append("<br>\n")
            } else {
                sb.append("<p>").append(formatInline(trimmed)).append("</p>\n")
            }
        }

        if (inCodeBlock) sb.append("</code></pre>\n")
        if (inTable) sb.append("</tbody></table>\n")
        if (inMathBlock) sb.append("$$\n</div>\n")
        closeLists(sb, inList, inOrderedList)

        return sb.toString()
    }

    private fun closeLists(sb: StringBuilder, inList: Boolean, inOrderedList: Boolean) {
        if (inList) sb.append("</ul>\n")
        if (inOrderedList) sb.append("</ol>\n")
    }

    private fun formatInline(text: String): String {
        var res = escapeHtml(text)

        // Bold
        res = res.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        res = res.replace(Regex("__(.+?)__"), "<strong>$1</strong>")

        // Italic
        res = res.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        res = res.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "<em>$1</em>")

        // Strikethrough
        res = res.replace(Regex("~~(.+?)~~"), "<del>$1</del>")

        // Highlight ==text==
        res = res.replace(Regex("==(.+?)=="), "<mark>$1</mark>")

        // Inline code `code`
        res = res.replace(Regex("`([^`]+)`"), "<code>$1</code>")

        // Links [text](url)
        res = res.replace(Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)"), "<a href=\"$2\">$1</a>")

        return res
    }
}
