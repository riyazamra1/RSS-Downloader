package com.riyaz.rssdownloader

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("rss-downloader", MODE_PRIVATE) }
    private val monetization by lazy { RssMonetizationStore(this) }
    // RSS Core is app-controlled. The host is no longer user-editable.
    private val api by lazy { NativeHostApi(
        BuildConfig.RSS_HOST_BASE_URL,
        BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null },
        getSharedPreferences("rss-downloader-license", MODE_PRIVATE).getString("app_key", null)
    ) }
    private val imageExecutor = Executors.newFixedThreadPool(4)
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var urlInput: EditText
    private lateinit var stateText: TextView
    private lateinit var mediaPanel: LinearLayout
    private var currentTab = TabOrder.SOCIAL
    private var lightMode = false
    private var appearanceMode = "dark"
    private var authenticatedThisSession = false
    private var biometricPromptActive = false
    private val saveLocationRequestCode = 4201
    private var drawerOpen = false
    private val clipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!isFinishing && ::urlInput.isInitialized) runOnUiThread { readClipboardUrl(prefs.getBoolean("autoAnalyze", true)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove legacy user-entered host values so an old accidental change can never redirect the app.
        prefs.edit().remove("hostUrl").remove("hostToken").apply()
        appearanceMode = prefs.getString("appearance", "dark") ?: "dark"
        lightMode = appearanceMode == "light" || (appearanceMode == "system" && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_NO)
        applyTheme()
        buildApp()
        requestRuntimePermissions()
        readClipboardUrl(prefs.getBoolean("autoAnalyze", true))
        syncPremiumEntitlement()
    }

    private fun syncPremiumEntitlement() {
        val email = getSharedPreferences("rss-downloader-license", MODE_PRIVATE).getString("email", "").orEmpty()
        api.checkPremium(email) { result -> runOnUiThread {
            result.onSuccess { premium ->
                monetization.setServerPremium(premium)
                if (premium) toast("RSS Core Premium access is active.")
            }
        }}
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.READ_MEDIA_IMAGES
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.READ_MEDIA_VIDEO
        } else if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 4102)
    }

    override fun onStart() {
        super.onStart()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("appLock", false) && !authenticatedThisSession) authenticateWithBiometric()
        if (::urlInput.isInitialized) readClipboardUrl(prefs.getBoolean("autoAnalyze", true))
    }

    override fun onStop() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        super.onStop()
        if (!isChangingConfigurations) authenticatedThisSession = false
    }

    override fun onBackPressed() {
        if (drawerOpen) {
            root.findViewWithTag<View>("rss_drawer_overlay")?.let { closeMenu(it) }
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildApp() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg()) }
        root.addView(buildTopBar())
        content = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        root.addView(content)
        root.addView(buildBottomNav())
        setContentView(root)
        showHome()
    }

    private fun buildTopBar(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, dp(78))
        addView(button("☰", 46) { showMenu() }, LinearLayout.LayoutParams(dp(46), dp(46)))
        addView(logoView(48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(10), 0, dp(12), 0) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("RSS Downloader", 18, textColor(), true))
            addView(text("Smart • Fast • Organized", 11, muted(), false).apply { setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(iconButton(R.drawable.ic_rss_settings, 46, "Settings") { showSettings() }, LinearLayout.LayoutParams(dp(46), dp(46)))
    }

    // Clean fixed navigation: no floating pill, no oversized container, and no duplicate Settings tab.
    private fun buildBottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = rounded(surface(), 18)
        elevation = dp(10).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, dp(72)).apply {
            setMargins(dp(12), 0, dp(12), dp(10))
        }
        TabOrder.defaults.forEach { id ->
            val selected = id == currentTab
            val item = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(3))
                background = rounded(if (selected) withAlpha(accent(), 28) else Color.TRANSPARENT, 14)
                setOnClickListener { if (currentTab != id) { currentTab = id; showHome() } }
            }
            item.addView(text(
                when (id) { TabOrder.SOCIAL -> "↗"; TabOrder.TAMIL -> "▣"; else -> "◈" },
                19, if (selected) accent() else muted(), true
            ))
            item.addView(text(tabShortLabel(id), 11, if (selected) textColor() else muted(), selected))
            addView(item, LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
    }

    private fun showHome() {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(18)) }
        if (currentTab == TabOrder.SOCIAL) buildSocial(box) else buildMovie(box, currentTab)
        scroll.addView(box)
        content.addView(scroll)
    }

    private fun buildSocial(box: LinearLayout) {
        val hero = panel().apply { setPadding(dp(20), dp(20), dp(20), dp(18)) }
        hero.addView(text("DOWNLOAD ANYWHERE", 10, accent(), true))
        hero.addView(text("Paste a link. RSS handles the rest.", 23, textColor(), true).apply { setPadding(0, dp(6), 0, 0) })
        hero.addView(text("Automatic clipboard detection • RSS Core analysis", 11, muted(), false).apply { setPadding(0, dp(5), 0, dp(16)) })
        val inputShell = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)); background = rounded(surface2(), 16) }
        urlInput = EditText(this).apply { hint = "Paste URL here…"; setHintTextColor(muted()); setTextColor(textColor()); setSingleLine(true); textSize = 14f; setPadding(dp(14), 0, dp(8), 0); background = ColorDrawableCompat.transparent() }
        inputShell.addView(urlInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        inputShell.addView(secondaryButton("Paste") { readClipboardUrl(false) }, LinearLayout.LayoutParams(dp(76), dp(46)))
        hero.addView(inputShell)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
        actions.addView(primaryButton("Analyze URL") { analyzeUrl() }, LinearLayout.LayoutParams(0, dp(50), 1f))
        actions.addView(secondaryButton("Clear") { urlInput.text.clear(); mediaPanel.visibility = View.GONE; stateText.visibility = View.GONE }, LinearLayout.LayoutParams(dp(92), dp(50)).apply { setMargins(dp(10), 0, 0, 0) })
        hero.addView(actions)
        stateText = text("", 13, muted(), false).apply { visibility = View.GONE; setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(surface2(), 12) }
        hero.addView(stateText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        box.addView(hero)
        mediaPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(4), dp(4), dp(4), 0) }
        box.addView(mediaPanel)
        box.addView(buildStorageCard())
    }

    private fun buildStorageCard(): View {
        val stat = StatFs(filesDir.absolutePath)
        val total = stat.totalBytes.coerceAtLeast(0L)
        val free = stat.availableBytes.coerceAtLeast(0L).coerceAtMost(total)
        val used = (total - free).coerceAtLeast(0L)
        val percent = if (total > 0L) ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100) else 0
        return panel().apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(text("DEVICE STORAGE", 10, muted(), true))
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(formatBytes(free) + " free", 18, textColor(), true))
                    addView(text(formatBytes(used) + " used  •  " + formatBytes(total) + " total", 11, muted(), false).apply { setPadding(0, dp(3), 0, 0) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(percent.toString() + "%", 13, accent(), true))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(progress(percent))
        }
    }

    private fun buildMovie(box: LinearLayout, tab: String) {
        box.addView(text(tabLabel(tab).uppercase(Locale.US), 10, Color.rgb(140, 156, 255), true).apply { setPadding(dp(18), dp(12), 0, dp(5)) })
        box.addView(text("Latest movies", 28, textColor(), true).apply { setPadding(dp(18), 0, 0, dp(12)) })
        val search = EditText(this).apply { hint = "Search movies…"; setHintTextColor(muted()); setTextColor(textColor()); setSingleLine(true); background = rounded(bg(), 14); setPadding(dp(16), 0, dp(16), 0) }
        val searchRow = LinearLayout(this).apply { setPadding(dp(18), 0, dp(18), dp(12)) }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        searchRow.addView(primaryButton("Search") { movieSearch(tab, search.text.toString()) }, LinearLayout.LayoutParams(dp(110), dp(52)).apply { setMargins(dp(10), 0, 0, 0) })
        box.addView(searchRow)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        box.addView(grid)
        loadLatestMovies(tab, grid)
    }

    private fun loadLatestMovies(tab: String, grid: LinearLayout) {
        grid.removeAllViews()
        grid.addView(text("Loading latest movies…", 13, muted(), false).apply { setPadding(dp(6), dp(12), dp(6), dp(12)) })
        if (!api.configured()) { grid.removeAllViews(); grid.addView(text("RSS Core is unavailable.", 13, muted(), false)); return }
        api.search(tab, "") { result -> runOnUiThread {
            grid.removeAllViews()
            result.onSuccess { items ->
                if (items.isEmpty()) { grid.addView(text("No latest movies returned.", 13, muted(), false)); return@onSuccess }
                items.forEach { item ->
                    grid.addView(movieCard(item, tab), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
                }
            }.onFailure { grid.addView(text(it.message ?: "Failed to load latest movies.", 13, muted(), false)) }
        }}
    }


    private fun movieCard(item: NativeHostApi.SearchResult, tab: String): View = panel().apply {
        setPadding(0, 0, 0, dp(14))
        isClickable = true
        setOnClickListener { showMovieDetails(item, tab) }
        val image = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.rss_downloader_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = item.title
        }
        addView(image, LinearLayout.LayoutParams(-1, dp(220)))
        loadImage(item.thumbnailUrl, image)
        if (item.thumbnailUrl.isNullOrBlank()) loadWikipediaPoster(item.title, image)
        val info = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), 0)
        }
        info.addView(text(item.title, 19, textColor(), true))
        val facts = buildString {
            item.year?.let { append(it) }
            item.runtime?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append("  •  "); append(it) }
            item.language?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append("  •  "); append(it) }
        }
        if (facts.isNotBlank()) info.addView(text(facts, 11, muted(), false).apply { setPadding(0, dp(4), 0, 0) })
        item.rating?.let { rating ->
            val source = item.ratingSource?.takeIf { it.isNotBlank() } ?: "Verified source"
            info.addView(text("★ " + String.format(Locale.US, "%.1f", rating) + "/10  •  " + source, 13, accent(), true).apply { setPadding(0, dp(8), 0, 0) })
        }
        val chips = listOfNotNull(
            item.genres.takeIf { it.isNotEmpty() }?.joinToString(" • "),
            item.budget?.takeIf { it.isNotBlank() }?.let { "Budget " + it },
            item.cost?.takeIf { it.isNotBlank() }?.let { "Cost " + it }
        )
        if (chips.isNotEmpty()) info.addView(text(chips.joinToString("  •  "), 10, muted(), false).apply { setPadding(0, dp(6), 0, 0) })
        val buttons = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), 0) }
        buttons.addView(primaryButton("Download") {
            val requestId = item.requestId
            if (requestId != null && item.mediaOptions.isNotEmpty()) renderOptions(buttons, requestId, item.mediaOptions)
            else if (requestId != null) api.listMediaOptions(requestId) { result -> runOnUiThread {
                result.onSuccess { options -> renderOptions(buttons, requestId, options) }
                    .onFailure { toast(it.message ?: "Unable to load download options.") }
            }} else toast("Download is not available for this movie yet.")
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(secondaryButton("Details") { showMovieDetails(item, tab) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(10), 0, 0, 0) })
        addView(info)
        addView(buttons)
    }

    private fun showMovieDetails(item: NativeHostApi.SearchResult, tab: String) {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(28)) }
        box.addView(secondaryButton("← Back to " + tabLabel(tab)) { showHome() }, LinearLayout.LayoutParams(-1, dp(46)))
        val hero = panel().apply { setPadding(0, 0, 0, dp(18)) }
        val poster = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = item.title }
        hero.addView(poster, LinearLayout.LayoutParams(-1, dp(330)))
        loadImage(item.thumbnailUrl, poster)
        if (item.thumbnailUrl.isNullOrBlank()) loadWikipediaPoster(item.title, poster)
        hero.addView(text(item.title, 24, textColor(), true).apply { setPadding(dp(18), dp(16), dp(18), 0) })
        item.rating?.let { rating ->
            val source = item.ratingSource?.takeIf { it.isNotBlank() } ?: "Verified source"
            hero.addView(text("★ " + String.format(Locale.US, "%.1f", rating) + "/10  •  " + source, 14, accent(), true).apply { setPadding(dp(18), dp(7), dp(18), 0) })
        }
        val meta = listOfNotNull(item.year?.toString(), item.releaseDate?.takeIf { it.isNotBlank() }, item.runtime?.takeIf { it.isNotBlank() }, item.language?.takeIf { it.isNotBlank() }, item.genres.takeIf { it.isNotEmpty() }?.joinToString(", "))
        if (meta.isNotEmpty()) hero.addView(text(meta.joinToString("  •  "), 11, muted(), false).apply { setPadding(dp(18), dp(7), dp(18), 0) })
        if (!item.director.isNullOrBlank()) hero.addView(text("Director: " + item.director, 12, textColor(), true).apply { setPadding(dp(18), dp(10), dp(18), 0) })
        if (!item.synopsis.isNullOrBlank()) hero.addView(text(item.synopsis, 13, muted(), false).apply { setPadding(dp(18), dp(12), dp(18), 0) })
        val finance = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(4)) }
        finance.addView(text("MOVIE DETAILS", 11, muted(), true).apply { setPadding(0, dp(12), 0, dp(8)) })
        finance.addView(infoLine("Budget", item.budget))
        finance.addView(infoLine("Cost / Box Office", item.cost))
        if (item.rating == null) finance.addView(text("Rating: Not supplied by the movie data source.", 11, muted(), false).apply { setPadding(0, dp(8), 0, 0) })
        box.addView(hero)
        box.addView(panel().apply { addView(finance) })
        if (item.requestId != null) {
            box.addView(primaryButton("Download Movie") {
                if (item.mediaOptions.isNotEmpty()) renderOptions(box, item.requestId, item.mediaOptions)
                else api.listMediaOptions(item.requestId) { result -> runOnUiThread {
                    result.onSuccess { options -> renderOptions(box, item.requestId, options) }
                        .onFailure { toast(it.message ?: "Unable to load download options.") }
                }}
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        }
        scroll.addView(box)
        content.addView(scroll)
    }

    private fun infoLine(label: String, value: String?): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(text(label, 12, muted(), false), LinearLayout.LayoutParams(0, dp(34), 1f))
        addView(text(value?.takeIf { it.isNotBlank() } ?: "Not available", 13, textColor(), true))
    }

    private fun analyzeUrl() {
        val url = urlInput.text.toString().trim()
        if (url.isBlank()) { toast("Paste a URL first."); return }
        if (prefs.getBoolean("confirmAnalyze", false)) {
            AlertDialog.Builder(this).setTitle("Analyze URL?").setMessage(url)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Analyze") { _, _ -> analyzeUrlNow(url) }.show()
            return
        }
        analyzeUrlNow(url)
    }

    private fun analyzeUrlNow(url: String) {
        stateText.visibility = View.VISIBLE
        mediaPanel.visibility = View.VISIBLE
        mediaPanel.removeAllViews()
        stateText.text = if (api.configured()) "Analyzing URL…" else "RSS Core is unavailable."
        if (!api.configured()) return
        api.analyze(url) { result -> runOnUiThread {
            result.onSuccess { analysis ->
                stateText.text = analysis.title.ifBlank { analysis.normalizedUrl }
                renderThumbnail(mediaPanel, analysis.thumbnailUrl, analysis.title)
                renderOptions(mediaPanel, analysis.requestId, analysis.mediaOptions)
            }.onFailure { stateText.text = it.message ?: "Analysis failed." }
        }}
    }

    private fun movieSearch(tab: String, query: String) {
        val q = query.trim()
        if (!api.configured()) { if (q.isBlank()) showHome() else toast("RSS Core is unavailable."); return }
        content.removeAllViews()
        val loading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(22)) }
        loading.addView(text("Searching…", 18, textColor(), true))
        content.addView(ScrollView(this).apply { addView(loading) })
        api.search(tab, q) { result -> runOnUiThread {
            result.onSuccess { results ->
                val panel = panel().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
                panel.addView(text("Search results", 18, textColor(), true))
                val optionsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
                panel.addView(optionsPanel)
                if (results.isEmpty()) panel.addView(text("No results returned by RSS Core.", 13, muted(), false).apply { setPadding(0, dp(12), 0, dp(8)) })
                results.forEach { item ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
                    val image = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = item.title }
                    row.addView(image, LinearLayout.LayoutParams(dp(82), dp(98)))
                    loadImage(item.thumbnailUrl, image)
                    if (item.thumbnailUrl.isNullOrBlank()) loadWikipediaPoster(item.title, image)
                    row.addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(item.title, 14, textColor(), true))
                        addView(text(item.year?.toString() ?: "", 11, muted(), false))
                        if (item.qualities.isNotEmpty()) addView(text(item.qualities.joinToString(" • "), 10, muted(), false))
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
                    row.addView(primaryButton("Select") {
                        val requestId = item.requestId
                        if (item.mediaOptions.isNotEmpty() && requestId != null) renderOptions(optionsPanel, requestId, item.mediaOptions)
                        else if (requestId != null) {
                            optionsPanel.removeAllViews()
                            optionsPanel.addView(text("Loading quality options…", 13, muted(), false))
                            api.listMediaOptions(requestId) { optionsResult -> runOnUiThread {
                                optionsResult.onSuccess { options -> renderOptions(optionsPanel, requestId, options) }
                                    .onFailure { optionsPanel.removeAllViews(); optionsPanel.addView(text(it.message ?: "Failed to load quality options.", 13, muted(), false)) }
                            }}
                        } else {
                            optionsPanel.removeAllViews()
                            optionsPanel.addView(text("RSS Core did not return a request ID.", 13, muted(), false))
                        }
                    })
                    panel.addView(row)
                }
                content.removeAllViews()
                content.addView(ScrollView(this).apply { addView(panel) })
            }.onFailure { toast(it.message ?: "Search failed.") }
        }}
    }

    private fun renderOptions(target: LinearLayout, requestId: String, options: List<NativeHostApi.MediaOption>) {
        target.visibility = View.VISIBLE
        target.removeAllViews()
        if (options.isEmpty()) { target.addView(text("RSS Core returned no authorized formats.", 13, muted(), false)); return }
        target.addView(text("Choose an authorized format", 13, muted(), false))
        options.forEach { option ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(13), 0, dp(13)) }
            row.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(option.kind.uppercase(Locale.US), 13, textColor(), true))
                addView(text(listOfNotNull(option.format.ifBlank { null }, option.quality, option.sizeBytes?.let(::formatBytes)).joinToString(" • "), 11, muted(), false))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(primaryButton("Download") { createDownload(requestId, option.id) })
            target.addView(row)
        }
    }

    private fun createDownload(requestId: String, optionId: String) {
        if (!api.configured()) { toast("RSS Core is unavailable."); return }
        when (DownloadNetworkPolicy.check(this, prefs.getBoolean("wifiOnly", false))) {
            DownloadNetworkPolicy.Decision.NoConnection -> { toast("No validated internet connection. Connect to Wi-Fi or mobile data."); return }
            DownloadNetworkPolicy.Decision.WifiRequired -> { toast("Wi-Fi only is enabled. Connect to Wi-Fi before downloading."); return }
            DownloadNetworkPolicy.Decision.Allowed -> Unit
        }
        if (!monetization.canStartDownload()) {
            startActivity(Intent(this, RssMonetizationActivity::class.java))
            return
        }
        api.createDownload(requestId, optionId) { result -> runOnUiThread {
            result.onSuccess {
                monetization.consumeDownload()
                if (hasNotificationPermission()) startDownloadKeepAlive()
                showDownloads()
            }.onFailure { toast(it.message ?: "Download failed.") }
        }}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun showDownloads() {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        box.addView(text("DOWNLOADS", 10, Color.rgb(140, 156, 255), true))
        box.addView(text("Downloads", 30, textColor(), true))
        box.addView(secondaryButton("Refresh") { showDownloads() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        val state = text(if (api.configured()) "Loading…" else "RSS Core is unavailable.", 13, muted(), false)
        box.addView(state)
        scroll.addView(box)
        content.addView(scroll)
        if (api.configured()) api.listDownloads { result -> runOnUiThread {
            result.onSuccess { jobs ->
                state.text = if (jobs.isEmpty()) "No downloads yet." else "${jobs.size} download(s)"
                jobs.forEach { box.addView(jobCard(it)) }
            }.onFailure { state.text = it.message ?: "Failed to load downloads." }
        }}
    }

    private fun jobCard(job: NativeHostApi.Job): View = panel().apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val image = ImageView(this@MainActivity).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP }
        addView(image, LinearLayout.LayoutParams(dp(56), dp(56)))
        loadImage(job.thumbnailUrl, image)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(job.title ?: "RSS Download", 14, textColor(), true))
            addView(text("${job.status} • ${job.progress ?: 0}%", 11, muted(), false))
            addView(progress(job.progress ?: 0))
            if (!job.error.isNullOrBlank()) addView(text(job.error, 11, Color.rgb(220, 90, 90), false))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
    }

    private fun showMenu() {
        if (drawerOpen) return
        drawerOpen=true
        val overlay=FrameLayout(this).apply{tag="rss_drawer_overlay";setBackgroundColor(Color.argb(if(lightMode)55 else 90,0,0,0));setOnClickListener{closeMenu(this)}}
        val drawer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(18),dp(14),dp(14));background=rounded(surface(),28);elevation=dp(18).toFloat();setOnClickListener{}}
        drawer.addView(panel().apply{gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(14),dp(18),dp(14),dp(16));addView(logoView(78),LinearLayout.LayoutParams(dp(78),dp(78)));addView(text("WELCOME BACK",10,accent(),true).apply{setPadding(0,dp(10),0,dp(0))});addView(text(prefs.getString("name","USER").orEmpty().ifBlank{"USER"},18,textColor(),true));prefs.getString("email","").orEmpty().takeIf{it.isNotBlank()}?.let{addView(text(it,10,muted(),false))}})
        drawer.addView(text("MAIN",10,muted(),true).apply{setPadding(dp(8),dp(18),dp(8),dp(6))})
        drawer.addView(menuItem("⌂","Home",false){closeMenu(overlay);showHome()})
        drawer.addView(menuItem("↗","Social Downloader",currentTab==TabOrder.SOCIAL){closeMenu(overlay);currentTab=TabOrder.SOCIAL;showHome()})
        drawer.addView(menuItem("◉","Tamil Movies",currentTab==TabOrder.TAMIL){closeMenu(overlay);currentTab=TabOrder.TAMIL;showHome()})
        drawer.addView(menuItem("▣","Tamil Dubbed Movies",currentTab==TabOrder.DUBBED){closeMenu(overlay);currentTab=TabOrder.DUBBED;showHome()})
        drawer.addView(text("SUPPORT",10,muted(),true).apply{setPadding(dp(8),dp(16),dp(8),dp(6))})
        drawer.addView(menuItem("⇩","Downloads",false){closeMenu(overlay);showDownloads()})
        drawer.addView(menuItem("Rewards","Earnings & Rewards",false){closeMenu(overlay);startActivity(Intent(this,RssMonetizationActivity::class.java))})
        drawer.addView(menuItem("★","RSS Premium",false){closeMenu(overlay);showPremium()})
        drawer.addView(menuItem("⚙","Settings",false){closeMenu(overlay);showSettings()})
        drawer.addView(Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,0,1f)})
        drawer.addView(panel().apply{setPadding(dp(12),dp(12),dp(12),dp(12));addView(text("RAZEEN SECURE SOLUTION",13,textColor(),true));addView(text("Mobile & PC Software • CCTV • Networking",9,muted(),false).apply{setPadding(0,dp(3),0,0)});addView(text("www.rsscctvsolution.eu.cc",10,accent(),false).apply{setPadding(0,dp(5),0,0)})})
        overlay.addView(drawer,FrameLayout.LayoutParams(dp(320),-1).apply{gravity=Gravity.START});root.addView(overlay,FrameLayout.LayoutParams(-1,-1))
    }
    private fun menuItem(icon:String,title:String,selected:Boolean,onClick:()->Unit):View=LinearLayout(this).apply{
        gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(9),dp(10),dp(9));background=rounded(if(selected)Color.argb(26,Color.red(accent()),Color.green(accent()),Color.blue(accent())) else Color.TRANSPARENT,18);setOnClickListener{onClick()}
        addView(TextView(this@MainActivity).apply{text=icon;textSize=19f;gravity=Gravity.CENTER;setTextColor(accent());background=rounded(surface2(),13)},LinearLayout.LayoutParams(dp(38),dp(38)))
        addView(text(title,14,textColor(),selected),LinearLayout.LayoutParams(0,dp(44),1f).apply{setMargins(dp(12),0,0,0)})
        if(selected)addView(View(this@MainActivity).apply{setBackgroundColor(accent())},LinearLayout.LayoutParams(dp(6),dp(6)))
    }
    private fun closeMenu(overlay:View){drawerOpen=false;root.removeView(overlay)}

    private fun showPremium() {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        box.addView(secondaryButton("← Back") { showHome() }, LinearLayout.LayoutParams(-1, dp(46)))
        box.addView(panel().apply {
            setPadding(dp(20), dp(22), dp(20), dp(22))
            addView(text("RSS PREMIUM", 12, accent(), true))
            addView(text("Unlock Premium for 1 year", 25, textColor(), true).apply { setPadding(0, dp(8), 0, 0) })
            addView(text("Premium is attached to your RSS Core account and remains available across supported RSS apps and devices.", 13, muted(), false).apply { setPadding(0, dp(8), 0, 0) })
            val price = text("Loading price…", 18, accent(), true).apply { setPadding(0, dp(16), 0, 0) }
            addView(price)
            val action = primaryButton(if (monetization.isPremium()) "Premium Active" else "Upgrade to Premium") {}
            addView(action, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })
            if (monetization.isPremium()) {
                action.isEnabled = false
                addView(text("Your RSS Core Premium entitlement is active.", 12, muted(), false).apply { setPadding(0, dp(10), 0, 0) })
            } else {
                action.setOnClickListener {
                    val email = getSharedPreferences("rss-downloader-license", MODE_PRIVATE).getString("email", "").orEmpty()
                    action.isEnabled = false
                    action.text = "Creating secure checkout…"
                    api.createPremiumCheckout(email, "https://rsscore.cv/payment/success", "https://rsscore.cv/payment/cancel") { result ->
                        runOnUiThread {
                            result.onSuccess { checkout ->
                                action.text = "Continue in browser"
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(checkout.checkoutUrl)))
                                toast("Payment is pending until RSS Core receives provider confirmation.")
                            }.onFailure {
                                action.isEnabled = true
                                action.text = "Upgrade to Premium"
                                toast(it.message ?: "Unable to create payment checkout.")
                            }
                        }
                    }
                }
            }
            api.premiumPlan { result ->
                runOnUiThread {
                    result.onSuccess { root ->
                        val plan = root.optJSONArray("plans")?.optJSONObject(0)
                        val amount = plan?.optInt("price_lkr", 0) ?: 0
                        price.text = if (amount > 0) "LKR $amount • 365 days" else "Price is being configured"
                    }.onFailure { price.text = "Price unavailable" }
                }
            }
        })
        box.addView(panel().apply {
            setPadding(dp(20), dp(18), dp(20), dp(18))
            addView(text("PAYMENT STATUS", 11, muted(), true))
            addView(text("After payment, return to RSS Downloader. The app will refresh the entitlement from RSS Core; the browser return page itself never grants Premium.", 12, muted(), false).apply { setPadding(0, dp(8), 0, 0) })
            addView(secondaryButton("Refresh Premium Status") { syncPremiumEntitlement(); toast("Refreshing RSS Core entitlement…") }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        scroll.addView(box)
        content.addView(scroll)
    }

    private fun showSettings() {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(28)) }
        box.addView(panel().apply { gravity = Gravity.CENTER_HORIZONTAL; addView(logoView(64), LinearLayout.LayoutParams(dp(64), dp(64))); addView(text("RSS DOWNLOADER", 20, textColor(), true)); addView(text("SETTINGS", 11, muted(), true).apply { setPadding(0, dp(3), 0, 0) }) })
        box.addView(settingsSection("GENERAL"))
        box.addView(settingActionCard("◐", "Appearance", appearanceLabel(), appearanceMode) { chooseAppearance() })
        box.addView(settingCard("↗", "Auto-paste copied URL", "Detect copied HTTP(S) URLs", prefs.getBoolean("clipboard", true)) { prefs.edit().putBoolean("clipboard", it).apply() })
        box.addView(settingCard("⌕", "Auto-analyze pasted URL", "Analyze a newly detected URL automatically", prefs.getBoolean("autoAnalyzeClipboard", true)) { prefs.edit().putBoolean("autoAnalyzeClipboard", it).apply() })
        box.addView(settingCard("✓", "Confirm before analyzing", "Ask before automatic clipboard analysis", prefs.getBoolean("confirmClipboardAnalyze", false)) { prefs.edit().putBoolean("confirmClipboardAnalyze", it).apply() })
        box.addView(settingsSection("SECURITY"))
        box.addView(settingCard("🔒", "App Lock", "Require authentication after leaving the app", prefs.getBoolean("appLock", false)) { v -> prefs.edit().putBoolean("appLock", v).apply(); authenticatedThisSession = !v; if (v) authenticateWithBiometric() })
        box.addView(settingCard("◉", "Biometric Unlock", "Fingerprint / face / supported biometric", prefs.getBoolean("biometric", true)) { v -> prefs.edit().putBoolean("biometric", v).apply(); if (v && prefs.getBoolean("appLock", false)) authenticateWithBiometric() })
        box.addView(settingActionCard("◷", "Auto-lock timing", lockTimingLabel(), prefs.getString("lockTimeout", "immediate") ?: "immediate") { chooseLockTiming() })
        box.addView(settingCard("◌", "Lock on background", "Lock when RSS Downloader leaves the foreground", prefs.getBoolean("lockOnBackground", true)) { prefs.edit().putBoolean("lockOnBackground", it).apply() })
        box.addView(settingsSection("DOWNLOADS"))
        val location = prefs.getString("saveLocationUri", null)
        box.addView(panel().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(text("↓", 20, accent(), true), LinearLayout.LayoutParams(dp(42), dp(42))); addView(text(if (location.isNullOrBlank()) "Default Save Location\nNot selected" else "Default Save Location\nCustom folder selected", 13, textColor(), true), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) }); addView(secondaryButton("Choose") { chooseSaveLocation() }) })
        box.addView(settingCard("↗", "Ask where to save", "Choose destination for each download", prefs.getBoolean("askSaveLocation", false)) { prefs.edit().putBoolean("askSaveLocation", it).apply() })
        box.addView(settingCard("Wi", "Wi-Fi only", "Restrict downloads to Wi-Fi", prefs.getBoolean("wifiOnly", false)) { prefs.edit().putBoolean("wifiOnly", it).apply() })
        box.addView(settingActionCard("HD", "Preferred video quality", qualityLabel("videoQuality", "Best available"), prefs.getString("videoQuality", "Best available") ?: "Best available") { chooseQuality("videoQuality", arrayOf("Best available", "1080p", "720p", "480p")) })
        box.addView(settingActionCard("AU", "Preferred audio quality", qualityLabel("audioQuality", "Best available"), prefs.getString("audioQuality", "Best available") ?: "Best available") { chooseQuality("audioQuality", arrayOf("Best available", "320 kbps", "256 kbps", "128 kbps")) })
        box.addView(settingCard("▶", "Prefer MP4", "Prefer MP4 when an authorized format is available", prefs.getBoolean("preferMp4", true)) { prefs.edit().putBoolean("preferMp4", it).apply() })
        box.addView(settingCard("CC", "Subtitles", "Include subtitles when supported", prefs.getBoolean("subtitles", false)) { prefs.edit().putBoolean("subtitles", it).apply() })
        box.addView(settingsSection("NOTIFICATIONS"))
        box.addView(settingCard("◔", "Download Progress", "Only during an active download", prefs.getBoolean("notifyProgress", true)) { prefs.edit().putBoolean("notifyProgress", it).apply() })
        box.addView(settingCard("✓", "Download Completed", "Only after a real download completes", prefs.getBoolean("notifyCompleted", true)) { prefs.edit().putBoolean("notifyCompleted", it).apply() })
        box.addView(settingCard("!", "Download Failed", "Only after an actual download failure", prefs.getBoolean("notifyFailed", true)) { prefs.edit().putBoolean("notifyFailed", it).apply() })
        box.addView(settingCard("•", "Background Activity", "Only during genuine background work", prefs.getBoolean("notifyBackground", true)) { prefs.edit().putBoolean("notifyBackground", it).apply() })
        box.addView(text("No activity = no notification. No fake starting-download notification.", 10, muted(), false).apply { setPadding(dp(4), dp(8), dp(4), 0) })
        box.addView(settingsSection("SYNC & DATA"))
        box.addView(settingCard("⟳", "Auto-sync download history", "Refresh download state when the app returns", prefs.getBoolean("autoSync", true)) { prefs.edit().putBoolean("autoSync", it).apply() })
        box.addView(settingActionCard("⌫", "Clear cached images", "Remove locally cached poster/image data", "Clear") { toast("Image cache clearing will be connected to the downloader cache.") })
        box.addView(settingActionCard("≡", "Clear download history", "Remove local history only", "Clear") { confirmAction("Clear download history?", "This removes local history. Active downloads are not cancelled.") { prefs.edit().remove("downloadHistory").apply(); toast("Download history cleared.") } })
        box.addView(settingsSection("NETWORK"))
        box.addView(settingActionCard("↻", "Request timeout", prefs.getInt("timeoutSeconds", 30).toString() + " seconds", prefs.getInt("timeoutSeconds", 30).toString()) { chooseTimeout() })
        box.addView(settingActionCard("↺", "Retry attempts", prefs.getInt("retryAttempts", 2).toString() + " retries", prefs.getInt("retryAttempts", 2).toString()) { chooseRetries() })
        box.addView(panel().apply { addView(text("RSS Core Host", 14, textColor(), true)); addView(text(BuildConfig.RSS_HOST_BASE_URL, 12, muted(), false).apply { setPadding(0, dp(4), 0, 0) }); addView(text("Build-controlled host. Not user-editable.", 10, muted(), false).apply { setPadding(0, dp(3), 0, 0) }) })
        box.addView(settingsSection("GENERAL BEHAVIOR"))
        box.addView(settingCard("☀", "Keep screen awake", "Prevent screen timeout while viewing active download details", prefs.getBoolean("keepScreenAwake", false)) { prefs.edit().putBoolean("keepScreenAwake", it).apply() })
        box.addView(settingCard("⚠", "Confirm downloads", "Ask before starting an authorized download", prefs.getBoolean("confirmDownload", false)) { prefs.edit().putBoolean("confirmDownload", it).apply() })
        box.addView(settingsSection("ABOUT"))
        box.addView(panel().apply { addView(text("RSS Downloader", 18, textColor(), true)); addView(text("Native Android downloader • " + BuildConfig.VERSION_NAME, 11, muted(), false).apply { setPadding(0, dp(4), 0, 0) }); addView(text("Package: " + BuildConfig.APPLICATION_ID, 10, muted(), false).apply { setPadding(0, dp(3), 0, 0) }) })
        box.addView(settingsSection("RAZEEN SECURE SOLUTION"))
        box.addView(panel().apply { gravity = Gravity.CENTER_HORIZONTAL; addView(logoView(58), LinearLayout.LayoutParams(dp(58), dp(58))); addView(text("RAZEEN SECURE SOLUTION", 15, textColor(), true)); addView(text("Mobile & PC Software • CCTV • Networking", 10, muted(), false)); addView(text("077 115 5504  •  070 155 5504", 11, accent(), true)); addView(text("rsscctvsolution@gmail.com", 11, accent(), false)); addView(text("www.rsscctvsolution.eu.cc", 11, accent(), false)) })
        scroll.addView(box); content.addView(scroll)
    }

    private fun appearanceLabel(): String = when (appearanceMode) { "light" -> "Light"; "system" -> "System default"; else -> "Dark" }
    private fun lockTimingLabel(): String = when (prefs.getString("lockTimeout", "immediate")) { "5m" -> "After 5 minutes"; "15m" -> "After 15 minutes"; "30m" -> "After 30 minutes"; "never" -> "Only when manually locked"; else -> "Immediately on background" }
    private fun qualityLabel(key: String, fallback: String): String = prefs.getString(key, fallback) ?: fallback
    private fun settingActionCard(icon: String, title: String, subtitle: String, value: String, onClick: () -> Unit): View = panel().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(text(icon, 18, accent(), true), LinearLayout.LayoutParams(dp(42), dp(42))); addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 14, textColor(), true)); addView(text(subtitle, 10, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) }); addView(secondaryButton(value, onClick)) }
    private fun chooseAppearance() { val values = arrayOf("Light", "Dark", "System default"); val keys = arrayOf("light", "dark", "system"); val selected = keys.indexOf(appearanceMode).coerceAtLeast(0); AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(values, selected) { dialog, which -> appearanceMode = keys[which]; prefs.edit().putString("appearance", appearanceMode).putBoolean("light", appearanceMode == "light").apply(); lightMode = appearanceMode == "light" || (appearanceMode == "system" && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_NO); dialog.dismiss(); recreate() }.setNegativeButton("Cancel", null).show() }
    private fun chooseLockTiming() { val values = arrayOf("Immediately on background", "After 5 minutes", "After 15 minutes", "After 30 minutes", "Only when manually locked"); val keys = arrayOf("immediate", "5m", "15m", "30m", "never"); val selected = keys.indexOf(prefs.getString("lockTimeout", "immediate")).coerceAtLeast(0); AlertDialog.Builder(this).setTitle("Auto-lock timing").setSingleChoiceItems(values, selected) { dialog, which -> prefs.edit().putString("lockTimeout", keys[which]).apply(); dialog.dismiss(); showSettings() }.setNegativeButton("Cancel", null).show() }
    private fun chooseQuality(key: String, values: Array<String>) { val current = prefs.getString(key, values[0]) ?: values[0]; val selected = values.indexOf(current).coerceAtLeast(0); AlertDialog.Builder(this).setTitle(if (key == "videoQuality") "Video quality" else "Audio quality").setSingleChoiceItems(values, selected) { dialog, which -> prefs.edit().putString(key, values[which]).apply(); dialog.dismiss(); showSettings() }.setNegativeButton("Cancel", null).show() }
    private fun chooseTimeout() { val values = arrayOf("15 seconds", "30 seconds", "60 seconds", "120 seconds"); val numbers = intArrayOf(15, 30, 60, 120); val selected = numbers.indexOf(prefs.getInt("timeoutSeconds", 30)).coerceAtLeast(0); AlertDialog.Builder(this).setTitle("Request timeout").setSingleChoiceItems(values, selected) { dialog, which -> prefs.edit().putInt("timeoutSeconds", numbers[which]).apply(); dialog.dismiss(); showSettings() }.setNegativeButton("Cancel", null).show() }
    private fun chooseRetries() { val values = arrayOf("0 retries", "1 retry", "2 retries", "3 retries", "5 retries"); val numbers = intArrayOf(0, 1, 2, 3, 5); val selected = numbers.indexOf(prefs.getInt("retryAttempts", 2)).coerceAtLeast(0); AlertDialog.Builder(this).setTitle("Retry attempts").setSingleChoiceItems(values, selected) { dialog, which -> prefs.edit().putInt("retryAttempts", numbers[which]).apply(); dialog.dismiss(); showSettings() }.setNegativeButton("Cancel", null).show() }

    private fun confirmAction(title: String, message: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> action() }.show() }
    private fun settingsSection(title: String): View = text(title, 11, muted(), true).apply { setPadding(dp(4), dp(20), dp(4), dp(6)) }

    private fun settingCard(icon: String, title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit): View = panel().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(text(icon, 18, accent(), true), LinearLayout.LayoutParams(dp(42), dp(42))); addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 14, textColor(), true)); addView(text(subtitle, 10, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(6), 0) }); addView(Switch(this@MainActivity).apply { isChecked = checked; setOnCheckedChangeListener { _, v -> onChanged(v) } }) }

    private fun chooseSaveLocation() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), saveLocationRequestCode) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == saveLocationRequestCode && resultCode == RESULT_OK) data?.data?.let { uri -> runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; prefs.edit().putString("saveLocationUri", uri.toString()).apply(); showSettings() } }

    private fun biometricAvailable(): Boolean = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticateWithBiometric() { if (!prefs.getBoolean("appLock", false) || !prefs.getBoolean("biometric", true) || biometricPromptActive) return; if (!biometricAvailable()) { toast("Biometric unlock is not available."); return }; biometricPromptActive = true; val prompt = BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { biometricPromptActive = false; authenticatedThisSession = true }; override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { biometricPromptActive = false } }); prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock RSS Downloader").setSubtitle("Authenticate to continue").setNegativeButtonText("Cancel").build()) }

    private fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun readClipboardUrl(autoAnalyze: Boolean) {
        if (!prefs.getBoolean("clipboard", true) || !::urlInput.isInitialized) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        if (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return
        val value = clip.getItemAt(0).coerceToText(this).toString().trim()
        if (!value.startsWith("http://") && !value.startsWith("https://")) return
        if (currentTab != TabOrder.SOCIAL) { currentTab = TabOrder.SOCIAL; showHome() }
        if (urlInput.text.toString() != value) urlInput.setText(value)
        if (autoAnalyze && prefs.getString("lastClipboardUrl", "") != value) {
            prefs.edit().putString("lastClipboardUrl", value).apply()
            urlInput.postDelayed({ if (!isFinishing && currentTab == TabOrder.SOCIAL) analyzeUrl() }, 180)
        }
    }

    private fun renderThumbnail(container: LinearLayout, url: String?, title: String) {
        if (url.isNullOrBlank()) return
        val image = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = title }
        container.addView(image, 0, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(10) })
        loadImage(url, image)
    }

    private fun loadImage(url: String?, target: ImageView) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return
        imageExecutor.execute {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bitmap -> runOnUiThread { if (!isFinishing) target.setImageBitmap(bitmap) } }
        }
    }

    private fun loadWikipediaPoster(title: String, target: ImageView) {
        imageExecutor.execute {
            runCatching {
                val encoded = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                val connection = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded").openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                json.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
            }.getOrNull()?.let { imageUrl -> loadImage(imageUrl, target) }
        }
    }

    private fun tabLabel(id: String) = when (id) { TabOrder.SOCIAL -> "Social Downloader"; TabOrder.TAMIL -> "Tamil Movies"; else -> "Tamil Dubbed Movies" }
    private fun tabShortLabel(id: String) = when (id) { TabOrder.SOCIAL -> "Social"; TabOrder.TAMIL -> "Tamil"; else -> "Dubbed" }
    private fun applyTheme() { window.statusBarColor = bg(); window.navigationBarColor = bg() }
    private fun bg() = Color.parseColor(if (lightMode) "#F4F6FB" else "#070B16")
    private fun surface() = Color.parseColor(if (lightMode) "#FFFFFF" else "#0D1324")
    private fun surface2() = Color.parseColor(if (lightMode) "#F5F1E5" else "#171717")
    private fun textColor() = Color.parseColor(if (lightMode) "#152039" else "#EEF2FF")
    private fun muted() = Color.parseColor(if (lightMode) "#667085" else "#8E9AB4")
    private fun accent() = Color.parseColor(if (lightMode) "#9A7416" else "#D4AF37")
    private fun panel() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(surface(), 22); setPadding(dp(18), dp(18), dp(18), dp(18)); elevation = dp(3).toFloat(); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) } }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT }
    private fun iconButton(iconRes: Int, size: Int, description: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(iconRes)
        imageTintList = android.content.res.ColorStateList.valueOf(textColor())
        background = rounded(surface2(), 14)
        contentDescription = description
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(2), 0, dp(2), 0) }
    }

    private fun button(label: String, size: Int, onClick: () -> Unit) = TextView(this).apply { text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(textColor()); background = rounded(surface2(), 14); setOnClickListener { onClick() }; layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(0, 0, dp(8), 0) } }
    private fun primaryButton(label: String, onClick: () -> Unit) = TextView(this).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.BLACK); typeface = android.graphics.Typeface.DEFAULT_BOLD; background = rounded(accent(), 14); setPadding(dp(14), 0, dp(14), 0); setOnClickListener { onClick() }; minimumWidth = dp(92) }
    private fun secondaryButton(label: String, onClick: () -> Unit) = TextView(this).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(textColor()); typeface = android.graphics.Typeface.DEFAULT_BOLD; background = rounded(surface2(), 12); setPadding(dp(14), 0, dp(14), 0); setOnClickListener { onClick() }; minimumWidth = dp(92) }
    private fun logoView(size: Int) = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_INSIDE; layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
    private fun progress(percent: Int) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent.coerceIn(0, 100); progressTintList = android.content.res.ColorStateList.valueOf(accent()); layoutParams = LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) } }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatBytes(value: Long): String { var n = value.toDouble(); val units = arrayOf("B", "KB", "MB", "GB"); var i = 0; while (n >= 1024 && i < units.size - 1) { n /= 1024; i++ }; return if (i == 0) "${n.toLong()} B" else String.format(Locale.US, "%.1f %s", n, units[i]) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun startDownloadKeepAlive() { ContextCompat.startForegroundService(this, DownloadKeepAliveService.startIntent(this)) }
}

private object ColorDrawableCompat {
    fun transparent(): GradientDrawable = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
}