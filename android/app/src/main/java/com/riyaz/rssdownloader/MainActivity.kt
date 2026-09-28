package com.riyaz.rssdownloader

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * RSS Downloader clean reset baseline.
 * The previous mixed UI implementation is intentionally removed.
 * Features are rebuilt only after each RSS KIT/Core/RAY stage is verified.
 */
class MainActivity : AppCompatActivity() {
    private val licensePrefs by lazy { getSharedPreferences("rss-downloader-license", MODE_PRIVATE) }
    private val api by lazy {
        NativeHostApi(
            BuildConfig.RSS_HOST_BASE_URL,
            BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null },
            licensePrefs.getString("app_key", null)
        )
    }
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private lateinit var content: LinearLayout
    private lateinit var urlInput: EditText
    private var currentTab = "social-downloader"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        loadClipboard()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 10, 10))
        }
        root.addView(topBar(), LinearLayout.LayoutParams(-1, dp(72)))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomTabs(), LinearLayout.LayoutParams(-1, dp(72)))
        setContentView(root)
        renderTab()
    }

    private fun topBar(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.rss_downloader_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "RSS Downloader"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("RSS Downloader", 19, Color.WHITE, true))
            addView(text("RSS KIT • RSS Core • RAY", 10, Color.rgb(165, 165, 165), false))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
        addView(button("⚙", 42) {
            Toast.makeText(this@MainActivity, "Settings will be rebuilt.", Toast.LENGTH_SHORT).show()
        })
    }

    private fun bottomTabs(): View = LinearLayout(this).apply {
        setPadding(dp(10), dp(8), dp(10), dp(10))
        setBackgroundColor(Color.rgb(18, 18, 18))
        val tabs = listOf(
            "social-downloader" to "Social",
            "tamil-movies" to "Tamil",
            "tamil-dubbed-movies" to "Dubbed"
        )
        tabs.forEach { tab ->
            addView(button(tab.second, 52) {
                currentTab = tab.first
                renderTab()
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
    }

    private fun renderTab() {
        content.removeAllViews()
        val title = when (currentTab) {
            "social-downloader" -> "Social Downloader"
            "tamil-movies" -> "Tamil Movies"
            else -> "Tamil Dubbed Movies"
        }
        content.addView(text(title, 27, Color.WHITE, true))
        content.addView(text("RSS Downloader → RSS Core → RAY", 12, Color.rgb(180, 180, 180), false).apply {
            setPadding(0, dp(5), 0, dp(18))
        })
        if (currentTab == "social-downloader") renderSocial() else renderMovies()
    }

    private fun renderSocial() {
        val card = card()
        card.addView(text("SOCIAL DOWNLOAD", 11, Color.rgb(212, 175, 55), true))
        card.addView(text("Paste a supported URL and send it to RSS Core for analysis.", 15, Color.WHITE, false).apply {
            setPadding(0, dp(8), 0, dp(14))
        })
        urlInput = EditText(this).apply {
            hint = "Paste URL here…"
            setHintTextColor(Color.rgb(120, 120, 120))
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.rgb(28, 28, 28))
        }
        card.addView(urlInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        val actions = LinearLayout(this).apply {
            addView(button("Paste", 48) { loadClipboard() }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(button("Analyze", 48) { analyze() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(8), 0, 0, 0)
            })
        }
        card.addView(actions)
        content.addView(card)
        content.addView(connectionCard())
    }

    private fun renderMovies() {
        val card = card()
        card.addView(text("MOVIE SEARCH", 11, Color.rgb(212, 175, 55), true))
        val query = EditText(this).apply {
            hint = "Search movies…"
            setHintTextColor(Color.rgb(120, 120, 120))
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.rgb(28, 28, 28))
        }
        card.addView(query, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        card.addView(button("Search", 48) { searchMovies(query.text.toString()) }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) }
        })
        content.addView(card)
        content.addView(connectionCard())
    }

    private fun connectionCard(): View = card().apply {
        addView(text("PLATFORM CONNECTION", 11, Color.rgb(212, 175, 55), true))
        addView(text("RSS Core: " + if (api.configured()) "configured" else "not configured", 13, Color.WHITE, false).apply {
            setPadding(0, dp(9), 0, dp(3))
        })
        addView(text("RAY: reached server-side through RSS Core", 13, Color.rgb(180, 180, 180), false))
    }

    private fun loadClipboard() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val value = clip.getItemAt(0).coerceToText(this).toString().trim()
        if (value.startsWith("http://") || value.startsWith("https://")) {
            if (::urlInput.isInitialized) urlInput.setText(value)
        }
    }

    private fun analyze() {
        val value = urlInput.text.toString().trim()
        if (value.isBlank()) {
            toast("Paste a URL first.")
            return
        }
        toast("Analyzing through RSS Core…")
        api.analyze(value) { result ->
            runOnUiThread {
                result.onSuccess { analysis ->
                    AlertDialog.Builder(this)
                        .setTitle(analysis.title.ifBlank { "RSS Download" })
                        .setMessage(
                            "RSS Core response received.\\n\\nRequest ID: " +
                                analysis.requestId.ifBlank { "not returned" } +
                                "\\nAvailable formats: " + analysis.mediaOptions.size
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure { toast(it.message ?: "RSS Core analysis failed.") }
            }
        }
    }

    private fun searchMovies(query: String) {
        toast("Searching through RSS Core…")
        api.search(currentTab, query.trim()) { result ->
            runOnUiThread {
                result.onSuccess { items ->
                    toast(items.size.toString() + " result(s) received.")
                }.onFailure { toast(it.message ?: "RSS Core search failed.") }
            }
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(Color.rgb(20, 20, 20))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun button(label: String, height: Int, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundColor(Color.rgb(32, 32, 32))
        minHeight = dp(height)
        setOnClickListener { action() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
