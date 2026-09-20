package com.riyaz.rsscore

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Central RSS Core entitlement client.
 * Server state is cached locally only for offline continuity; refresh from RSS Core
 * whenever the authenticated RSS account becomes available.
 */
class RssCoreEntitlementClient(private val context: Context) {
    companion object {
        private const val PREFS = "rss-core-entitlements"
        private const val SERVER_PREMIUM = "server_premium"
        private const val CORE_HOST = "https://rsscore.cv"
    }

    fun cachedPremium(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(SERVER_PREMIUM, false)

    fun checkPremium(email: String, appKey: String, callback: (Result<Boolean>) -> Unit) {
        if (email.isBlank() || appKey.isBlank()) {
            callback(Result.success(false))
            return
        }
        Thread {
            try {
                val url = URL(
                    "$CORE_HOST/api/v1/entitlements/check?app_key=" +
                        URLEncoder.encode(appKey, "UTF-8") +
                        "&email=" + URLEncoder.encode(email.trim(), "UTF-8")
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val premium = JSONObject(response).optBoolean("premium", false)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(SERVER_PREMIUM, premium).apply()
                callback(Result.success(premium))
            } catch (t: Throwable) {
                callback(Result.failure(t))
            }
        }.start()
    }
}
