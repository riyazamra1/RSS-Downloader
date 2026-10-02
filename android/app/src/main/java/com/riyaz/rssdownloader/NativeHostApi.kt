package com.riyaz.rssdownloader

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.io.OutputStream
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

/** Native Android implementation of the RSS Downloader host contract. */
class NativeHostApi(private val baseUrl: String, private val accessToken: String? = null, private val appKey: String? = null) {
    // RSS Core compatibility: current and legacy downloader gateways are both accepted.
    data class MediaOption(val id: String, val kind: String, val format: String, val quality: String?, val sizeBytes: Long?, val width: Int? = null, val height: Int? = null)
    data class SearchResult(val id: String, val requestId: String?, val title: String, val year: Int?, val thumbnailUrl: String?, val qualities: List<String>, val mediaOptions: List<MediaOption>, val rating: Double?, val ratingSource: String?, val budget: String?, val cost: String?, val releaseDate: String?, val runtime: String?, val genres: List<String>, val language: String?, val director: String?, val synopsis: String?)
    data class Job(val jobId: String, val status: String, val progress: Int?, val title: String?, val thumbnailUrl: String?, val mediaKind: String?, val quality: String?, val format: String?, val downloadedBytes: Long?, val totalBytes: Long?, val speed: Long?, val eta: Long?, val error: String?, val filename: String?, val mimeType: String?)
    data class Analysis(val requestId: String, val title: String, val normalizedUrl: String, val thumbnailUrl: String?, val mediaOptions: List<MediaOption>)

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun <T> deliver(callback: (Result<T>) -> Unit, result: Result<T>) {
        mainHandler.post { callback(result) }
    }

    fun configured(): Boolean = baseUrl.startsWith("https://")

    fun analyze(url: String, callback: (Result<Analysis>) -> Unit) = executor.execute {
        deliver(callback, runCatching {
            val json = requestObject("/api/downloader/analyze", "POST", JSONObject().put("url", url))
            Analysis(json.optString("requestId").ifBlank { json.optString("request_id").ifBlank { json.optString("id") } }, json.optString("title", "RSS Download"), json.optString("normalizedUrl", url), json.optString("thumbnailUrl", "").ifBlank { null }, mediaOptions(json.optJSONArray("mediaOptions") ?: json.optJSONArray("downloadOptions") ?: json.optJSONArray("formats") ?: json.optJSONArray("options")))
        })
    }

