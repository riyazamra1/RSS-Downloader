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
    // RSS Core is app-controlled. The host is no longer user-editable.
    private val api by lazy { NativeHostApi(BuildConfig.RSS_HOST_BASE_URL, BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null }) }
    private val imageExecutor = Executors.newFixedThreadPool(4)
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var urlInput: EditText
    private lateinit var stateText: TextView
    private lateinit var mediaPanel: LinearLayout
    private var currentTab = TabOrder.SOCIAL
    private var lightMode = false
    private var tabOrder = TabOrder.defaults.toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove legacy user-entered host values so an old accidental change can never redirect the app.
        prefs.edit().remove("hostUrl").remove("hostToken").apply()
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

    override fun onBackPressed() { if(drawerOpen){root.findViewWithTag<View>("rss_drawer_overlay")?.let{closeMenu(it)};return};super.onBackPressed() }\n\n    override fun onDestroy() {
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
        setPadding(dp(10), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, dp(74))
        addView(button("☰", 46) { showMenu() }, LinearLayout.LayoutParams(dp(46), dp(46)))
        addView(logoView(44), LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(10), 0, dp(12), 0) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("RSS Downloader", 17, textColor(), true))
            addView(text("Fast. Organized. Controlled.", 11, muted(), false))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(button("⚙", 46) { showSettings() }, LinearLayout.LayoutParams(dp(46), dp(46)))
    }

    // Clean fixed navigation: no floating pill, no oversized container, and no duplicate Settings tab.
    private fun buildBottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(surface())
        elevation = dp(8).toFloat()
        tabOrder.forEach { id ->
            val selected = id == currentTab
            val item = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(4))
                background = ColorDrawableCompat.transparent()
                setOnClickListener { if (currentTab != id) { currentTab = id; showHome() } }
                setOnLongClickListener { showTabOrderDialog(); true }
            }
            val label = text(tabShortLabel(id), 12, if (selected) accent() else muted(), selected)
            item.addView(label)
            val indicator = View(this@MainActivity).apply { setBackgroundColor(if (selected) accent() else Color.TRANSPARENT) }
            item.addView(indicator, LinearLayout.LayoutParams(dp(28), dp(3)).apply { topMargin = dp(5) })
            addView(item, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(64))
    }

    private fun showTabOrderDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        tabOrder.forEachIndexed { index, id ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            row.addView(text("${index + 1}. ${tabLabel(id)}", 15, textColor(), true), LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(secondaryButton("↑") { if (index > 0) { val x = tabOrder.removeAt(index); tabOrder.add(index - 1, x); prefs.edit().putString("tabOrder", TabOrder.save(tabOrder)).apply(); recreate() } }, LinearLayout.LayoutParams(dp(48), dp(44)).apply { setMargins(dp(4), 0, dp(4), 0) })
            row.addView(secondaryButton("↓") { if (index < tabOrder.lastIndex) { val x = tabOrder.removeAt(index); tabOrder.add(index + 1, x); prefs.edit().putString("tabOrder", TabOrder.save(tabOrder)).apply(); recreate() } }, LinearLayout.LayoutParams(dp(48), dp(44)))
            box.addView(row)
        }
        box.addView(secondaryButton("Reset default order") { prefs.edit().remove("tabOrder").apply(); recreate() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        android.app.AlertDialog.Builder(this).setTitle("Rearrange tabs").setView(box).setNegativeButton("Close", null).show()
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
        val hero = panel().apply { setPadding(dp(24), dp(24), dp(24), dp(20)) }
        hero.addView(text("SOCIAL DOWNLOADER", 10, Color.rgb(140, 156, 255), true))
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(20), 0, 0) }
        urlInput = EditText(this).apply { hint = "Paste URL here…"; setHintTextColor(muted()); setTextColor(textColor()); setSingleLine(true); setPadding(dp(16), 0, dp(16), 0); background = rounded(bg(), 14) }
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
                    grid.addView(movieCard(item.title, item.year ?: 2026, tab), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
                }
            }.onFailure { grid.addView(text(it.message ?: "Failed to load latest movies.", 13, muted(), false)) }
        }}
    }

    private fun movieCard(title: String, year: Int, tab: String): View = panel().apply {
        orientation = LinearLayout.VERTICAL
        val image = ImageView(this@MainActivity).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = title }
        addView(image, LinearLayout.LayoutParams(-1, dp(145)))
        loadWikipediaPoster(title, image)
        addView(text(title, 15, textColor(), true).apply { setPadding(dp(13), dp(7), dp(13), 0) })
        addView(text("$year • Authorized provider options", 11, muted(), false).apply { setPadding(dp(13), 0, dp(13), dp(7)) })
        addView(secondaryButton("Get options") { movieSearch(tab, title) }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(13), 0, dp(13), dp(13)) })
    }

    private fun analyzeUrl() {
        val url = urlInput.text.toString().trim()
        if (url.isBlank()) { toast("Paste a URL first."); return }
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
        api.createDownload(requestId, optionId) { result -> runOnUiThread {
            result.onSuccess {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
                else startDownloadKeepAlive()
                showDownloads()
            }.onFailure { toast(it.message ?: "Download failed.") }
        }}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4102 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startDownloadKeepAlive()
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
        drawer.addView(menuItem("⌂","Home",currentTab==TabOrder.SOCIAL){closeMenu(overlay);currentTab=TabOrder.SOCIAL;showHome()})
        drawer.addView(menuItem("↗","Social Downloader",currentTab==TabOrder.SOCIAL){closeMenu(overlay);currentTab=TabOrder.SOCIAL;showHome()})
        drawer.addView(menuItem("◉","Tamil Movies",currentTab==TabOrder.TAMIL){closeMenu(overlay);currentTab=TabOrder.TAMIL;showHome()})
        drawer.addView(menuItem("▣","Tamil Dubbed Movies",currentTab==TabOrder.DUBBED){closeMenu(overlay);currentTab=TabOrder.DUBBED;showHome()})
        drawer.addView(text("SUPPORT",10,muted(),true).apply{setPadding(dp(8),dp(16),dp(8),dp(6))})
        drawer.addView(menuItem("⇩","Downloads",false){closeMenu(overlay);showDownloads()})
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

    private fun showSettings() {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(28)) }
        box.addView(panel().apply {
            gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(18),dp(20),dp(18),dp(20))
            addView(logoView(64),LinearLayout.LayoutParams(dp(64),dp(64)))
            addView(text("RSS DOWNLOADER",20,textColor(),true).apply{setPadding(0,dp(8),0,0)})
            addView(text("SETTINGS",11,muted(),true).apply{setPadding(0,dp(3),0,0)})
        })
        box.addView(text("GENERAL",11,muted(),true).apply{setPadding(dp(4),dp(20),dp(4),dp(6))})
        box.addView(settingCard("☀","Appearance","Light / dark interface • saved automatically",lightMode){lightMode=it;prefs.edit().putBoolean("light",it).apply();recreate()})
        box.addView(settingCard("↗","Auto-paste copied URL","Detect and analyze a copied HTTP(S) URL",prefs.getBoolean("clipboard",true)){prefs.edit().putBoolean("clipboard",it).apply()})
        box.addView(text("TAB ORDER",11,muted(),true).apply{setPadding(dp(4),dp(20),dp(4),dp(6))})
        tabOrder.forEachIndexed{index,id->
            val row=panel().apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(8),dp(8))}
            row.addView(text(tabShortLabel(id),13,textColor(),true),LinearLayout.LayoutParams(dp(54),dp(48)))
            row.addView(text(tabLabel(id),14,textColor(),true),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(secondaryButton("↑"){moveTab(index,-1)},LinearLayout.LayoutParams(dp(48),dp(44)).apply{setMargins(dp(4),0,dp(4),0)})
            row.addView(secondaryButton("↓"){moveTab(index,1)},LinearLayout.LayoutParams(dp(48),dp(44)))
            box.addView(row,LinearLayout.LayoutParams(-1,dp(64)).apply{setMargins(0,dp(4),0,dp(4))})
        }
        box.addView(secondaryButton("Reset tab order"){tabOrder=TabOrder.defaults.toMutableList();prefs.edit().remove("tabOrder").apply();currentTab=TabOrder.SOCIAL;showSettings()},LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(text("RSS CORE",11,muted(),true).apply{setPadding(dp(4),dp(20),dp(4),dp(6))})
        box.addView(panel().apply{addView(text("RSS Core Host",14,textColor(),true));addView(text(BuildConfig.RSS_HOST_BASE_URL,12,muted(),false).apply{setPadding(0,dp(4),0,0)});addView(text("Build-controlled host. Changing it requires a new build.",10,muted(),false).apply{setPadding(0,dp(4),0,0)})})
        box.addView(text("PRIVACY & ACCESS",11,muted(),true).apply{setPadding(dp(4),dp(20),dp(4),dp(6))})
        box.addView(panel().apply{addView(text("Internet • Storage / media • Notifications",13,textColor(),true));addView(text("Required Android permissions are declared in the manifest.",10,muted(),false).apply{setPadding(0,dp(5),0,0)})})
        box.addView(text("RAZEEN SECURE SOLUTION",11,muted(),true).apply{setPadding(dp(4),dp(20),dp(4),dp(6))})
        box.addView(panel().apply{gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(16),dp(18),dp(16),dp(18));addView(logoView(58),LinearLayout.LayoutParams(dp(58),dp(58)));addView(text("RAZEEN SECURE SOLUTION",15,textColor(),true).apply{setPadding(0,dp(8),0,0)});addView(text("Mobile & PC Software • CCTV • Networking",10,muted(),false).apply{setPadding(0,dp(4),0,0)});addView(text("077 115 5504  •  070 155 5504",11,accent(),true).apply{setPadding(0,dp(7),0,0)});addView(text("rsscctvsolution@gmail.com",11,accent(),false).apply{setPadding(0,dp(3),0,0)});addView(text("www.rsscctvsolution.eu.cc",11,accent(),false).apply{setPadding(0,dp(3),0,0)})})
        scroll.addView(box);content.addView(scroll)
    }

    private fun settingCard(icon:String,title:String,subtitle:String,checked:Boolean,onChanged:(Boolean)->Unit):View=panel().apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(10),dp(10),dp(10))
        addView(TextView(this@MainActivity).apply{text=icon;textSize=18f;gravity=Gravity.CENTER;setTextColor(accent());background=rounded(surface2(),13)},LinearLayout.LayoutParams(dp(42),dp(42)))
        addView(LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(text(title,14,textColor(),true));addView(text(subtitle,10,muted(),false).apply{setPadding(0,dp(3),0,0)})},LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(12),0,dp(6),0)})
        addView(Switch(this@MainActivity).apply{isChecked=checked;setOnCheckedChangeListener{_,v->onChanged(v)}})
    }


    private fun moveTab(index: Int, delta: Int) {
        val target = index + delta
        if (target !in tabOrder.indices) return
        val moved = tabOrder.removeAt(index)
        tabOrder.add(target, moved)
        prefs.edit().putString("tabOrder", TabOrder.save(tabOrder)).apply()
        currentTab = moved
        showSettings()
    }

    private fun settingRow(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(16), dp(10), dp(16))
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 15, textColor(), true)); addView(text(subtitle, 11, muted(), false)) }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Switch(this@MainActivity).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> onChanged(value) } })
    }

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

private object ColorDrawableCompat {
    fun transparent(): GradientDrawable = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
}
