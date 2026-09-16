package com.riyaz.rssdownloader

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("rss-downloader", MODE_PRIVATE) }
    private val api by lazy { NativeHostApi(prefs.getString("hostUrl", BuildConfig.RSS_HOST_BASE_URL).orEmpty(), prefs.getString("hostToken", BuildConfig.RSS_HOST_ACCESS_TOKEN).orEmpty().ifBlank { null }) }
    private val imageExecutor = Executors.newFixedThreadPool(4)
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var urlInput: EditText
    private lateinit var stateText: TextView
    private lateinit var mediaPanel: LinearLayout
    private var currentTab = TabOrder.SOCIAL
    private var lightMode = false
    private var tabOrder = TabOrder.defaults.toMutableList()
    private val movieTitles = mapOf(
        TabOrder.TAMIL to listOf("Maharaja" to 2024, "Amaran" to 2024, "Lubber Pandhu" to 2024, "Good Night" to 2023, "Parking" to 2023, "Tourist Family" to 2025, "Dragon" to 2025, "Retro" to 2025),
        TabOrder.DUBBED to listOf("Kalki 2898 AD" to 2024, "Pushpa 2: The Rule" to 2024, "Hanuman" to 2024, "Leo" to 2023, "Salaar" to 2023, "Baahubali 2" to 2017, "RRR" to 2022, "Jailer" to 2023)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lightMode = prefs.getBoolean("light", false)
        tabOrder = TabOrder.load(prefs.getString("tabOrder", null))
        applyTheme()
        buildApp()
        readClipboardUrl(true)
    }

    override fun onResume() {
        super.onResume()
        if (::urlInput.isInitialized) readClipboardUrl(true)
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
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(12), dp(10)); layoutParams = LinearLayout.LayoutParams(-1, dp(74))
        addView(logoView(44), LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(12), 0, dp(12), 0) })
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text("RSS Downloader", 17, textColor(), true)); addView(text("Fast. Organized. Controlled.", 11, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(button("⚙", 46) { showSettings() })
    }

    private fun buildBottomNav(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER; setPadding(dp(7), dp(6), dp(7), dp(6)); background = rounded(surface(), 22); elevation = dp(12).toFloat()
        tabOrder.forEach { id ->
            val selected = id == currentTab
            val item = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), dp(8), dp(3), dp(7)); background = rounded(if (selected) surface2() else Color.TRANSPARENT, 16)
                alpha = if (selected) 1f else 0.78f; alpha = if (selected) 1f else 0.78f }
            item.addView(text(if (id == TabOrder.SOCIAL) "⇩" else if (id == TabOrder.TAMIL) "🎬" else "▶", 19, textColor(), false))
            item.addView(text(tabLabel(id), 9, if (selected) textColor() else muted(), true))
            item.setOnClickListener {
                if (currentTab == id) return@setOnClickListener
                currentTab = id
                item.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
                    item.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }.start()
                showHome()
            }
            addView(item, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(text("⚙", 19, muted(), false)); addView(text("Settings", 9, muted(), true)); setOnClickListener { showSettings() } }, LinearLayout.LayoutParams(0, dp(58), 1f))
        layoutParams = LinearLayout.LayoutParams(-1, dp(78)).apply { setMargins(dp(10), 0, dp(10), 0) }
    }

    private fun showHome() {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(18)) }
        if (currentTab == TabOrder.SOCIAL) buildSocial(box) else buildMovie(box, currentTab)
        scroll.addView(box); content.addView(scroll)
    }

    private fun buildSocial(box: LinearLayout) {
        val hero = panel().apply { setPadding(dp(24), dp(24), dp(24), dp(20)) }
        hero.addView(text("SOCIAL DOWNLOADER", 10, Color.rgb(140, 156, 255), true))
        hero.addView(text("Paste a link.\nRSS handles the rest.", 34, textColor(), true).apply { setPadding(0, dp(4), 0, dp(8)) })
        hero.addView(text("Copied HTTP(S) links are detected automatically while RSS Downloader is active.", 13, muted(), false))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(20), 0, 0) }
        urlInput = EditText(this).apply { hint = "Paste URL here…"; setHintTextColor(muted()); setTextColor(textColor()); setSingleLine(true); setPadding(dp(16), 0, dp(16), 0); background = rounded(bg(), 14) }
        row.addView(urlInput, LinearLayout.LayoutParams(0, dp(54), 1f)); row.addView(primaryButton("Analyze") { analyzeUrl() }, LinearLayout.LayoutParams(dp(120), dp(54)).apply { setMargins(dp(10), 0, 0, 0) }); hero.addView(row)
        stateText = text("", 13, muted(), false).apply { visibility = View.GONE; setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(surface(), 12) }
        hero.addView(stateText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }); box.addView(hero)
        mediaPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(18), dp(10), dp(18), 0) }; box.addView(mediaPanel)
    }

    private fun buildMovie(box: LinearLayout, tab: String) {
        box.addView(text(tabLabel(tab).uppercase(Locale.US), 10, Color.rgb(140, 156, 255), true).apply { setPadding(dp(18), dp(12), 0, dp(5)) })
        box.addView(text("Tamil movie search", 28, textColor(), true).apply { setPadding(dp(18), 0, 0, dp(12)) })
        val search = EditText(this).apply { hint = "Search movies…"; setHintTextColor(muted()); setTextColor(textColor()); setSingleLine(true); background = rounded(bg(), 14); setPadding(dp(16), 0, dp(16), 0) }
        val searchRow = LinearLayout(this).apply { setPadding(dp(18), 0, dp(18), dp(12)) }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f)); searchRow.addView(primaryButton("Search") { movieSearch(tab, search.text.toString()) }, LinearLayout.LayoutParams(dp(110), dp(52)).apply { setMargins(dp(10), 0, 0, 0) }); box.addView(searchRow)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        movieTitles[tab].orEmpty().chunked(2).forEach { pair -> val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; pair.forEach { (title, year) -> row.addView(movieCard(title, year, tab), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }) }; if (pair.size == 1) row.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f)); grid.addView(row) }
        box.addView(grid)
    }

    private fun movieCard(title: String, year: Int, tab: String): View = panel().apply {
        orientation = LinearLayout.VERTICAL
        val image = ImageView(this@MainActivity).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = title }
        addView(image, LinearLayout.LayoutParams(-1, dp(145))); loadWikipediaPoster(title, image)
        addView(text(title, 15, textColor(), true).apply { setPadding(dp(13), dp(7), dp(13), 0) }); addView(text("$year • Authorized provider options", 11, muted(), false).apply { setPadding(dp(13), 0, dp(13), dp(7)) })
        addView(secondaryButton("Get options") { movieSearch(tab, title) }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(13), 0, dp(13), dp(13)) })
    }

    private fun analyzeUrl() {
        val url = urlInput.text.toString().trim(); if (url.isBlank()) { toast("Paste a URL first."); return }
        stateText.visibility = View.VISIBLE; mediaPanel.visibility = View.VISIBLE; mediaPanel.removeAllViews(); stateText.text = if (api.configured()) "Analyzing URL…" else "RSS Core is not configured."; if (!api.configured()) return
        api.analyze(url) { result -> runOnUiThread { result.onSuccess { analysis -> stateText.text = analysis.title.ifBlank { analysis.normalizedUrl }; renderThumbnail(mediaPanel, analysis.thumbnailUrl, analysis.title); renderOptions(mediaPanel, analysis.requestId, analysis.mediaOptions) }.onFailure { stateText.text = it.message ?: "Analysis failed." } } }
    }

    private fun movieSearch(tab: String, query: String) {
        val q = query.trim(); if (q.isBlank()) { toast("Enter a movie name."); return }; if (!api.configured()) { toast("RSS Core is not configured."); return }
        content.removeAllViews(); val loading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(22)) }; loading.addView(text("Searching…", 18, textColor(), true)); content.addView(ScrollView(this).apply { addView(loading) })
        api.search(tab, q) { result -> runOnUiThread {
            result.onSuccess { results ->
                val panel = panel().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }; panel.addView(text("Search results", 18, textColor(), true)); val optionsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }; panel.addView(optionsPanel)
                if (results.isEmpty()) panel.addView(text("No results returned by RSS Core.", 13, muted(), false).apply { setPadding(0, dp(12), 0, dp(8)) })
                results.forEach { item ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }; val image = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = item.title }; row.addView(image, LinearLayout.LayoutParams(dp(82), dp(98))); loadImage(item.thumbnailUrl, image); if (item.thumbnailUrl.isNullOrBlank()) loadWikipediaPoster(item.title, image)
                    row.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(item.title, 14, textColor(), true)); addView(text(item.year?.toString() ?: "", 11, muted(), false)); if (item.qualities.isNotEmpty()) addView(text(item.qualities.joinToString(" • "), 10, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
                    row.addView(primaryButton("Select") { val requestId = item.requestId; if (item.mediaOptions.isNotEmpty() && requestId != null) renderOptions(optionsPanel, requestId, item.mediaOptions) else if (requestId != null) { optionsPanel.removeAllViews(); optionsPanel.addView(text("Loading quality options…", 13, muted(), false)); api.listMediaOptions(requestId) { optionsResult -> runOnUiThread { optionsResult.onSuccess { options -> renderOptions(optionsPanel, requestId, options) }.onFailure { optionsPanel.removeAllViews(); optionsPanel.addView(text(it.message ?: "Failed to load quality options.", 13, muted(), false)) } } } } else { optionsPanel.removeAllViews(); optionsPanel.addView(text("RSS Core did not return a request ID.", 13, muted(), false)) } }); panel.addView(row)
                }
                content.removeAllViews(); content.addView(ScrollView(this).apply { addView(panel) })
            }.onFailure { toast(it.message ?: "Search failed.") }
        }}
    }

    private fun renderOptions(target: LinearLayout, requestId: String, options: List<NativeHostApi.MediaOption>) {
        target.visibility = View.VISIBLE; target.removeAllViews(); if (options.isEmpty()) { target.addView(text("RSS Core returned no authorized formats.", 13, muted(), false)); return }; target.addView(text("Choose an authorized format", 13, muted(), false))
        options.forEach { option -> val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(13), 0, dp(13)) }; row.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(option.kind.uppercase(Locale.US), 13, textColor(), true)); addView(text(listOfNotNull(option.format.ifBlank { null }, option.quality, option.sizeBytes?.let(::formatBytes)).joinToString(" • "), 11, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(primaryButton("Download") { createDownload(requestId, option.id) }); target.addView(row) }
    }

    private fun createDownload(requestId: String, optionId: String) {
        if (!api.configured()) { toast("RSS Core is not configured."); return }
        api.createDownload(requestId, optionId) { result -> runOnUiThread { result.onSuccess { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102) else startDownloadKeepAlive(); showDownloads() }.onFailure { toast(it.message ?: "Download failed.") } } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == 4102 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startDownloadKeepAlive() }

    private fun showDownloads() {
        content.removeAllViews(); val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }; box.addView(text("DOWNLOADS", 10, Color.rgb(140, 156, 255), true)); box.addView(text("Downloads", 30, textColor(), true)); box.addView(secondaryButton("Refresh") { showDownloads() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) }); val state = text(if (api.configured()) "Loading…" else "RSS Core is not configured.", 13, muted(), false); box.addView(state); scroll.addView(box); content.addView(scroll)
        if (api.configured()) api.listDownloads { result -> runOnUiThread { result.onSuccess { jobs -> state.text = if (jobs.isEmpty()) "No downloads yet." else "${jobs.size} download(s)"; jobs.forEach { box.addView(jobCard(it)) } }.onFailure { state.text = it.message ?: "Failed to load downloads." } } }
    }

    private fun jobCard(job: NativeHostApi.Job): View = panel().apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12)); val image = ImageView(this@MainActivity).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP }; addView(image, LinearLayout.LayoutParams(dp(56), dp(56))); loadImage(job.thumbnailUrl, image)
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(job.title ?: "RSS Download", 14, textColor(), true)); addView(text("${job.status} • ${job.progress ?: 0}%", 11, muted(), false)); addView(progress(job.progress ?: 0)); if (!job.error.isNullOrBlank()) addView(text(job.error, 11, Color.rgb(220, 90, 90), false)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
    }

    private fun showSettings() {
        content.removeAllViews(); val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }; box.addView(text("RSS DOWNLOADER", 10, Color.rgb(140, 156, 255), true)); box.addView(text("Settings", 30, textColor(), true)); box.addView(settingRow("Appearance", "Use light or dark interface", lightMode) { lightMode = it; prefs.edit().putBoolean("light", it).apply(); recreate() }); box.addView(settingRow("Auto-paste copied URL", "Detect a copied HTTP(S) URL when RSS Downloader is active", prefs.getBoolean("clipboard", true)) { prefs.edit().putBoolean("clipboard", it).apply() }); box.addView(text("RSS CORE HOST", 10, muted(), true).apply { setPadding(dp(18), dp(18), 0, dp(6)) })
        val host = EditText(this).apply { setText(prefs.getString("hostUrl", BuildConfig.RSS_HOST_BASE_URL)); setTextColor(textColor()); setHintTextColor(muted()); setSingleLine(true); setPadding(dp(16), 0, dp(16), 0); background = rounded(bg(), 14) }; box.addView(host, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(18), 0, dp(18), dp(8)) }); box.addView(secondaryButton("Save RSS Core host") { val value = host.text.toString().trim().trimEnd('/'); if (!value.startsWith("https://")) toast("Use an HTTPS RSS Core URL.") else { prefs.edit().putString("hostUrl", value).apply(); recreate() } }, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(18), 0, dp(18), dp(10)) })
        box.addView(text("TAB ORDER", 10, muted(), true).apply { setPadding(dp(18), dp(18), 0, dp(6)) }); tabOrder.forEachIndexed { index, id -> val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(8), dp(8)); background = rounded(surface(), 14) }; row.addView(text(if (id == TabOrder.SOCIAL) "⇩" else if (id == TabOrder.TAMIL) "🎬" else "▶", 20, textColor(), false), LinearLayout.LayoutParams(dp(36), dp(48))); row.addView(text(tabLabel(id), 14, textColor(), true), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(secondaryButton("↑") { moveTab(index, -1) }, LinearLayout.LayoutParams(dp(48), dp(44)).apply { setMargins(dp(4), 0, dp(4), 0) }); row.addView(secondaryButton("↓") { moveTab(index, 1) }, LinearLayout.LayoutParams(dp(48), dp(44))); box.addView(row, LinearLayout.LayoutParams(-1, dp(64)).apply { setMargins(dp(18), dp(4), dp(18), dp(4)) }) }
        box.addView(secondaryButton("Reset tab order") { tabOrder = TabOrder.defaults.toMutableList(); prefs.edit().remove("tabOrder").apply(); currentTab = TabOrder.SOCIAL; showSettings() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }); scroll.addView(box); content.addView(scroll)
    }

    private fun moveTab(index: Int, delta: Int) { val target = index + delta; if (target !in tabOrder.indices) return; val moved = tabOrder.removeAt(index); tabOrder.add(target, moved); prefs.edit().putString("tabOrder", TabOrder.save(tabOrder)).apply(); currentTab = moved; showSettings() }
    private fun settingRow(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit): View = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(16), dp(10), dp(16)); addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 15, textColor(), true)); addView(text(subtitle, 11, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f)); addView(Switch(this@MainActivity).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> onChanged(value) } }) }

    private fun readClipboardUrl(autoAnalyze: Boolean) {
        if (!prefs.getBoolean("clipboard", true) || !::urlInput.isInitialized) return; val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip = cm.primaryClip ?: return; if (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return; val value = clip.getItemAt(0).coerceToText(this).toString().trim(); if (!value.startsWith("http://") && !value.startsWith("https://")) return
        if (currentTab != TabOrder.SOCIAL) { currentTab = TabOrder.SOCIAL; showHome() }; if (urlInput.text.toString() != value) urlInput.setText(value); if (autoAnalyze && prefs.getString("lastClipboardUrl", "") != value) { prefs.edit().putString("lastClipboardUrl", value).apply(); urlInput.postDelayed({ if (!isFinishing && currentTab == TabOrder.SOCIAL) analyzeUrl() }, 180) }
    }

    private fun renderThumbnail(container: LinearLayout, url: String?, title: String) { if (url.isNullOrBlank()) return; val image = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = title }; container.addView(image, 0, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(10) }); loadImage(url, image) }

    private fun loadImage(url: String?, target: ImageView) { if (url.isNullOrBlank() || !url.startsWith("http")) return; imageExecutor.execute { runCatching { val connection = URL(url).openConnection() as HttpURLConnection; connection.connectTimeout = 10000; connection.readTimeout = 15000; connection.instanceFollowRedirects = true; connection.inputStream.use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { bitmap -> runOnUiThread { if (!isFinishing) target.setImageBitmap(bitmap) } } } }
    private fun loadWikipediaPoster(title: String, target: ImageView) { imageExecutor.execute { runCatching { val encoded = URLEncoder.encode(title, "UTF-8").replace("+", "%20"); val connection = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded").openConnection() as HttpURLConnection; connection.connectTimeout = 8000; connection.readTimeout = 12000; val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }; json.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null } }.getOrNull()?.let { imageUrl -> loadImage(imageUrl, target) } } }

    private fun tabLabel(id: String) = when (id) { TabOrder.SOCIAL -> "Social Downloader"; TabOrder.TAMIL -> "Tamil Movies"; else -> "Tamil Dubbed Movies" }
    private fun applyTheme() { window.statusBarColor = bg(); window.navigationBarColor = bg() }
    private fun bg() = Color.parseColor(if (lightMode) "#F4F6FB" else "#070B16")
    private fun surface() = Color.parseColor(if (lightMode) "#FFFFFF" else "#0D1324")
    private fun surface2() = Color.parseColor(if (lightMode) "#EDF1F8" else "#121A2D")
    private fun textColor() = Color.parseColor(if (lightMode) "#152039" else "#EEF2FF")
    private fun muted() = Color.parseColor(if (lightMode) "#667085" else "#8E9AB4")
    private fun accent() = Color.parseColor(if (lightMode) "#5969E8" else "#6C7CFF")
    private fun panel() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(surface(), 22); setPadding(dp(18), dp(18), dp(18), dp(18)); elevation = dp(3).toFloat(); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) } }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT }
    private fun button(label: String, size: Int, onClick: () -> Unit) = TextView(this).apply { text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(textColor()); background = rounded(surface2(), 14); setOnClickListener { onClick() }; layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(0, 0, dp(8), 0) } }
    private fun primaryButton(label: String, onClick: () -> Unit) = TextView(this).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD; background = rounded(accent(), 12); setPadding(dp(14), 0, dp(14), 0); setOnClickListener { onClick() }; minimumWidth = dp(92) }
    private fun secondaryButton(label: String, onClick: () -> Unit) = TextView(this).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(textColor()); typeface = android.graphics.Typeface.DEFAULT_BOLD; background = rounded(surface2(), 12); setPadding(dp(14), 0, dp(14), 0); setOnClickListener { onClick() }; minimumWidth = dp(92) }
    private fun logoView(size: Int) = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_INSIDE; layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
    private fun progress(percent: Int) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent.coerceIn(0, 100); progressTintList = android.content.res.ColorStateList.valueOf(accent()); layoutParams = LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) } }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatBytes(value: Long): String { var n = value.toDouble(); val units = arrayOf("B", "KB", "MB", "GB"); var i = 0; while (n >= 1024 && i < units.size - 1) { n /= 1024; i++ }; return if (i == 0) "${n.toLong()} B" else String.format(Locale.US, "%.1f %s", n, units[i]) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun startDownloadKeepAlive() { ContextCompat.startForegroundService(this, DownloadKeepAliveService.startIntent(this)) }
}
