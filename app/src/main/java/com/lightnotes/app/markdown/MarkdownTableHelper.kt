package com.lightnotes.app.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import io.noties.markwon.Markwon
import java.io.File
import java.io.FileInputStream

data class TableBlock(
    val headers: List<String>,
    val rows: List<List<String>>
)

sealed class MarkdownSegment {
    data class Prose(val text: String, val checkboxOffset: Int) : MarkdownSegment()
    data class Table(val table: TableBlock) : MarkdownSegment()
    data class Image(val altText: String, val uriString: String) : MarkdownSegment()
}

object MarkdownTableHelper {

    private val CHECKBOX_REGEX = Regex("(?m)^\\s*[-*+]\\s+\\[[ xX]\\]")
    private val BR_REGEX = Regex("(?i)<br\\s*/?>")
    private val AUTOLINK_URL_REGEX = Regex("<(https?://[^>\\s]+)>")
    private val AUTOLINK_MAIL_REGEX = Regex("<(mailto:[^>\\s]+)>")
    private val CODE_FENCE_REGEX = Regex("^\\s*(```|~~~)")
    private val IMAGE_REGEX = Regex("^\\s*!\\[(.*?)\\]\\((.*?)\\)\\s*$")

    fun countCheckboxes(text: String): Int {
        if (text.isEmpty()) return 0
        return CHECKBOX_REGEX.findAll(text).count()
    }

