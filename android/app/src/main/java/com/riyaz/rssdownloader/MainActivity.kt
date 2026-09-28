package com.riyaz.rssdownloader

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("rss-downloader-license", MODE_PRIVATE) }
    private val api by lazy {
        NativeHostApi(BuildConfig.RSS_HOST_BASE_URL,
            BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null },
            prefs.getString("app_key", null))
    }
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private var mainTab = 0
    private var movieTab = 0
    private var socialUrl: EditText? = null
    private var pendingAnalyze: Runnable? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        buildRoot()
        loadClipboard()
    }

    override fun onDestroy() {
        pendingAnalyze?.let(handler::removeCallbacks)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildRoot() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9,9,9))
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16),dp(12),dp(16),dp(16))
        }
        root.addView(ScrollView(this).apply { isFillViewport=true; addView(content) },
            LinearLayout.LayoutParams(-1,0,1f))
        root.addView(mainNavigation(), LinearLayout.LayoutParams(-1,dp(72)))
        setContentView(root)
        render()
    }

    private fun mainNavigation(): View = LinearLayout(this).apply {
        setPadding(dp(8),dp(7),dp(8),dp(9))
        setBackgroundColor(Color.rgb(18,18,18))
        listOf("Social Downloader","Movie","Settings").forEachIndexed { index,name ->
            addView(button(if (mainTab == index) "● $name" else name,54) { mainTab=index; render() },
                LinearLayout.LayoutParams(0,dp(54),1f).apply { setMargins(dp(3),0,dp(3),0) })
        }
    }

    private fun render() {
        content.removeAllViews()
        when(mainTab) {
            0 -> renderSocial()
            1 -> renderMovie()
            else -> renderSettings()
        }
    }

    private fun renderSocial() {
        content.addView(label("Social Downloader",28,Color.WHITE,true))
        content.addView(label("Paste a link. RSS Downloader detects and analyzes it automatically.",
            13,Color.rgb(185,185,185),false).apply { setPadding(0,dp(5),0,dp(14)) })

        val card=card()
        card.addView(label("AUTOMATIC URL DETECTION",11,Color.rgb(212,175,55),true))
        val input=EditText(this).apply {
            hint="Paste supported URL"
            setHintTextColor(Color.rgb(115,115,115))
            setTextColor(Color.WHITE)
            textSize=15f
            singleLine=true
            setPadding(dp(14),0,dp(14),0)
            setBackgroundColor(Color.rgb(28,28,28))
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_share,0,0,0)
            compoundDrawablePadding=dp(10)
            contentDescription="Social media URL"
        }
        socialUrl=input
        card.addView(input,LinearLayout.LayoutParams(-1,dp(56)))
        card.addView(label("✓ Automatic analysis • no Analyze button",
            12,Color.rgb(155,155,155),false).apply { setPadding(0,dp(9),0,dp(0)) })
        content.addView(card)

        val supported=card()
        supported.addView(label("SUPPORTED SOURCES",11,Color.rgb(212,175,55),true))
        supported.addView(label("Facebook  •  Instagram  •  X  •  Telegram",
            13,Color.WHITE,false).apply { setPadding(0,dp(8),0,dp(3)) })
        supported.addView(label("WhatsApp Status  •  Pinterest",
            13,Color.rgb(185,185,185),false))
        content.addView(supported)

        input.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                scheduleAnalysis(s.toString())
            }
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        content.addView(connectionCard())
    }

    private fun scheduleAnalysis(value:String) {
        pendingAnalyze?.let(handler::removeCallbacks)
        val url=value.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        pendingAnalyze=Runnable { analyze(url) }
        handler.postDelayed(pendingAnalyze!!,650)
    }

    private fun analyze(url:String) {
        if(mainTab!=0 || socialUrl?.text.toString().trim()!=url) return
        val loading=label("Analyzing…",13,Color.rgb(212,175,55),false)
        content.addView(loading)
        api.analyze(url) { result ->
            runOnUiThread {
                if(loading.parent===content) content.removeView(loading)
                result.onSuccess { a ->
                    val card=card()
                    card.addView(label(a.title,18,Color.WHITE,true))
                    if(a.mediaOptions.isEmpty()) {
                        card.addView(label("No downloadable format returned.",13,Color.rgb(180,180,180),false))
                    } else {
                        a.mediaOptions.forEach { option ->
                            val quality=option.quality ?: "Available"
                            val text=option.format.uppercase()+" • "+quality
                            card.addView(button(text,48) {
                                api.createDownload(a.requestId,option.id) { r ->
                                    runOnUiThread {
                                        r.onSuccess { Toast.makeText(this,"Download queued.",Toast.LENGTH_SHORT).show() }
                                            .onFailure { Toast.makeText(this,it.message ?: "Download failed.",Toast.LENGTH_LONG).show() }
                                    }
                                }
                            },LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(7) })
                        }
                    }
                    content.addView(card)
                }.onFailure { error ->
                    content.addView(label("Analysis failed: "+(error.message ?: "Unknown error"),
                        12,Color.rgb(220,120,120),false))
                }
            }
        }
    }

    private fun renderMovie() {
        content.addView(label("Movies",28,Color.WHITE,true))
        content.addView(label("Browse the pre-loaded catalogue or search within a category.",
            13,Color.rgb(185,185,185),false).apply { setPadding(0,dp(5),0,dp(14)) })

        val sub=LinearLayout(this).apply {
            setPadding(dp(4),dp(4),dp(4),dp(4))
            setBackgroundColor(Color.rgb(20,20,20))
        }
        val tamil=button(if(movieTab==0) "● Tamil" else "Tamil",44) { movieTab=0; render() }
        val dubbed=button(if(movieTab==1) "● Dubbed" else "Dubbed",44) { movieTab=1; render() }
        sub.addView(tamil,LinearLayout.LayoutParams(0,dp(44),1f))
        sub.addView(dubbed,LinearLayout.LayoutParams(0,dp(44),1f).apply { leftMargin=dp(6) })
        content.addView(sub)

        val searchCard=card()
        searchCard.addView(label("MOVIE SEARCH",11,Color.rgb(212,175,55),true))
        val search=EditText(this).apply {
            hint=if(movieTab==0) "Search Tamil movies" else "Search Tamil dubbed movies"
            setHintTextColor(Color.rgb(115,115,115))
            setTextColor(Color.WHITE)
            textSize=15f
            singleLine=true
            setPadding(dp(14),0,dp(14),0)
            setBackgroundColor(Color.rgb(28,28,28))
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        searchCard.addView(search,LinearLayout.LayoutParams(-1,dp(54)).apply { topMargin=dp(8) })
        search.setOnEditorActionListener { _, actionId, _ ->
            if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                loadMovies(search.text.toString()); true
            } else false
        }
        content.addView(searchCard)

        val catalogue=card()
        catalogue.addView(label(if(movieTab==0) "TAMIL CATALOGUE" else "DUBBED CATALOGUE",
            11,Color.rgb(212,175,55),true))
        catalogue.addView(label("Loading pre-loaded movies…",12,Color.rgb(155,155,155),false)
            .apply { setPadding(0,dp(8),0,dp(0)) })
        content.addView(catalogue)
        loadMovies("")
    }

    private fun loadMovies(query:String) {
        val loading=label("Loading catalogue…",13,Color.rgb(212,175,55),false)
        content.addView(loading)
        val tab=if(movieTab==0) "tamil-movies" else "tamil-dubbed-movies"
        api.search(tab,query) { result ->
            runOnUiThread {
                if(loading.parent===content) content.removeView(loading)
                result.onSuccess { movies ->
                    val section=card()
                    section.addView(label(if(query.isBlank()) "AVAILABLE MOVIES" else "SEARCH RESULTS",
                        11,Color.rgb(212,175,55),true))
                    if(movies.isEmpty()) {
                        section.addView(label("No movies returned from the configured source.",
                            13,Color.rgb(180,180,180),false).apply { setPadding(0,dp(10),0,dp(0)) })
                    } else movies.forEachIndexed { index,movie ->
                        val row=LinearLayout(this).apply {
                            orientation=LinearLayout.VERTICAL
                            setPadding(dp(2),dp(13),dp(2),dp(13))
                            isClickable=true
                            isFocusable=true
                        }
                        row.addView(label(movie.title,17,Color.WHITE,true))
                        val meta=listOfNotNull(movie.year?.toString(),movie.language,movie.releaseDate)
                            .joinToString(" • ")
                        if(meta.isNotBlank()) row.addView(label(meta,12,Color.rgb(160,160,160),false)
                            .apply { setPadding(0,dp(5),0,0) })
                        row.addView(label("View details  ›",12,Color.rgb(212,175,55),false)
                            .apply { setPadding(0,dp(7),0,dp(0)) })
                        row.setOnClickListener { showMovie(movie) }
                        section.addView(row)
                        if(index < movies.lastIndex) section.addView(divider())
                    }
                    content.addView(section)
                }.onFailure { error ->
                    content.addView(label("Movie source error: "+(error.message ?: "Unknown error"),
                        12,Color.rgb(220,120,120),false))
                }
            }
        }
    }

    private fun showMovie(movie:NativeHostApi.SearchResult) {
        content.removeAllViews()
        content.addView(label("Movie Details",26,Color.WHITE,true))
        content.addView(label("Review the movie information and choose an available quality.",
            13,Color.rgb(185,185,185),false).apply { setPadding(0,dp(5),0,dp(14)) })

        val info=card()
        info.addView(label(movie.title,21,Color.WHITE,true))
        val meta=listOfNotNull(movie.year?.toString(),movie.language,movie.releaseDate,movie.runtime,movie.director)
            .joinToString(" • ")
        if(meta.isNotBlank()) info.addView(label(meta,12,Color.rgb(165,165,165),false)
            .apply { setPadding(0,dp(6),0,dp(0)) })
        movie.synopsis?.takeIf { it.isNotBlank() }?.let {
            info.addView(label(it,13,Color.rgb(205,205,205),false).apply {
                setPadding(0,dp(14),0,dp(0))
            })
        }
        content.addView(info)

        val quality=card()
        quality.addView(label("AVAILABLE QUALITY",11,Color.rgb(212,175,55),true))
        if(movie.mediaOptions.isEmpty()) {
            quality.addView(label("No authorized download options are currently available.",
                13,Color.rgb(180,180,180),false).apply { setPadding(0,dp(9),0,dp(0)) })
        } else {
            movie.mediaOptions.forEach { option ->
                val qualityName=(option.quality ?: "Available").uppercase()
                val formatName=option.format.uppercase()
                quality.addView(button("$formatName  •  $qualityName",50) {
                    api.createDownload(movie.requestId ?: movie.id,option.id) { r ->
                        runOnUiThread {
                            r.onSuccess {
                                Toast.makeText(this,"Download queued.",Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(this,it.message ?: "Download failed.",Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(8) })
            }
        }
        content.addView(quality)
        content.addView(button("Back to Movies",48) { render() },
            LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(12) })
    }

    private fun renderSettings() {
        content.addView(label("Settings",28,Color.WHITE,true))
        content.addView(label("RSS Downloader preferences and account controls.",
            13,Color.rgb(185,185,185),false).apply { setPadding(0,dp(5),0,dp(14)) })

        val account=card()
        account.addView(label("ACCOUNT",11,Color.rgb(212,175,55),true))
        account.addView(label("RSS Core account",16,Color.WHITE,true).apply { setPadding(0,dp(8),0,dp(2)) })
        account.addView(label("Registration, verification and session status",12,Color.rgb(165,165,165),false))
        account.addView(button("Manage Account  ›",46) {
            Toast.makeText(this,"Account management will use the RSS Core session.",Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(10) })
        content.addView(account)

        val downloads=card()
        downloads.addView(label("DOWNLOADS",11,Color.rgb(212,175,55),true))
        downloads.addView(settingsRow("Download location","Choose where completed files are saved"))
        downloads.addView(settingsRow("Download history","View completed and queued downloads"))
        content.addView(downloads)

        val appearance=card()
        appearance.addView(label("APPEARANCE",11,Color.rgb(212,175,55),true))
        appearance.addView(settingsRow("Theme","Light / Dark / System"))
        appearance.addView(settingsRow("Interface","RSS KIT visual system"))
        content.addView(appearance)

        val service=card()
        service.addView(label("RSS SERVICES",11,Color.rgb(212,175,55),true))
        service.addView(settingsRow("Premium","Premium entitlement is controlled by RSS Core"))
        service.addView(settingsRow("Connection","RSS Core control plane • RAY server-side"))
        content.addView(service)

        val information=card()
        information.addView(label("INFORMATION",11,Color.rgb(212,175,55),true))
        information.addView(settingsRow("About","RSS Downloader information"))
        information.addView(settingsRow("Privacy Policy","Privacy information"))
        information.addView(settingsRow("Terms & Conditions","Terms information"))
        content.addView(information)
    }

    private fun settingsRow(title:String,subtitle:String):View=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(dp(2),dp(12),dp(2),dp(12))
        addView(label(title,15,Color.WHITE,true))
        addView(label(subtitle,12,Color.rgb(165,165,165),false).apply { setPadding(0,dp(4),0,0) })
    }

    private fun connectionCard():View=card().apply {
        addView(label("PLATFORM",11,Color.rgb(212,175,55),true))
        addView(label("RSS Core: "+if(api.configured()) "configured" else "not configured",
            13,Color.WHITE,false))
        addView(label("RAY: server-side through RSS Core",13,Color.rgb(165,165,165),false))
    }

    private fun loadClipboard() {
        val clip=clipboard.primaryClip ?: return
        if(clip.itemCount==0) return
        val value=clip.getItemAt(0).coerceToText(this).toString().trim()
        if(value.startsWith("http://") || value.startsWith("https://")) socialUrl?.setText(value)
    }

    private fun card():LinearLayout=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(dp(16),dp(16),dp(16),dp(16))
        setBackgroundColor(Color.rgb(20,20,20))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(6),0,dp(10)) }
    }

    private fun button(text:String,height:Int,action:()->Unit={}):TextView=TextView(this).apply {
        this.text=text
        textSize=13f
        gravity=Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(38,38,38))
        isClickable=true
        setOnClickListener { action() }
    }

    private fun label(text:String,size:Int,color:Int,bold:Boolean):TextView=TextView(this).apply {
        this.text=text
        textSize=size.toFloat()
        setTextColor(color)
        if(bold) typeface=android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun divider():View=View(this).apply {
        setBackgroundColor(Color.rgb(48,48,48))
        layoutParams=LinearLayout.LayoutParams(-1,dp(1))
    }

    private fun dp(value:Int):Int=(value*resources.displayMetrics.density).toInt()
}