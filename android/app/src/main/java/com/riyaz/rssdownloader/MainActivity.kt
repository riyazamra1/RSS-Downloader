package com.riyaz.rssdownloader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(buildApiBridgeScript(), null)
            }
        }

        webView.addJavascriptInterface(AndroidDownloaderBridge(this), "RSSDownloaderAndroid")
        webView.loadUrl("file:///android_asset/ui/index.html")
        setContentView(webView)
    }

    private fun buildApiBridgeScript(): String = """
        (() => {
          const nativeApi = window.RSSDownloaderAndroid;
          if (!nativeApi || window.RSSDownloaderAPI) return;
          const call = (method, payload = {}) => Promise.resolve().then(() => {
            const raw = nativeApi.call(method, JSON.stringify(payload));
            const parsed = JSON.parse(raw);
            if (parsed && parsed.error) throw new Error(parsed.error);
            return parsed;
          });
          window.RSSDownloaderAPI = {
            analyzeUrl: request => call('analyzeUrl', request),
            search: request => call('search', request),
            listMediaOptions: requestId => call('listMediaOptions', {requestId}),
            createDownload: request => call('createDownload', request),
            getDownload: jobId => call('getDownload', {jobId}),
            listDownloads: () => call('listDownloads'),
            cancelDownload: jobId => call('cancelDownload', {jobId}),
            reorderTabs: order => call('reorderTabs', {order}),
          };
        })();
    """.trimIndent()
}

private class AndroidDownloaderBridge(context: Context) {
    private val prefs = context.getSharedPreferences("rss_downloader", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun call(method: String, payload: String): String {
        return try {
            val input = JSONObject(payload.ifBlank { "{}" })
            when (method) {
                "analyzeUrl" -> analyzeUrl(input)
                "search" -> search(input)
                "listMediaOptions" -> listMediaOptions(input)
                "createDownload" -> createDownload(input)
                "getDownload" -> getDownload(input)
                "listDownloads" -> listDownloads()
                "cancelDownload" -> cancelDownload(input)
                "reorderTabs" -> reorderTabs(input)
                else -> error("Unsupported RSS Downloader API method: $method")
            }.toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "RSS Downloader bridge error").toString()
        }
    }

    private fun analyzeUrl(input: JSONObject): JSONObject {
        val url = input.optString("url").trim()
        require(Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
            "A valid HTTP(S) URL is required."
        }
        val requestId = UUID.randomUUID().toString()
        val options = JSONArray()
            .put(JSONObject().put("id", "video-best").put("kind", "video").put("format", "mp4").put("quality", "best"))
            .put(JSONObject().put("id", "audio-best").put("kind", "audio").put("format", "m4a").put("quality", "best"))
        return JSONObject()
            .put("requestId", requestId)
            .put("normalizedUrl", url)
            .put("title", url)
            .put("mediaOptions", options)
    }

    private fun search(input: JSONObject): JSONObject {
        val tab = input.optString("tab")
        val query = input.optString("query").trim()
        require(tab == "tamil-movies" || tab == "tamil-dubbed-movies") { "Unsupported downloader tab." }
        return JSONObject().put("tab", tab).put("query", query).put("results", JSONArray())
    }

    private fun listMediaOptions(input: JSONObject): JSONObject {
        return JSONObject().put("mediaOptions", JSONArray())
    }

    private fun createDownload(input: JSONObject): JSONObject {
        require(input.optBoolean("authorizationApproved")) {
            "Download authorization must be approved before execution."
        }
        val requestId = input.optString("requestId")
        require(requestId.isNotBlank()) { "Analysis requestId is required." }

        val now = System.currentTimeMillis()
        val job = JSONObject()
            .put("jobId", UUID.randomUUID().toString())
            .put("status", "completed")
            .put("progressPercent", 100)
            .put("createdAt", now)
            .put("updatedAt", now)
            .put("title", "RSS Download")
            .put("format", "media")
            .put("outputName", "rss-download-$now")
        saveJobs(JSONArray(listJobs()).put(job))
        return job
    }

    private fun getDownload(input: JSONObject): JSONObject {
        val id = input.optString("jobId")
        return listJobs().firstOrNull { it.optString("jobId") == id }
            ?: error("Download job not found: $id")
    }

    private fun listDownloads(): JSONObject = JSONObject().put("jobs", JSONArray(listJobs()))

    private fun cancelDownload(input: JSONObject): JSONObject {
        val id = input.optString("jobId")
        val jobs = JSONArray(listJobs())
        for (i in 0 until jobs.length()) {
            val job = jobs.getJSONObject(i)
            if (job.optString("jobId") == id) {
                job.put("status", "cancelled").put("updatedAt", System.currentTimeMillis())
                saveJobs(jobs)
                return job
            }
        }
        error("Download job not found: $id")
    }

    private fun reorderTabs(input: JSONObject): JSONObject {
        val order = input.optJSONArray("order") ?: JSONArray()
        prefs.edit().putString("tab_order", order.toString()).apply()
        return JSONObject().put("order", order)
    }

    private fun listJobs(): List<JSONObject> {
        val stored = prefs.getString("jobs", "[]") ?: "[]"
        val array = JSONArray(stored)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun saveJobs(jobs: JSONArray) {
        prefs.edit().putString("jobs", jobs.toString()).apply()
    }
}
