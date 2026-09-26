package com.riyaz.rssdownloader

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
            text = "▶  Watch ad • Get +" + RssMonetizationConfig.REWARDED_BONUS_DOWNLOADS + " downloads"
            textSize = 16f; gravity = Gravity.CENTER; setPadding(28, 22, 28, 22)
            setOnClickListener { showRewardedAd() }
        })
        root.addView(TextView(this).apply {
            text = "★  Upgrade to Premium • No ads • Unlimited downloads"
            textSize = 16f; gravity = Gravity.CENTER; setPadding(28, 22, 28, 22)
            setOnClickListener { startPremiumCheckout() }
        })
        setContentView(root)
        updateStatus()
        MobileAds.initialize(this) { loadRewardedAd() }
        handlePaymentReturn(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentReturn(intent)
    }

    private fun startPremiumCheckout() {
        val email = getSharedPreferences("rss-downloader-license", MODE_PRIVATE)
            .getString("email", "").orEmpty()
        if (email.isBlank()) {
            status.text = "Register your RSS Downloader account before upgrading."
            return
        }
        status.text = "Creating secure Premium checkout…"
        val successUrl = "rssdownloader://premium/success"
        val cancelUrl = "rssdownloader://premium/cancel"
        NativeHostApi(
            BuildConfig.RSS_HOST_BASE_URL,
            BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null },
            getSharedPreferences("rss-downloader-license", MODE_PRIVATE).getString("app_key", null)
        ).createPremiumCheckout(email, successUrl, cancelUrl) { result ->
            runOnUiThread {
                result.onSuccess { checkout ->
                    getSharedPreferences("rss-monetization", MODE_PRIVATE)
                        .edit().putString("pending_order_id", checkout.orderId).apply()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkout.checkoutUrl)))
                    status.text = "Complete payment in the browser. RSS Core will verify Premium automatically."
                }.onFailure {
                    status.text = it.message ?: "Premium checkout could not be started."
                }
            }
        }
    }

    private fun handlePaymentReturn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "rssdownloader" || data.host != "premium") return
        if (data.path == "/cancel") {
            status.text = "Premium checkout was cancelled."
            return
        }
        if (data.path != "/success") return
        status.text = "Payment returned. Verifying Premium entitlement…"
        val email = getSharedPreferences("rss-downloader-license", MODE_PRIVATE)
            .getString("email", "").orEmpty()
        NativeHostApi(
            BuildConfig.RSS_HOST_BASE_URL,
            BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null },
            getSharedPreferences("rss-downloader-license", MODE_PRIVATE).getString("app_key", null)
        ).checkPremium(email) { result ->
            runOnUiThread {
                result.onSuccess { premium ->
                    store.setServerPremium(premium)
                    status.text = if (premium) "Premium active • Unlimited downloads"
                    else "Payment returned, but Premium is not active yet. Check again shortly."
                }.onFailure {
                    status.text = it.message ?: "Could not verify Premium status."
                }
            }
        }
    }

    private fun updateStatus() {
        status.text = if (store.isPremium()) "Premium active • Unlimited downloads"
        else "Free today: " + store.freeDownloadsRemaining() + " • Bonus credits: " + store.bonusCredits()
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
