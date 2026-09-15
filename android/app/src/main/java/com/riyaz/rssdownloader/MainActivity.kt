package com.riyaz.rssdownloader

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("rss-downloader", MODE_PRIVATE) }
    private val api by lazy { NativeHostApi(BuildConfig.RSS_HOST_BASE_URL, BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null }) }
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var badge: TextView
    private lateinit var sideMenu: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var stateText: TextView
    private lateinit var mediaPanel: LinearLayout
    private var currentTab = "social-downloader"
    private var lightMode = false
    private val tabs = listOf("social-downloader", "tamil-movies", "tamil-dubbed-movies")
    private val movieTitles = mapOf(
        "tamil-movies" to listOf("Maharaja" to 2024, "Amaran" to 2024, "Lubber Pandhu" to 2024, "Good Night" to 2023, "Parking" to 2023, "Tourist Family" to 2025, "Dragon" to 2025, "Retro" to 2025),
        "tamil-dubbed-movies" to listOf("Kalki 2898 AD" to 2024, "Pushpa 2: The Rule" to 2024, "Hanuman" to 2024, "Leo" to 2023, "Salaar" to 2023, "Baahubali 2" to 2017, "RRR" to 2022, "Jailer" to 2023),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lightMode = prefs.getBoolean("light", false)
        applyTheme()
        startDownloadKeepAlive()
        requestNotificationPermissionIfNeeded()
        buildApp()
        readClipboardUrl()
    }

    private fun buildApp() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg()); setPadding(0, 0, 0, dp(88)) }
        root.addView(buildTopBar())
        content = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        root.addView(content)
        root.addView(buildBottomNav())
        sideMenu = buildSideMenu()
        val frame = FrameLayout(this)
        frame.addView(root)
        frame.addView(sideMenu, FrameLayout.LayoutParams(dp(76), -1))
        setContentView(frame)
        showHome()
    }

    private fun buildTopBar(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(88), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, dp(74))
        addView(button("☰", 46) { toggleMenu() })
        addView(logoView(44), LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(12), 0, dp(12), 0) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("RSS Downloader", 17, textColor(), true))
            addView(text("Fast. Organized. Controlled.", 11, muted(), false))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        badge = text("↓ 0", 12, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = pill(accent()) }
        addView(badge, LinearLayout.LayoutParams(dp(54), dp(38)))
        addView(button("⚙", 46) { showSettings() })
    }

    private fun buildBottomNav(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(dp(7), dp(6), dp(7), dp(6))
        background = rounded(surface(), 22)
        elevation = dp(12).toFloat()
        val labels = listOf("⇩" to "Social Downloader", "🎬" to "Tamil Movie", "▶" to "Tamil Dubbed Movie", "⚙" to "Settings")
        labels.forEachIndexed { index, pair ->
            val item = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), dp(8), dp(3), dp(7)); background = rounded(if (index == 0) surface2() else Color.TRANSPARENT, 16) }
            item.addView(text(pair.first, 19, textColor(), false).apply { gravity = Gravity.CENTER })
            item.addView(text(pair.second, 9, if (index == 0) textColor() else muted(), true).apply { gravity = Gravity.CENTER })
            item.setOnClickListener { if (index == 3) showSettings() else { currentTab = tabs[index]; showHome() } }
            addView(item, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        layoutParams = FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(10), 0, dp(10), dp(8)) }
    }

    private fun buildSideMenu(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(surface())
        elevation = dp(18).toFloat()
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(14), dp(10), dp(14))
            addView(button("☰", 48) { toggleMenu() })
            addView(logoView(40), LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(12), 0, dp(10), 0) })
        }, LinearLayout.LayoutParams(-1, dp(78)))
        addView(menuItem("↓", "Downloads") { showDownloads(); closeMenu() })
        addView(text("APPEARANCE", 10, muted(), true).apply { setPadding(dp(14), dp(18), 0, dp(6)) })
        addView(menuItem("☾", "Light mode") { lightMode = !lightMode; prefs.edit().putBoolean("light", lightMode).apply(); applyTheme(); buildApp() })
        addView(menuItem("⚙", "Settings") { showSettings(); closeMenu() })
    }

    private fun menuItem(icon: String, label: String, action: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(14), dp(8), dp(14))
        addView(text(icon, 20, textColor(), false).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(30), -1) })
        addView(text(label, 15, textColor(), true))
        setOnClickListener { action() }
    }

    private fun showHome() {
        content.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(94), dp(10), dp(18), dp(18)) }
        if (currentTab == "social-downloader") buildSocial(box) else buildMovie(box, currentTab)
        scroll.addView(box)
        content.addView(scroll)
    }

    private fun buildSocial(box: LinearLayout) {
        val hero = panel()
        hero.setPadding(dp(28), dp(28), dp(28), dp(24))
        hero.addView(text("SOCIAL DOWNLOADER", 10, Color.rgb(140, 156, 255), true))
        hero.addView(text("Paste a link.\nRSS handles the rest.", 36, textColor(), true).apply { setPadding(0, dp(4), 0, dp(8)) })
        hero.addView(text("Copy a supported URL and it will be detected automatically.", 14, muted(), false))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(24), 0, 0) }
        urlInput = EditText(this).apply { hint = "Paste URL here…"; hintTextColor = muted(); setTextColor(textColor()); setSingleLine(true); setPadding(dp(16), 0, dp(16), 0); background = rounded(bg(), 14) }
        row.addView(urlInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        row.addView(primaryButton("Analyze") { analyzeUrl() }, LinearLayout.LayoutParams(dp(120), dp(54)).apply { setMargins(dp(10), 0, 0, 0) })
        hero.addView(row)
        stateText = text("", 13, muted(), false).apply { visibility = View.GONE; setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(surface(), 12) }
        hero.addView(stateText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        box.addView(hero)
        mediaPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(18), dp(10), dp(18), 0) }
        box.addView(mediaPanel)
    }

    private fun buildMovie(box: LinearLayout, tab: String) {
        box.addView(text(tabLabel(tab).uppercase(Locale.US), 10, Color.rgb(140,156,255), true).apply { setPadding(dp(18), dp(12), 0, dp(5)) })
        box.addView(text("Pre-loaded movies", 28, textColor(), true).apply { setPadding(dp(18), 0, 0, dp(12)) })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        val list = movieTitles[tab].orEmpty()
        list.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (title, year) -> row.addView(movieCard(title, year, tab), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }) }
            if (pair.size == 1) row.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
        box.addView(grid)
        val search = EditText(this).apply { hint = "Optional: search more movies…"; hintTextColor = muted(); setTextColor(textColor()); setSingleLine(true); background = rounded(bg(), 14); setPadding(dp(16), 0, dp(16), 0) }
        val row = LinearLayout(this).apply { setPadding(dp(18), dp(12), dp(18), 0) }
        row.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(primaryButton("Search") { movieSearch(tab, search.text.toString()) }, LinearLayout.LayoutParams(dp(110), dp(52)).apply { setMargins(dp(10),0,0,0) })
        box.addView(row)
    }

    private fun movieCard(title: String, year: Int, tab: String): View = panel().apply {
        orientation = LinearLayout.VERTICAL
        addView(logoView(120).apply { setPadding(dp(20), dp(20), dp(20), dp(10)) }, LinearLayout.LayoutParams(-1, dp(160)))
        addView(text(title, 15, textColor(), true).apply { setPadding(dp(13), dp(5), dp(13), 0) })
        addView(text("$year • Authorized provider options", 11, muted(), false).apply { setPadding(dp(13), 0, dp(13), dp(7)) })
        addView(secondaryButton("Get options") { movieSearch(tab, title) }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(13), 0, dp(13), dp(13) ) })
    }

    private fun analyzeUrl() {
        val url = urlInput.text.toString().trim()
        if (url.isBlank()) return
        stateText.visibility = View.VISIBLE; stateText.text = if (api.configured()) "Analyzing URL…" else "RSS host API is not configured."
        if (!api.configured()) return
        api.analyze(url) { result -> runOnUiThread {
            result.onSuccess { analysis -> stateText.text = analysis.title; renderOptions(analysis.requestId, analysis.mediaOptions) }
                .onFailure { stateText.text = it.message ?: "Analysis failed." }
        }}
    }

    private fun movieSearch(tab: String, query: String) {
        if (!api.configured()) { toast("RSS host API is not configured."); return }
        api.search(tab, query) { result -> runOnUiThread {
            result.onSuccess { results ->
                val panel = panel().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
                panel.addView(text("Search results", 18, textColor(), true))
                results.forEach { item ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
                    row.addView(logoView(54))
                    row.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(item.title, 14, textColor(), true)); addView(text(item.year?.toString() ?: "", 11, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12),0,dp(8),0) })
                    row.addView(primaryButton("Select") { if (item.mediaOptions.isNotEmpty()) renderOptions(item.requestId ?: item.id, item.mediaOptions) else toast("No authorized quality options available.") })
                    panel.addView(row)
                }
                content.addView(panel, FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP; setMargins(dp(94), dp(12), dp(18), 0) })
            }.onFailure { toast(it.message ?: "Search failed.") }
        }}
    }

    private fun renderOptions(requestId: String, options: List<NativeHostApi.MediaOption>) {
        mediaPanel.visibility = View.VISIBLE; mediaPanel.removeAllViews(); mediaPanel.addView(text("Choose an authorized format", 13, muted(), false))
        options.forEach { option ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(13), 0, dp(13)) }
            row.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(option.kind.uppercase(Locale.US), 13, textColor(), true)); addView(text(listOfNotNull(option.format, option.quality, option.sizeBytes?.let(::formatBytes)).joinToString(" • "), 11, muted(), false)) }, LinearLayout.LayoutParams(0,-2,1f))
            row.addView(primaryButton("Download") { createDownload(requestId, option.id) })
            mediaPanel.addView(row)
        }
    }

    private fun createDownload(requestId: String, optionId: String) {
        if (!api.configured()) { toast("RSS host API is not configured."); return }
        api.createDownload(requestId, optionId) { result -> runOnUiThread { result.onSuccess { showDownloads() }.onFailure { toast(it.message ?: "Download failed.") } } }
    }

    private fun showDownloads() {
        content.removeAllViews(); val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(94), dp(18), dp(18), dp(18)) }
        box.addView(text("DOWNLOADS", 10, Color.rgb(140,156,255), true)); box.addView(text("Downloads", 30, textColor(), true))
        box.addView(secondaryButton("Refresh") { showDownloads() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        val state = text(if (api.configured()) "Loading…" else "RSS host API is not configured.", 13, muted(), false).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        box.addView(state)
        scroll.addView(box); content.addView(scroll)
        if (api.configured()) api.listDownloads { result -> runOnUiThread { result.onSuccess { jobs -> state.text = if (jobs.isEmpty()) "No downloads yet." else "${jobs.size} download(s)"; jobs.forEach { job -> box.addView(jobCard(job)) } }.onFailure { state.text = it.message ?: "Failed to load downloads." } } }
    }

    private fun jobCard(job: NativeHostApi.Job): View = panel().apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(logoView(56))
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(job.title ?: "RSS Download", 14, textColor(), true)); addView(text("${job.status} • ${job.progress ?: 0}%", 11, muted(), false)); addView(progress(job.progress ?: 0)) }, LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(12),0,dp(8),0) })
    }

    private fun progress(percent: Int): View = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = percent.coerceIn(0,100); progressTintList = android.content.res.ColorStateList.valueOf(accent()); layoutParams = LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) } }

    private fun showSettings() {
        content.removeAllViews(); val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(94), dp(18), dp(18), dp(18)) }
        box.addView(text("RSS DOWNLOADER", 10, Color.rgb(140,156,255), true)); box.addView(text("Settings", 30, textColor(), true))
        box.addView(settingRow("Appearance", "Use light or dark interface", lightMode) { lightMode = it; prefs.edit().putBoolean("light", it).apply(); applyTheme(); showSettings() })
        box.addView(settingRow("Auto-paste copied URL", "Detect a copied HTTP(S) URL when RSS Downloader is active", prefs.getBoolean("clipboard", true)) { prefs.edit().putBoolean("clipboard", it).apply() })
        box.addView(settingRow("Pre-loaded movie lists", "Show Tamil Movies and Tamil Dubbed Movies immediately", prefs.getBoolean("preload", true)) { prefs.edit().putBoolean("preload", it).apply() })
        box.addView(secondaryButton("Reset tab order") { prefs.edit().remove("tabOrder").apply(); toast("Tab order reset") }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        box.addView(LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); addView(logoView(42)); addView(text("RSS Downloader\nRazeen Secure Solution", 14, textColor(), true).apply { setPadding(dp(12),0,0,0) }) }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        scroll.addView(box); content.addView(scroll)
    }

    private fun settingRow(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean)->Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(16),dp(10),dp(16)); background = rounded(surface(), 0)
        addView(LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; addView(text(title,15,textColor(),true)); addView(text(subtitle,11,muted(),false)) }, LinearLayout.LayoutParams(0,-2,1f))
        val sw = Switch(this@MainActivity).apply { isChecked=checked; buttonTintList=android.content.res.ColorStateList.valueOf(accent()); setOnCheckedChangeListener { _, value -> onChanged(value) } }
        addView(sw)
    }

    private fun readClipboardUrl() {
        if (!prefs.getBoolean("clipboard", true)) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.startsWith("http://") || text.startsWith("https://")) { currentTab="social-downloader"; showHome(); urlInput.setText(text) }
    }

    private fun toggleMenu() { val lp = sideMenu.layoutParams as FrameLayout.LayoutParams; lp.width = if (lp.width == dp(76)) dp(300) else dp(76); sideMenu.layoutParams = lp }
    private fun closeMenu() { val lp = sideMenu.layoutParams as FrameLayout.LayoutParams; lp.width=dp(76); sideMenu.layoutParams=lp }
    private fun tabLabel(id:String) = when(id){"social-downloader"->"Social Downloader";"tamil-movies"->"Tamil Movies";else->"Tamil Dubbed Movies"}
    private fun applyTheme(){ window.statusBarColor=bg(); window.navigationBarColor=bg(); if(::root.isInitialized) root.setBackgroundColor(bg()) }
    private fun bg()=Color.parseColor(if(lightMode)"#F4F6FB" else "#070B16")
    private fun surface()=Color.parseColor(if(lightMode)"#FFFFFF" else "#0D1324")
    private fun surface2()=Color.parseColor(if(lightMode)"#EDF1F8" else "#121A2D")
    private fun textColor()=Color.parseColor(if(lightMode)"#152039" else "#EEF2FF")
    private fun muted()=Color.parseColor(if(lightMode)"#667085" else "#8E9AB4")
    private fun accent()=Color.parseColor(if(lightMode)"#5969E8" else "#6C7CFF")
    private fun panel()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(surface(),22); setPadding(dp(18),dp(18),dp(18),dp(18)); elevation=dp(3).toFloat(); layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)} }
    private fun text(value:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply{ text=value; textSize=size.toFloat(); setTextColor(color); typeface=if(bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT }
    private fun button(label:String,size:Int,onClick:()->Unit)=TextView(this).apply{ text=label; textSize=20f; gravity=Gravity.CENTER; setTextColor(textColor()); background=rounded(surface2(),14); setOnClickListener{onClick()}; layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)).apply{setMargins(0,0,dp(8),0)} }
    private fun primaryButton(label:String,onClick:()->Unit)=TextView(this).apply{ text=label; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); typeface=android.graphics.Typeface.DEFAULT_BOLD; background=rounded(accent(),12); setPadding(dp(14),0,dp(14),0); setOnClickListener{onClick()}; minimumWidth=dp(92); layoutParams=LinearLayout.LayoutParams(-2,dp(44)) }
    private fun secondaryButton(label:String,onClick:()->Unit)=TextView(this).apply{ text=label; textSize=13f; gravity=Gravity.CENTER; setTextColor(textColor()); typeface=android.graphics.Typeface.DEFAULT_BOLD; background=rounded(surface2(),12); setPadding(dp(14),0,dp(14),0); setOnClickListener{onClick()}; minimumWidth=dp(92) }
    private fun logoView(size:Int)=ImageView(this).apply{ setImageResource(R.drawable.rss_downloader_logo); scaleType=ImageView.ScaleType.CENTER_INSIDE; layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)) }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun pill(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(99).toFloat()}
    private fun dp(value:Int)= (value*resources.displayMetrics.density).toInt()
    private fun formatBytes(value:Long):String{var n=value.toDouble();val units=arrayOf("B","KB","MB","GB");var i=0;while(n>=1024&&i<units.size-1){n/=1024;i++};return if(i==0)"${n.toLong()} B" else String.format(Locale.US,"%.1f %s",n,units[i])}
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    private fun startDownloadKeepAlive(){ContextCompat.startForegroundService(this,DownloadKeepAliveService.startIntent(this))}
    private fun requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),4102)}
}
