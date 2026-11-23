package com.lightnotes.app.markdown

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MermaidRenderer private constructor(private val context: Context) {

    private val diagramCache = LruCache<String, Bitmap>(30)
    private var offscreenWebView: WebView? = null
    private var isPageReady = false
    private var currentCallback: ((Bitmap?) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        if (offscreenWebView != null) return
        try {
            offscreenWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRenderFinished(success: Boolean) {
                        post {
                            if (success) {
                                captureBitmap()
                            } else {
                                currentCallback?.invoke(null)
                                currentCallback = null
                            }
                        }
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        isPageReady = true
                    }
                }

                loadUrl("file:///android_asset/mermaid/mermaid.html")
            }
        } catch (_: Throwable) {
            isPageReady = false
        }
    }

    private fun captureBitmap() {
        val webView = offscreenWebView ?: return
        try {
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val width = webView.measuredWidth.coerceAtLeast(300)
            val height = webView.measuredHeight.coerceAtLeast(150)
            webView.layout(0, 0, width, height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)

            currentCallback?.invoke(bitmap)
            currentCallback = null
        } catch (_: Throwable) {
            currentCallback?.invoke(null)
            currentCallback = null
        }
    }

    fun renderMermaid(code: String, onRendered: (Bitmap?) -> Unit) {
        val cached = diagramCache.get(code)
        if (cached != null) {
            onRendered(cached)
            return
        }

        initWebView()
        val webView = offscreenWebView
        if (webView == null) {
            onRendered(null)
            return
        }

        currentCallback = { bitmap ->
            if (bitmap != null) {
                diagramCache.put(code, bitmap)
            }
            onRendered(bitmap)
        }

        val escapedCode = code.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")

        webView.evaluateJavascript("renderDiagram('$escapedCode');", null)
    }

    companion object {
        @Volatile
        private var INSTANCE: MermaidRenderer? = null

        fun getInstance(context: Context): MermaidRenderer {
            return INSTANCE ?: synchronized(this) {
                val instance = MermaidRenderer(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
