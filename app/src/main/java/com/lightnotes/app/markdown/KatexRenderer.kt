package com.lightnotes.app.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class KatexRenderer private constructor(private val context: Context) {

    private val bitmapCache = LruCache<String, Bitmap>(50)
    private var offscreenWebView: WebView? = null
    private var isWebViewReady = false

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        if (offscreenWebView != null) return
        try {
            offscreenWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        isWebViewReady = true
                    }
                }
                loadUrl("file:///android_asset/katex/katex.html")
            }
        } catch (_: Throwable) {
            isWebViewReady = false
        }
    }

    fun getCachedBitmap(latex: String): Bitmap? {
        return bitmapCache.get(latex)
    }

    fun cacheRenderedBitmap(latex: String, bitmap: Bitmap) {
        bitmapCache.put(latex, bitmap)
    }

    fun renderOffscreen(latex: String, isBlock: Boolean, onRendered: (Bitmap?) -> Unit) {
        val cached = getCachedBitmap(latex)
        if (cached != null) {
            onRendered(cached)
            return
        }

        initWebView()
        val webView = offscreenWebView
        if (webView == null || !isWebViewReady) {
            onRendered(null)
            return
        }

        val escaped = latex.replace("\\", "\\\\").replace("'", "\\'")
        webView.evaluateJavascript("renderMath('$escaped', $isBlock);") {
            try {
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val width = webView.measuredWidth.coerceAtLeast(100)
                val height = webView.measuredHeight.coerceAtLeast(60)
                webView.layout(0, 0, width, height)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                webView.draw(canvas)

                cacheRenderedBitmap(latex, bitmap)
                onRendered(bitmap)
            } catch (_: Throwable) {
                onRendered(null)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: KatexRenderer? = null

        fun getInstance(context: Context): KatexRenderer {
            return INSTANCE ?: synchronized(this) {
                val instance = KatexRenderer(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
