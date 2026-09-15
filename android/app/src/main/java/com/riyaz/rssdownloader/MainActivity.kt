package com.riyaz.rssdownloader

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var clipboardManager: ClipboardManager

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clipboardManager = getSystemService(ClipboardManager::class.java)

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(ClipboardBridge(), "AndroidClipboard")
        webView.loadUrl("file:///android_asset/ui/index.html")
        setContentView(webView)
    }

    inner class ClipboardBridge {
        @JavascriptInterface
        fun getText(): String {
            val description = clipboardManager.primaryClipDescription ?: return ""
            if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            ) return ""
            return clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: ""
        }
    }
}
