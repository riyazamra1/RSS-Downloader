package com.riyaz.rssdownloader

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Native Android implementation of the RSS Downloader host contract. */
class NativeHostApi(private val baseUrl: String, private val accessToken: String? = null) {
    data class MediaOption(val id: String, val kind: String, val format: String, val quality: String?, val sizeBytes: Long?)
    data class SearchResult(val id: String, val requestId: String?, val title: String, val year: Int?, val thumbnailUrl: String?, val qualities: List<String>, val mediaOptions: List<MediaOption>)
    data class Job(val jobId: String, val status: String, val progress: Int?, val title: String?, val thumbnailUrl: String?, val mediaKind: String?, val quality: String?, val format: String?, val downloadedBytes: Long?, val totalBytes: Long?, val speed: Long?, val eta: Long?, val error: String?)
    data class Analysis(val requestId: String, val title: String, val normalizedUrl: String, val thumbnailUrl: String?, val mediaOptions: List<MediaOption>)

    private val executor = Executors.newCachedThreadPool()

    fun configured(): Boolean = baseUrl.startsWith("https://")

    fun analyze(url: String, callback: (Result<Analysis>) -> Unit) = executor.execute {
        callback(runCatching {
            val json = request("/api/downloader/analyze", "POST", JSONObject().put("url", url))
            val requestId = json.optString("requestId")
            Analysis(
                requestId,
                json.optString("title", "RSS Download"),
                json.optString("normalizedUrl", url),
                json.optString("thumbnailUrl", "").ifBlank { null },
                mediaOptions(json.optJSONArray("mediaOptions")),
            )
        })
    }

    fun search(tab: String, query: String, callback: (Result<List<SearchResult>>) -> Unit) = executor.execute {
        callback(runCatching {
            val json = request("/api/downloader/search", "POST", JSONObject().put("tab", tab).put("query", query))
            val results = mutableListOf<SearchResult>()
            val array = json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                results += SearchResult(
                    item.optString("id"),
                    item.optString("requestId").ifBlank { null },
                    item.optString("title", "Untitled"),
                    if (item.has("year") && !item.isNull("year")) item.optInt("year") else null,
                    item.optString("thumbnailUrl", "").ifBlank { null },
                    jsonStringList(item.optJSONArray("qualities")),
                    mediaOptions(item.optJSONArray("mediaOptions")),
                )
            }
            results
        })
    }

    fun listMediaOptions(requestId: String, callback: (Result<List<MediaOption>>) -> Unit) = executor.execute {
        callback(runCatching {
            val json = request("/api/downloader/media-options/${enc(requestId)}", "GET", null)
            if (json.has("mediaOptions")) mediaOptions(json.optJSONArray("mediaOptions"))
            else if (json.has("options")) mediaOptions(json.optJSONArray("options"))
            else if (json.has("results")) mediaOptions(json.optJSONArray("results"))
            else mediaOptions(json.optJSONArray(null))
        })
    }

    fun createDownload(requestId: String, optionId: String, callback: (Result<Job>) -> Unit) = executor.execute {
        callback(runCatching {
            parseJob(request("/api/downloader/download", "POST", JSONObject()
                .put("requestId", requestId)
                .put("mediaOptionId", optionId)
                .put("authorizationApproved", true)))
        })
    }

    fun listDownloads(callback: (Result<List<Job>>) -> Unit) = executor.execute {
        callback(runCatching {
            val json = request("/api/downloader/downloads", "GET", null)
            val array = json.optJSONArray("jobs") ?: JSONArray()
            buildList { for (i in 0 until array.length()) add(parseJob(array.getJSONObject(i))) }
        })
    }

    fun cancel(jobId: String, callback: (Result<Job>) -> Unit) = executor.execute {
        callback(runCatching { parseJob(request("/api/downloader/cancel/${enc(jobId)}", "POST", JSONObject())) })
    }

    private fun mediaOptions(array: JSONArray?): List<MediaOption> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { o.optString("mediaOptionId") }
                if (id.isBlank()) continue
                add(MediaOption(
                    id,
                    o.optString("kind", "media"),
                    o.optString("format", ""),
                    o.optString("quality", "").ifBlank { null },
                    if (o.has("sizeBytes") && !o.isNull("sizeBytes")) o.optLong("sizeBytes") else null,
                ))
            }
        }
    }

    private fun parseJob(o: JSONObject): Job = Job(
        o.optString("jobId"),
        o.optString("status", "unknown"),
        if (o.has("progressPercent") && !o.isNull("progressPercent")) o.optDouble("progressPercent").toInt() else null,
        o.optString("title", "").ifBlank { null },
        o.optString("thumbnailUrl", "").ifBlank { null },
        o.optString("mediaKind", "").ifBlank { null },
        o.optString("quality", "").ifBlank { null },
        o.optString("format", "").ifBlank { null },
        longOrNull(o, "downloadedBytes"),
        longOrNull(o, "totalBytes"),
        longOrNull(o, "speedBytesPerSecond"),
        longOrNull(o, "etaSeconds"),
        o.optString("error", "").ifBlank { null },
    )

    private fun longOrNull(o: JSONObject, key: String): Long? = if (o.has(key) && !o.isNull(key)) o.optLong(key) else null
    private fun jsonStringList(a: JSONArray?): List<String> = if (a == null) emptyList() else buildList { for (i in 0 until a.length()) add(a.optString(i)) }
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun request(path: String, method: String, body: JSONObject?): JSONObject {
        if (!configured()) throw IllegalStateException("RSS host API is not configured.")
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        if (!accessToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $accessToken")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("RSS host API request failed ($code). ${text.take(180)}")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