    fun isDelimiterRow(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('|')) return false
        var inner = trimmed
        if (inner.startsWith('|')) inner = inner.substring(1)
        if (inner.endsWith('|')) inner = inner.substring(0, inner.length - 1)
        val cells = inner.split('|')
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            cell.trim().matches(Regex("^:?-+:?$"))
        }
    }

    fun splitTableRow(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith('|')) trimmed = trimmed.substring(1)
        if (trimmed.endsWith('|')) trimmed = trimmed.substring(0, trimmed.length - 1)

        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var escaped = false

        for (ch in trimmed) {
            if (escaped) {
                if (ch != '|') {
                    sb.append('\\')
                }
                sb.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '|') {
                cells.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (escaped) {
            sb.append('\\')
        }
        cells.add(sb.toString().trim())
        return cells
    }

    fun normalizeCellText(raw: String): String {
        var text = raw.replace(BR_REGEX, "\n")
        text = text.replace(AUTOLINK_URL_REGEX, "[$1]($1)")
        text = text.replace(AUTOLINK_MAIL_REGEX, "[$1]($1)")
        return text.trim()
    }

    fun parseSegments(markdown: String): List<MarkdownSegment> {
        if (markdown.isBlank()) {
            return listOf(MarkdownSegment.Prose(markdown, 0))
        }

        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val segments = mutableListOf<MarkdownSegment>()
        val currentProseLines = mutableListOf<String>()
        var runningCheckboxCount = 0
        var inCodeFence = false

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (CODE_FENCE_REGEX.containsMatchIn(line)) {
                inCodeFence = !inCodeFence
                currentProseLines.add(line)
                i++
                continue
            }

            if (!inCodeFence && IMAGE_REGEX.matches(line)) {
                val match = IMAGE_REGEX.find(line)!!
                val altText = match.groupValues[1]
                val uriString = match.groupValues[2]

                if (currentProseLines.isNotEmpty()) {
                    val proseText = currentProseLines.joinToString("\n")
                    segments.add(MarkdownSegment.Prose(proseText, runningCheckboxCount))
                    runningCheckboxCount += countCheckboxes(proseText)
                    currentProseLines.clear()
                }

                segments.add(MarkdownSegment.Image(altText, uriString))
                i++
                continue
            }

            if (!inCodeFence && line.contains('|') && i + 1 < lines.size && isDelimiterRow(lines[i + 1])) {
                val headerCells = splitTableRow(line).map { normalizeCellText(it) }
                val delimiterCells = splitTableRow(lines[i + 1])

                if (headerCells.isNotEmpty() && delimiterCells.isNotEmpty()) {
                    // Flush existing prose
                    if (currentProseLines.isNotEmpty()) {
                        val proseText = currentProseLines.joinToString("\n")
                        segments.add(MarkdownSegment.Prose(proseText, runningCheckboxCount))
                        runningCheckboxCount += countCheckboxes(proseText)
                        currentProseLines.clear()
                    }

                    val targetColCount = headerCells.size
                    val rows = mutableListOf<List<String>>()
                    i += 2 // skip header and delimiter

                    while (i < lines.size) {
                        val rowLine = lines[i]
                        if (CODE_FENCE_REGEX.containsMatchIn(rowLine)) {
                            break
                        }
                        if (rowLine.isBlank() || !rowLine.contains('|')) {
                            break
                        }

                        val rawCells = splitTableRow(rowLine).map { normalizeCellText(it) }
                        val normalizedRow = if (rawCells.size < targetColCount) {
                            rawCells + List(targetColCount - rawCells.size) { "" }
                        } else if (rawCells.size > targetColCount) {
                            rawCells.take(targetColCount)
                        } else {
                            rawCells
                        }
                        rows.add(normalizedRow)
                        i++
                    }

                    segments.add(MarkdownSegment.Table(TableBlock(headerCells, rows)))
                    continue
                }
            }

            currentProseLines.add(line)
            i++
        }

        if (currentProseLines.isNotEmpty()) {
            val proseText = currentProseLines.joinToString("\n")
            segments.add(MarkdownSegment.Prose(proseText, runningCheckboxCount))
        }

        return if (segments.isEmpty()) listOf(MarkdownSegment.Prose("", 0)) else segments
    }

    fun createTableLayout(
        context: Context,
        table: TableBlock,
        markwon: Markwon,
        isNight: Boolean,
        isPrint: Boolean = false
    ): TableLayout {
        val density = context.resources.displayMetrics.density
        val borderWidth = maxOf(1, (1 * density).toInt())
        val padding = if (isPrint) (8 * density).toInt() else (12 * density).toInt()
        val minCellWidth = if (isPrint) 0 else (88 * density).toInt()
        val maxCellWidth = (240 * density).toInt()
        val cellTextSize = if (isPrint) 11f else 14f

        val borderColor = when {
            isNight -> Color.parseColor("#2C2C2E")
            else -> Color.parseColor("#D1D1D6")
        }

        val headerBg = when {
            isNight -> Color.parseColor("#1C1C1E")
            else -> Color.parseColor("#F2F2F7")
        }

        val oddRowBg = when {
            isNight -> Color.parseColor("#121214")
            else -> Color.parseColor("#FAFAFC")
        }

        val evenRowBg = when {
            isNight -> Color.parseColor("#000000")
            else -> Color.parseColor("#FFFFFF")
        }

        val textColor = when {
            isNight -> Color.parseColor("#F5F5F7")
            else -> Color.parseColor("#111111")
        }

        val tableLayout = TableLayout(context).apply {
            layoutParams = TableLayout.LayoutParams(
                if (isPrint) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isShrinkAllColumns = isPrint
            isStretchAllColumns = true
            val divider = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(borderColor)
                setSize(borderWidth, borderWidth)
            }
            showDividers = LinearLayout.SHOW_DIVIDER_BEGINNING or
                    LinearLayout.SHOW_DIVIDER_MIDDLE or
                    LinearLayout.SHOW_DIVIDER_END
            dividerDrawable = divider
        }

        // Header Row
        val headerRow = TableRow(context).apply {
            layoutParams = TableLayout.LayoutParams(
                if (isPrint) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(headerBg)
            val rowDivider = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(borderColor)
                setSize(borderWidth, borderWidth)
            }
            showDividers = LinearLayout.SHOW_DIVIDER_BEGINNING or
                    LinearLayout.SHOW_DIVIDER_MIDDLE or
                    LinearLayout.SHOW_DIVIDER_END
            dividerDrawable = rowDivider
        }

        table.headers.forEach { headerText ->
            val tv = TextView(context).apply {
                layoutParams = if (isPrint) {
                    TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f)
                } else {
                    TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                if (!isPrint) {
                    minWidth = minCellWidth
                    maxWidth = maxCellWidth
                }
                setPadding(padding, padding, padding, padding)
                textSize = cellTextSize
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textColor)
                setLineSpacing(0f, 1.25f)
                if (isPrint && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                }
                linksClickable = !isPrint
                if (!isPrint) {
                    movementMethod = LinkMovementMethod.getInstance()
                }
            }
            markwon.setMarkdown(tv, headerText)
            headerRow.addView(tv)
        }
        tableLayout.addView(headerRow)

        // Data Rows
        table.rows.forEachIndexed { rowIndex, rowCells ->
            val dataRow = TableRow(context).apply {
                layoutParams = TableLayout.LayoutParams(
                    if (isPrint) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(if (rowIndex % 2 == 1) oddRowBg else evenRowBg)
                val rowDivider = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(borderColor)
                    setSize(borderWidth, borderWidth)
                }
                showDividers = LinearLayout.SHOW_DIVIDER_BEGINNING or
                        LinearLayout.SHOW_DIVIDER_MIDDLE or
                        LinearLayout.SHOW_DIVIDER_END
                dividerDrawable = rowDivider
            }

            rowCells.forEach { cellText ->
                val tv = TextView(context).apply {
                    layoutParams = if (isPrint) {
                        TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f)
                    } else {
                        TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    if (!isPrint) {
                        minWidth = minCellWidth
                        maxWidth = maxCellWidth
                    }
                    setPadding(padding, padding, padding, padding)
                    textSize = cellTextSize
                    setTextColor(textColor)
                    setLineSpacing(0f, 1.25f)
                    if (isPrint && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                    }
                    linksClickable = !isPrint
                    if (!isPrint) {
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                }
                markwon.setMarkdown(tv, cellText)
                dataRow.addView(tv)
            }
            tableLayout.addView(dataRow)
        }

        return tableLayout
    }

    fun decodeSampledBitmap(context: Context, uriString: String, reqWidth: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val openStream: () -> java.io.InputStream? = {
                when {
                    uri.scheme == "content" -> context.contentResolver.openInputStream(uri)
                    uri.scheme == "file" -> {
                        val path = uri.path ?: uriString.removePrefix("file://")
                        val file = File(path)
                        if (file.exists()) FileInputStream(file) else null
                    }
                    else -> {
                        val file = File(uriString.removePrefix("file://"))
                        if (file.exists()) FileInputStream(file) else null
                    }
                }
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openStream()?.use { BitmapFactory.decodeStream(it, null, options) }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var inSampleSize = 1
            val halfWidth = options.outWidth / 2
            while ((halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            openStream()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (_: Throwable) {
            null
        }
    }

    fun createTableScrollView(
        context: Context,
        table: TableBlock,
        markwon: Markwon,
        isNight: Boolean
    ): HorizontalScrollView {
        val density = context.resources.displayMetrics.density
        val tableLayout = createTableLayout(context, table, markwon, isNight, isPrint = false)
        val hScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (12 * density).toInt()
            }
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        }
        hScroll.addView(tableLayout)
        return hScroll
    }
}
