package com.riyaz.rssdownloader

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import org.json.JSONObject

class RssMonetizationActivity : Activity() {
    private lateinit var store: RssMonetizationStore
    private lateinit var status: TextView
    private lateinit var plansContainer: LinearLayout
    private lateinit var loading: ProgressBar

    data class Plan(val key: String, val title: String, val description: String, val amount: Int, val currency: String, val oneTime: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RssMonetizationStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 44, 36, 36)
        }
        root.addView(TextView(this).apply { text = "RSS Downloader"; textSize = 16f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply {
            text = "Free • Premium"; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 8, 0, 10)
        })
        root.addView(TextView(this).apply { text = "Choose your RSS Core plan"; textSize = 15f; gravity = Gravity.CENTER })
        status = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 18, 0, 12) }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "FREE\n\n5 downloads per day\nRewarded credits available\nNo payment required"
            textSize = 16f; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 })
        root.addView(TextView(this).apply {
            text = "PREMIUM"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 8, 0, 8)
        })
        loading = ProgressBar(this)
        root.addView(loading)
        plansContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(plansContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Pricing is managed by RSS Core"; textSize = 13f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 4)
            setOnClickListener { openWebPage("https://rsscore.cv/pricing") }
        })
        setContentView(root)
        updateStatus()
        loadPlans()
        handlePaymentReturn(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent); setIntent(intent); handlePaymentReturn(intent)
    }

    private fun loadPlans() {
        loading.visibility = View.VISIBLE
        status.text = "Loading current RSS Core pricing…"
        api().premiumPlan { result ->
            runOnUiThread {
                loading.visibility = View.GONE
                result.onSuccess { json ->
                    val plans = parsePlans(json)
                    plansContainer.removeAllViews()
                    if (plans.isEmpty()) status.text = "No Premium plans are currently available."
                    else {
                        status.text = if (store.isPremium()) "Premium active" else "Select a Premium plan"
                        plans.forEach { addPlanCard(it) }
                    }
                }.onFailure { status.text = it.message ?: "Could not load RSS Core pricing." }
            }
        }
    }

    private fun parsePlans(json: JSONObject): List<Plan> {
        val array = json.optJSONArray("plans")
            ?: json.optJSONObject("data")?.optJSONArray("plans")
            ?: json.optJSONArray("data")
            ?: return emptyList()
        val result = mutableListOf<Plan>()
        for (i in 0 until array.length()) {
            val p = array.optJSONObject(i) ?: continue
            val key = p.optString("plan_key").ifBlank { p.optString("key").ifBlank { p.optString("id") } }
            if (key.isBlank()) continue
            val rawTitle = p.optString("name").ifBlank { p.optString("title") }
            val duration = p.optString("duration").ifBlank { p.optString("billing_period").ifBlank { p.optString("billingPeriod") } }
            val oneTime = p.optBoolean("one_time", false) || p.optBoolean("oneTime", false) ||
                duration.equals("one_time", true) || duration.equals("lifetime", true) ||
                key.contains("LIFETIME", true) || key.contains("ONE_TIME", true)
            val title = when {
                oneTime -> "One-Time"
                duration.contains("year", true) || key.contains("1_YEAR", true) || key.contains("YEARLY", true) -> "Year"
                rawTitle.isNotBlank() -> rawTitle
                else -> key.replace("_", " ")
            }
            val amount = when {
                p.has("amount_lkr") -> p.optInt("amount_lkr")
                p.has("price_lkr") -> p.optInt("price_lkr")
                p.has("amount") -> p.optInt("amount")
                p.has("price") -> p.optInt("price")
                else -> 0
            }
            result.add(Plan(key, title, p.optString("description"), amount, p.optString("currency").ifBlank { "LKR" }, oneTime))
        }
        return result.filter { it.title.equals("Year", true) || it.title.equals("One-Time", true) || it.oneTime }.distinctBy { it.key }
    }

    private fun addPlanCard(plan: Plan) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20); setBackgroundResource(android.R.drawable.dialog_holo_light_frame) }
        card.addView(TextView(this).apply { text = plan.title; textSize = 20f; typeface = Typeface.DEFAULT_BOLD })
        card.addView(TextView(this).apply { text = if (plan.amount > 0) "@@{plan.currency} @@{plan.amount}" else "Price shown by RSS Core"; textSize = 19f; setPadding(0, 6, 0, 4) })
        if (plan.description.isNotBlank()) card.addView(TextView(this).apply { text = plan.description; textSize = 14f; setPadding(0, 0, 0, 8) })
        card.addView(Button(this).apply { text = "Continue with @@{plan.title}"; setOnClickListener { startPremiumCheckout(plan) } })
        plansContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
    }

    private fun api(): NativeHostApi {
        val prefs = getSharedPreferences("rss-downloader-license", MODE_PRIVATE)
        return NativeHostApi(BuildConfig.RSS_HOST_BASE_URL, BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null }, prefs.getString("app_key", null))
    }

    private fun startPremiumCheckout(plan: Plan) {
        val prefs = getSharedPreferences("rss-downloader-license", MODE_PRIVATE)
        val email = prefs.getString("email", "").orEmpty()
        if (email.isBlank()) { status.text = "Register your RSS Downloader account before upgrading."; return }
        status.text = "Creating secure @@{plan.title} checkout…"
        api().createPremiumCheckout(email, plan.key, "rssdownloader://premium/success", "rssdownloader://premium/cancel") { result ->
            runOnUiThread {
                result.onSuccess { checkout ->
                    getSharedPreferences("rss-monetization", MODE_PRIVATE).edit().putString("pending_order_id", checkout.orderId).apply()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkout.checkoutUrl)))
                    status.text = "Complete payment in the browser. RSS Core will verify Premium automatically."
                }.onFailure { status.text = it.message ?: "Premium checkout could not be started." }
            }
        }
    }

    private fun handlePaymentReturn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "rssdownloader" || data.host != "premium") return
        if (data.path == "/cancel") { status.text = "Premium checkout was cancelled."; return }
        if (data.path != "/success") return
        status.text = "Payment returned. Verifying Premium entitlement…"
        val prefs = getSharedPreferences("rss-downloader-license", MODE_PRIVATE)
        api().checkPremium(prefs.getString("email", "").orEmpty()) { result ->
            runOnUiThread {
                result.onSuccess { premium ->
                    store.setServerPremium(premium)
                    status.text = if (premium) "Premium active • Unlimited downloads" else "Payment returned, but Premium is not active yet. Check again shortly."
                }.onFailure { status.text = it.message ?: "Could not verify Premium status." }
            }
        }
    }

    private fun updateStatus() { status.text = if (store.isPremium()) "Premium active • Unlimited downloads" else "Free plan active" }

    private fun openWebPage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (packageManager.resolveActivity(intent, 0) != null) startActivity(intent)
        else Toast.makeText(this, "No browser is available.", Toast.LENGTH_LONG).show()
    }
}
