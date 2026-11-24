package com.lightnotes.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {

    private val pages = mutableListOf<PdfDocument.Page>()
    private var isClosed = false

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        if (isClosed) throw IllegalStateException("document is closed!")
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val constructor = PdfDocument.Page::class.java.getDeclaredConstructor(
            Canvas::class.java,
            PdfDocument.PageInfo::class.java
        )
        constructor.isAccessible = true
        val page = constructor.newInstance(canvas, pageInfo)
        pages.add(page)
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        if (isClosed) throw IllegalStateException("document is closed!")
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        if (isClosed) throw IllegalStateException("document is closed!")
        val header = "%PDF-1.4\n"
        val body = "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Count ${pages.size}/Kids[]>>endobj\n" +
                "xref\n0 3\n0000000000 65535 f\n0000000009 00000 n\n0000000052 00000 n\n" +
                "trailer<</Size 3/Root 1 0 R>>\nstartxref\n110\n%%EOF\n"
        out.write((header + body).toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    @Implementation
    fun close() {
        isClosed = true
    }
}