    fun search(tab: String, query: String, callback: (Result<List<SearchResult>>) -> Unit) = executor.execute {
        deliver(callback, runCatching {
            val effectiveQuery = if (query.isBlank()) when (tab) {
                TabOrder.TAMIL -> "2026 Tamil films"
                TabOrder.DUBBED -> "2026 Tamil dubbed films"
                else -> query
            } else query
            val json = requestObject("/api/downloader/search", "POST", JSONObject().put("tab", tab).put("query", effectiveQuery))
            val results = mutableListOf<SearchResult>(); val array = json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until array.length()) { val item = array.getJSONObject(i); results += SearchResult(item.optString("id"), item.optString("requestId").ifBlank { null }, item.optString("title", "Untitled"), if (item.has("year") && !item.isNull("year")) item.optInt("year") else null, item.optString("thumbnailUrl", "").ifBlank { null }, jsonStringList(item.optJSONArray("qualities")), mediaOptions(item.optJSONArray("mediaOptions") ?: item.optJSONArray("downloadOptions") ?: item.optJSONArray("formats") ?: item.optJSONArray("options")), numberOrNull(item, "rating"), item.optString("ratingSource", "").ifBlank { null }, item.optString("budget", "").ifBlank { null }, item.optString("cost", "").ifBlank { null }, item.optString("releaseDate", "").ifBlank { null }, item.optString("runtime", "").ifBlank { null }, jsonStringList(item.optJSONArray("genres")), item.optString("language", "").ifBlank { null }, item.optString("director", "").ifBlank { null }, item.optString("synopsis", "").ifBlank { null }) }
            results
        })
    }

    fun listMediaOptions(requestId: String, callback: (Result<List<MediaOption>>) -> Unit) = executor.execute { deliver(callback, runCatching {
        val raw = requestText("/api/downloader/media-options/${enc(requestId)}", "GET", null)
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) mediaOptions(JSONArray(trimmed)) else {
            val json = JSONObject(trimmed)
            mediaOptions(json.optJSONArray("mediaOptions") ?: json.optJSONArray("downloadOptions") ?: json.optJSONArray("formats") ?: json.optJSONArray("options") ?: json.optJSONArray("items"))
        }
    }) }
    fun createDownload(requestId: String, optionId: String, callback: (Result<Job>) -> Unit) = executor.execute { deliver(callback, runCatching { parseJob(requestObject("/api/downloader/download", "POST", JSONObject().put("requestId", requestId).put("mediaOptionId", optionId).put("authorizationApproved", true))) }) }
    fun getDownload(jobId: String, callback: (Result<Job>) -> Unit) = executor.execute { deliver(callback, runCatching {
        runCatching {
            val json = requestObject("/api/downloader/downloads/${enc(jobId)}", "GET", null)
            val jobs = json.optJSONArray("jobs")
            if (jobs != null) {
                (0 until jobs.length()).asSequence().map { jobs.getJSONObject(it) }.firstOrNull { it.optString("jobId").ifBlank { it.optString("job_id") } == jobId }?.let(::parseJob)
                    ?: throw IllegalStateException("Download job not found.")
            } else parseJob(json)
        }.getOrElse {
            val list = requestObject("/api/downloader/downloads", "GET", null).optJSONArray("jobs") ?: throw it
            (0 until list.length()).asSequence().map { list.getJSONObject(it) }
                .firstOrNull { it.optString("jobId").ifBlank { it.optString("job_id") } == jobId }
                ?.let(::parseJob)
                ?: throw IllegalStateException("Download job not found.")
        }
    }) }

    fun listDownloads(callback: (Result<List<Job>>) -> Unit) = executor.execute { deliver(callback, runCatching { val a = requestObject("/api/downloader/downloads", "GET", null).optJSONArray("jobs") ?: JSONArray(); buildList { for (i in 0 until a.length()) add(parseJob(a.getJSONObject(i))) } }) }
    fun cancel(jobId: String, callback: (Result<Job>) -> Unit) = executor.execute { deliver(callback, runCatching { parseJob(requestObject("/api/downloader/cancel/${enc(jobId)}", "POST", JSONObject())) }) }

    /**
     * Streams a completed RAY file through RSS Core directly into the caller-provided
     * SAF output stream. The RSS Core/RAY credentials remain inside request headers.
     */
    fun downloadFile(jobId: String, output: OutputStream, callback: (Result<Long>) -> Unit) = executor.execute {
        deliver(callback, runCatching {
            var copied = 0L
            val connection = URL(baseUrl.trimEnd('/') + "/api/downloader/jobs/${enc(jobId)}/file").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 120000
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("X-RSS-App-Id", "rss-downloader")
                if (!accessToken.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $accessToken")
                if (!appKey.isNullOrBlank()) connection.setRequestProperty("X-RSS-App-Key", appKey)
                val code = connection.responseCode
                if (code !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("RSS Core file download failed ($code). ${error.take(240)}")
                }
                connection.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        copied += read
                    }
                    output.flush()
                }
                copied
            } finally {
                runCatching { output.close() }
                connection.disconnect()
            }
        })
    }

    fun checkPremium(email: String, callback: (Result<Boolean>) -> Unit) = executor.execute { deliver(callback, runCatching {
        if (email.isBlank() || appKey.isNullOrBlank()) return@runCatching false
        val url = baseUrl.trimEnd('/') + "/api/v1/entitlements/check?app_key=" + enc(appKey!!) + "&email=" + enc(email.trim())
        val result = execute(url, "GET", null)
        if (result.first !in 200..299) throw IllegalStateException("Premium entitlement check failed (" + result.first + ").")
        JSONObject(result.second).optBoolean("premium", false)
    }) }

    fun premiumPlan(callback: (Result<JSONObject>) -> Unit) = executor.execute {
        deliver(callback, runCatching { requestObject("/api/v1/payments/plans", "GET", null) })
    }

    fun createPremiumCheckout(email: String, successUrl: String, cancelUrl: String, callback: (Result<PaymentCheckout>) -> Unit) = executor.execute { deliver(callback, runCatching {
        val key = appKey ?: throw IllegalStateException("RSS Downloader account is not registered.")
        if (email.isBlank()) throw IllegalStateException("RSS Downloader account email is missing.")
        val body = JSONObject()
            .put("email", email.trim())
            .put("app_key", key)
            .put("project_key", "rss-downloader")
            .put("plan_key", "RSS_PREMIUM_1_YEAR")
            .put("success_url", successUrl)
            .put("cancel_url", cancelUrl)
        val json = requestObject("/api/v1/payments/checkout", "POST", body)
        PaymentCheckout(
            json.optString("order_id"),
            json.optString("status", "pending"),
            json.optInt("amount_lkr", 0),
            json.optString("currency", "LKR"),
            json.optString("checkout_url").ifBlank { throw IllegalStateException("Payment checkout URL was not returned.") }
        )
    }) }

    data class PaymentCheckout(
        val orderId: String,
        val status: String,
        val amountLkr: Int,
        val currency: String,
        val checkoutUrl: String
    )

    fun paymentOrder(orderId: String, callback: (Result<JSONObject>) -> Unit) = executor.execute {
        deliver(callback, runCatching { requestObject("/api/v1/payments/orders/" + enc(orderId), "GET", null) })
    }

    private fun mediaOptions(array: JSONArray?): List<MediaOption> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { o.optString("mediaOptionId").ifBlank { o.optString("optionId").ifBlank { o.optString("option_id") } } }
                if (id.isBlank()) continue
                val kind = o.optString("kind").ifBlank { o.optString("type").ifBlank { o.optString("mediaType", "media") } }
                val format = o.optString("format").ifBlank { o.optString("extension").ifBlank { o.optString("ext") } }
                val quality = o.optString("quality").ifBlank { o.optString("resolution").ifBlank { null } }
                val size = if (o.has("sizeBytes") && !o.isNull("sizeBytes")) o.optLong("sizeBytes") else if (o.has("size_bytes") && !o.isNull("size_bytes")) o.optLong("size_bytes") else null
                val width = if (o.has("width") && !o.isNull("width")) o.optInt("width") else null
                val height = if (o.has("height") && !o.isNull("height")) o.optInt("height") else null
                add(MediaOption(id, kind, format, quality, size, width, height))
            }
        }
    }
    private fun parseJob(o: JSONObject): Job {
        val jobId = o.optString("jobId").ifBlank { o.optString("job_id") }
        val status = o.optString("status").ifBlank { o.optString("state", "unknown") }
        val progress = if (o.has("progressPercent") && !o.isNull("progressPercent")) o.optDouble("progressPercent").toInt() else if (o.has("progress_percent") && !o.isNull("progress_percent")) o.optDouble("progress_percent").toInt() else null
        val mediaKind = o.optString("mediaKind").ifBlank { o.optString("media_kind").ifBlank { o.optString("kind").ifBlank { null } } }
        val filename = o.optString("filename").ifBlank { o.optString("outputName").ifBlank { o.optString("output_name").ifBlank { null } } }
        return Job(jobId, status, progress, o.optString("title", "").ifBlank { null }, o.optString("thumbnailUrl", "").ifBlank { o.optString("thumbnail_url", "").ifBlank { null } }, mediaKind, o.optString("quality", "").ifBlank { null }, o.optString("format", "").ifBlank { null }, longOrNull(o, "downloadedBytes") ?: longOrNull(o, "downloaded_bytes"), longOrNull(o, "totalBytes") ?: longOrNull(o, "total_bytes"), longOrNull(o, "speedBytesPerSecond") ?: longOrNull(o, "speed_bytes_per_second"), longOrNull(o, "etaSeconds") ?: longOrNull(o, "eta_seconds"), o.optString("error", "").ifBlank { null }, filename, o.optString("mimeType", "").ifBlank { o.optString("mime_type", "").ifBlank { null } })
    }
    private fun numberOrNull(o: JSONObject, key: String): Double? = if (o.has(key) && !o.isNull(key)) o.optDouble(key) else null
    private fun longOrNull(o: JSONObject, key: String): Long? = if (o.has(key) && !o.isNull(key)) o.optLong(key) else null
    private fun jsonStringList(a: JSONArray?): List<String> = if (a == null) emptyList() else buildList { for (i in 0 until a.length()) add(a.optString(i)) }
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun requestObject(path: String, method: String, body: JSONObject?) = JSONObject(requestText(path, method, body))

    private fun requestText(path: String, method: String, body: JSONObject?): String {
        if (!configured()) throw IllegalStateException("RSS host API is not configured.")
        val primary = execute(baseUrl.trimEnd('/') + path, method, body)
        if (primary.first in 200..299) return primary.second
        // RSS Core deployments may expose the dedicated downloader gateway at
        // /api/downloader, /api/v1/downloader, or /v1/downloader.
        // Try compatible gateways on route/transient-server errors.
        if (primary.first == 404 || primary.first in 500..504) {
            val fallbackPaths = buildList {
                if (path.startsWith("/api/") && !path.startsWith("/api/v1/")) {
                    add(path.replaceFirst("/api/", "/api/v1/"))
                    add(path.replaceFirst("/api/", "/v1/"))
                } else if (path.startsWith("/api/v1/")) {
                    add(path.replaceFirst("/api/v1/", "/v1/"))
                }
            }.distinct()
            var last = primary
            for (fallbackPath in fallbackPaths) {
                val fallback = execute(baseUrl.trimEnd('/') + fallbackPath, method, body)
                if (fallback.first in 200..299) return fallback.second
                last = fallback
            }
            throw IllegalStateException(
                "RSS host API request failed (\${primary.first}; fallback \${last.first}). " +
                    last.second.take(240).ifBlank { primary.second.take(240).ifBlank { "Endpoint unavailable." } }
            )
        }
        throw IllegalStateException("RSS host API request failed (\${primary.first}). \${primary.second.take(240).ifBlank { "Endpoint unavailable." }}")
    }

    private fun execute(url: String, method: String, body: JSONObject?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            // IMPORTANT: configure every request property before any operation that can
            // implicitly establish the connection (including getOutputStream()).
            // This prevents: "cannot set request property after connection is made".
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            // RSS Core identifies the calling product separately from the per-user app key.
            c.setRequestProperty("X-RSS-App-Id", "rss-downloader")
            if (!accessToken.isNullOrBlank()) c.setRequestProperty("Authorization", "Bearer $accessToken")
            if (!appKey.isNullOrBlank()) c.setRequestProperty("X-RSS-App-Key", appKey)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(body.toString().toByteArray(Charsets.UTF_8).size)
            }
            val bodyBytes = body?.toString()?.toByteArray(Charsets.UTF_8)
            if (bodyBytes != null) c.outputStream.use { it.write(bodyBytes) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally { c.disconnect() }
    }
}
