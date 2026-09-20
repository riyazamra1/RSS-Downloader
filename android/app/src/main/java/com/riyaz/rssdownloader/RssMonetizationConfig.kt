package com.riyaz.rssdownloader

object RssMonetizationConfig {
    const val DAILY_FREE_DOWNLOADS = 5
    const val REWARDED_BONUS_DOWNLOADS = 2
    const val PREMIUM_MONTHLY_PRODUCT = "rss_downloader_premium_monthly"
    const val PREMIUM_LIFETIME_PRODUCT = "rss_downloader_premium_lifetime"
    const val CREDIT_PACK_SMALL = "rss_downloader_credits_10"
    const val REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"
    fun isProductionAdsConfigured(): Boolean =
        REWARDED_AD_UNIT != "ca-app-pub-3940256099942544/5224354917"
}
