package com.riyaz.rssdownloader

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RssMonetizationStore(context: Context) {
    private val prefs = context.getSharedPreferences("rss-monetization", Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun resetIfNeeded() {
        if (prefs.getString("day", "") != today()) {
            prefs.edit().putString("day", today()).putInt("freeUsed", 0).apply()
        }
    }

    fun isPremium(): Boolean = prefs.getBoolean("premium", false)

    fun freeDownloadsRemaining(): Int {
        resetIfNeeded()
        return (RssMonetizationConfig.DAILY_FREE_DOWNLOADS - prefs.getInt("freeUsed", 0)).coerceAtLeast(0)
    }

    fun bonusCredits(): Int = prefs.getInt("bonusCredits", 0)

    fun canStartDownload(): Boolean = isPremium() || freeDownloadsRemaining() > 0 || bonusCredits() > 0

    fun consumeDownload() {
        if (isPremium()) return
        resetIfNeeded()
        val bonus = bonusCredits()
        if (bonus > 0) prefs.edit().putInt("bonusCredits", bonus - 1).apply()
        else prefs.edit().putInt("freeUsed", prefs.getInt("freeUsed", 0) + 1).apply()
    }

    fun addRewardedCredits() {
        prefs.edit().putInt("bonusCredits", bonusCredits() + RssMonetizationConfig.REWARDED_BONUS_DOWNLOADS).apply()
    }

    fun setPremium(enabled: Boolean) {
        prefs.edit().putBoolean("premium", enabled).apply()
    }
}
