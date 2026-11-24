package com.lightnotes.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import com.lightnotes.app.markdown.MarkdownEngine
import com.lightnotes.app.markdown.MarkdownSegment
import com.lightnotes.app.markdown.MarkdownTableHelper
import java.io.File
import java.io.FileOutputStream

object PdfExportHelper {

    const val LAYOUT_WIDTH_PX = 1080
    const val A4_PAGE_WIDTH_PT = 595
    const val A4_PAGE_HEIGHT_PT = 842
    const val A4_MARGIN_PT = 40

    fun buildRenderedLayout(
        context: Context,
        title: String,
        content: String,
        dateStr: String,
        wordCount: Int,
        folderName: String? = null,
        isNight: Boolean = false
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val padding = (32 * density).toInt()

        val bgColor = if (isNight) Color.BLACK else Color.WHITE
        val textColor = if (isNight) Color.parseColor("#F5F5F7") else Color.parseColor("#111111")
        val metaColor = if (isNight) Color.parseColor("#8E8E93") else Color.parseColor("#555555")
        val dividerColor = if (isNight) Color.parseColor("#242426") else Color.parseColor("#D1D1D6")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                LAYOUT_WIDTH_PX,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        if (title.isNotBlank()) {
            val titleTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10 * density).toInt()
                }
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textColor)
                text = title
            }
            layout.addView(titleTv)

            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxOf(1, (1 * density).toInt())
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
                setBackgroundColor(dividerColor)
            }
            layout.addView(divider)
        }

        // Metadata
        val metaText = if (!folderName.isNullOrBlank()) {
            "$dateStr | $wordCount words | $folderName"
        } else {
            "$dateStr | $wordCount words"
        }
        val metaTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (18 * density).toInt()
            }
            textSize = 12f
            setTextColor(metaColor)
            text = metaText
        }
        layout.addView(metaTv)

        // Markdown body
        val markdownEngine = MarkdownEngine.getInstance(context)
        val markwon = if (isNight) markdownEngine.markwon else markdownEngine.printMarkwon
        val segments = MarkdownTableHelper.parseSegments(content)

        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Prose -> {
                    if (segment.text.isNotBlank() || segments.size == 1) {
                        val tv = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = (12 * density).toInt()
                            }
                            textSize = 15f
                            setLineSpacing(0f, 1.35f)
                            setTextColor(textColor)
                        }
                        markwon.setMarkdown(tv, segment.text)
                        layout.addView(tv)
                    }
                }
                is MarkdownSegment.Table -> {
                    val tableLayout = MarkdownTableHelper.createTableLayout(
                        context,
                        segment.table,
                        markwon,
                        isNight = isNight,
                        isPrint = !isNight
                    ).apply {
                        val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (8 * density).toInt()
                            bottomMargin = (16 * density).toInt()
                        }
                        layoutParams = lp
                        isStretchAllColumns = true
                    }
                    layout.addView(tableLayout)
                }
            }
        }

        // Measure & Layout
        val widthSpec = View.MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH_PX, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        layout.measure(widthSpec, heightSpec)
        val measuredHeight = maxOf(layout.measuredHeight, 200)
        layout.layout(0, 0, LAYOUT_WIDTH_PX, measuredHeight)

        return layout
    }

    fun collectSplitPoints(layout: LinearLayout): List<Int> {
        val candidates = sortedSetOf<Int>()
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            val childTop = child.top
            val childBottom = child.bottom
            candidates.add(childBottom)

            if (child is TextView && child.layout != null) {
                val textLayout = child.layout
                for (line in 0 until textLayout.lineCount) {
                    val lineBottom = childTop + child.totalPaddingTop + textLayout.getLineBottom(line)
                    candidates.add(lineBottom)
                }
            } else if (child is TableLayout) {
                for (r in 0 until child.childCount) {
                    val row = child.getChildAt(r)
                    val rowBottom = childTop + row.bottom
                    candidates.add(rowBottom)
                }
            }
        }
        candidates.add(layout.measuredHeight)
        return candidates.toList()
    }

    fun exportNoteToPdf(
        context: Context,
        title: String,
        content: String,
        outputFile: File,
        dateStr: String = "",
        wordCount: Int = 0,
        folderName: String? = null
    ): File {
        val layout = buildRenderedLayout(
            context = context,
            title = title,
            content = content,
            dateStr = dateStr,
            wordCount = wordCount,
            folderName = folderName,
            isNight = false
        )

        val printableWidth = A4_PAGE_WIDTH_PT - (A4_MARGIN_PT * 2) // 515
        val printableHeight = A4_PAGE_HEIGHT_PT - (A4_MARGIN_PT * 2) // 762
        val scale = printableWidth.toFloat() / LAYOUT_WIDTH_PX.toFloat()
        val maxSliceHeight = (printableHeight / scale).toInt()

        val pdfDocument = PdfDocument()
        val splitPoints = collectSplitPoints(layout)
        val totalHeight = maxOf(layout.measuredHeight, 1)

        var currentY = 0
        var pageNumber = 1

        while (currentY < totalHeight) {
            val remainingHeight = totalHeight - currentY
            val sliceHeight = if (remainingHeight <= maxSliceHeight) {
                remainingHeight
            } else {
                val maxTargetY = currentY + maxSliceHeight
                val candidate = splitPoints.filter { it in (currentY + 1)..maxTargetY }.maxOrNull()
                if (candidate != null && candidate > currentY) {
                    candidate - currentY
                } else {
                    maxSliceHeight
                }
            }

            val pageInfo = PdfDocument.PageInfo.Builder(A4_PAGE_WIDTH_PT, A4_PAGE_HEIGHT_PT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Pristine white page
            canvas.drawColor(Color.WHITE)

            canvas.save()
            canvas.translate(A4_MARGIN_PT.toFloat(), A4_MARGIN_PT.toFloat())
            canvas.scale(scale, scale)

            canvas.clipRect(0f, 0f, LAYOUT_WIDTH_PX.toFloat(), sliceHeight.toFloat())
            canvas.translate(0f, -currentY.toFloat())
            layout.draw(canvas)
            canvas.restore()

            pdfDocument.finishPage(page)
            pageNumber++
            currentY += sliceHeight
        }

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile
    }

    fun exportNoteToImage(
        context: Context,
        title: String,
        content: String,
        outputFile: File,
        dateStr: String = "",
        wordCount: Int = 0,
        folderName: String? = null,
        isNight: Boolean = false
    ): File {
        val layout = buildRenderedLayout(
            context = context,
            title = title,
            content = content,
            dateStr = dateStr,
            wordCount = wordCount,
            folderName = folderName,
            isNight = isNight
        )

        val height = maxOf(400, layout.measuredHeight)
        val bitmap = Bitmap.createBitmap(LAYOUT_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = if (isNight) Color.BLACK else Color.WHITE
        canvas.drawColor(bgColor)
        layout.draw(canvas)

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        return outputFile
    }
}
