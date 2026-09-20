package com.riyaz.rssdownloader

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.LoadAdError

class RssMonetizationActivity : Activity() {
    private lateinit var store: RssMonetizationStore
    private var rewardedAd: RewardedAd? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RssMonetizationStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 48)
        }
        root.addView(TextView(this).apply { text = "RSS Downloader • Rewards"; textSize = 26f; gravity = Gravity.CENTER })
        status = TextView(this).apply { textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 24, 0, 24) }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "▶  Watch ad • Get +$REWARD downloads".replace("$REWARD", RssMonetizationConfig.REWARDED_BONUS_DOWNLOADS.toString())
            textSize = 16f; gravity = Gravity.CENTER; setPadding(28, 22, 28, 22)
            setOnClickListener { showRewardedAd() }
        })
        root.addView(TextView(this).apply {
            text = "★  Premium • No ads • Unlimited downloads"
            textSize = 16f; gravity = Gravity.CENTER; setPadding(28, 22, 28, 22)
        })
        setContentView(root)
        updateStatus()
        MobileAds.initialize(this) { loadRewardedAd() }
    }

    private fun updateStatus() {
        status.text = if (store.isPremium()) "Premium active • Unlimited downloads"
        else "Free today: ${store.freeDownloadsRemaining()} • Bonus credits: ${store.bonusCredits()}"
    }

    private fun loadRewardedAd() {
        RewardedAd.load(this, RssMonetizationConfig.REWARDED_AD_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            })
    }

    private fun showRewardedAd() {
        val ad = rewardedAd ?: run {
            status.text = "Reward ad is loading. Try again in a moment."
            loadRewardedAd()
            return
        }
        ad.show(this) { _: RewardItem ->
            store.addRewardedCredits()
            updateStatus()
            rewardedAd = null
            loadRewardedAd()
        }
    }
}
