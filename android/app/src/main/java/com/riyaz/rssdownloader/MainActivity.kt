package com.riyaz.rssdownloader

import android.os.Bundle
import android.content.Context
import android.content.ClipboardManager
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import com.riyaz.rss.common.RssBrand
import com.riyaz.rss.common.components.RssSettingRow
import com.riyaz.rss.common.theme.RssTheme
import com.riyaz.rss.common.navigation.RssMenuItem
import com.riyaz.rss.common.navigation.RssSlideMenu
import coil.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun rssOnMain(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("rss-downloader-license", MODE_PRIVATE) }
    private val api by lazy { NativeHostApi(BuildConfig.RSS_HOST_BASE_URL, BuildConfig.RSS_HOST_ACCESS_TOKEN.ifBlank { null }, prefs.getString("app_key", null)) }

    override fun onCreate(state: Bundle?) {
        applyTheme(prefs.getString("theme", "System default") ?: "System default")
        super.onCreate(state)
        setContent {
            val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            RssTheme(darkTheme = dark) { DownloaderApp(api, prefs) }
        }
    }

    private fun applyTheme(theme: String) {
        val mode = when (theme) {
            "Light" -> AppCompatDelegate.MODE_NIGHT_NO
            "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }
}

private fun queueAndDeliver(context: Context, api: NativeHostApi, prefs: android.content.SharedPreferences, requestId: String, optionId: String, label: String) {
    api.createDownload(requestId, optionId) { result ->
        result.onFailure { error -> rssOnMain { Toast.makeText(context, error.message ?: "Download failed", Toast.LENGTH_LONG).show() } }
        result.onSuccess { job ->
            rssOnMain { Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show() }
            fun poll(attempt: Int) {
                if (attempt > 150) {
                    rssOnMain { Toast.makeText(context, "Download is still processing. Check Download history.", Toast.LENGTH_LONG).show() }
                    return
                }
                api.getDownload(job.jobId) { statusResult ->
                    statusResult.onFailure { error -> rssOnMain { Toast.makeText(context, error.message ?: "Download status unavailable", Toast.LENGTH_LONG).show() } }
                    statusResult.onSuccess { status ->
                        val state = status.status.lowercase()
                        if (state in listOf("completed", "complete", "ready", "success")) {
                            val filename = status.filename?.takeIf { it.isNotBlank() } ?: label.replace(Regex("[^A-Za-z0-9._-]"), "_")
                            val mime = status.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                            val treeUri = prefs.getString("download_tree_uri", null)?.let(Uri::parse)
                            if (treeUri != null) {
                                try {
                                    val parent = DocumentsContract.buildChildDocumentsUriUsingTree(
                                        treeUri,
                                        DocumentsContract.getTreeDocumentId(treeUri)
                                    )
                                    val docUri = DocumentsContract.createDocument(
                                        context.contentResolver,
                                        parent,
                                        mime,
                                        filename
                                    ) ?: throw IllegalStateException("Unable to create the selected download file.")
                                    context.contentResolver.openOutputStream(docUri)?.let { output ->
                                        api.downloadFile(job.jobId, output) { fileResult ->
                                            fileResult.onSuccess {
                                                rssOnMain { Toast.makeText(context, "Download saved to selected location", Toast.LENGTH_LONG).show() }
                                            }.onFailure {
                                                context.contentResolver.delete(docUri, null, null)
                                                rssOnMain { Toast.makeText(context, it.message ?: "File delivery failed", Toast.LENGTH_LONG).show() }
                                            }
                                        }
                                    } ?: throw IllegalStateException("Unable to open the selected download file.")
                                } catch (e: Exception) {
                                    rssOnMain { Toast.makeText(context, e.message ?: "Selected download location failed", Toast.LENGTH_LONG).show() }
                                }
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val values = android.content.ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/RSS Downloader")
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                }
                                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                if (uri == null) {
                                    rssOnMain { Toast.makeText(context, "Unable to create the download file.", Toast.LENGTH_LONG).show() }
                                    return@getDownload
                                }
                                try {
                                    context.contentResolver.openOutputStream(uri)?.let { output ->
                                        api.downloadFile(job.jobId, output) { fileResult ->
                                            fileResult.onSuccess {
                                                val done = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                                                context.contentResolver.update(uri, done, null, null)
                                                rssOnMain { Toast.makeText(context, "Download saved to Downloads/RSS Downloader", Toast.LENGTH_LONG).show() }
                                            }.onFailure {
                                                context.contentResolver.delete(uri, null, null)
                                                rssOnMain { Toast.makeText(context, it.message ?: "File delivery failed", Toast.LENGTH_LONG).show() }
                                            }
                                        }
                                    } ?: throw IllegalStateException("Unable to open the download file.")
                                } catch (e: Exception) {
                                    context.contentResolver.delete(uri, null, null)
                                    rssOnMain { Toast.makeText(context, e.message ?: "File delivery failed", Toast.LENGTH_LONG).show() }
                                }
                            } else {
                                rssOnMain { Toast.makeText(context, "Download ready. Use Download history to save it.", Toast.LENGTH_LONG).show() }
                            }
                        } else if (state in listOf("failed", "error", "cancelled", "canceled")) {
                            rssOnMain { Toast.makeText(context, status.error ?: "Download failed.", Toast.LENGTH_LONG).show() }
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ poll(attempt + 1) }, 2000)
                        }
                    }
                }
            }
            poll(0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderApp(api: NativeHostApi, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var imageFormatExpanded by remember { mutableStateOf(false) }
    var imageSizeExpanded by remember { mutableStateOf(false) }
    var selectedImageFormat by remember { mutableStateOf("Original") }
    var selectedImageSize by remember { mutableStateOf("Original") }
    var selectedSocialFormat by remember { mutableStateOf("") }
    var selectedSocialQuality by remember { mutableStateOf("") }
    var socialFormatExpanded by remember { mutableStateOf(false) }
    var socialQualityExpanded by remember { mutableStateOf(false) }
        var movieTab by remember { mutableIntStateOf(0) }
    var movieSearch by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<NativeHostApi.Analysis?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var movies by remember { mutableStateOf<List<NativeHostApi.SearchResult>>(emptyList()) }
    var movieError by remember { mutableStateOf<String?>(null) }
    var themeDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var lastAnalyzed by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var settingsDialog by remember { mutableStateOf<String?>(null) }
    var historyJobs by remember { mutableStateOf<List<NativeHostApi.Job>?>(null) }
    var premiumText by remember { mutableStateOf<String?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    val downloadLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString("download_tree_uri", uri.toString()).apply()
                Toast.makeText(context, "Download location saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Unable to save download location", Toast.LENGTH_LONG).show()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fun readClipboard() {
            val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            if ((value.startsWith("http://") || value.startsWith("https://")) && value != url.trim()) {
                url = value
                analysis = null
                analysisError = null
                preview = false
                lastAnalyzed = ""
                analyzing = false
            }
        }
        readClipboard()
        val listener = ClipboardManager.OnPrimaryClipChangedListener { readClipboard() }
        clipboard.addPrimaryClipChangedListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readClipboard()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            clipboard.removePrimaryClipChangedListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun analyzeNow() {
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            analysisError = "Enter or paste a valid URL first."
            return
        }
        lastAnalyzed = target
        analysis = null
        preview = false
        analysisError = null
        analyzing = true
        api.analyze(target) { result ->
            rssOnMain {
                result.onSuccess { value ->
                    fun finish(options: List<NativeHostApi.MediaOption>) {
                        analysis = value.copy(mediaOptions = options)
                        preview = true
                        analyzing = false
                        options.firstOrNull { it.kind.equals("image", true) }?.format
                            ?.takeIf { it.isNotBlank() }
                            ?.let { selectedImageFormat = it.uppercase() }
                        options.firstOrNull()?.let {
                            selectedSocialFormat = it.format.ifBlank { "Original" }.uppercase()
                            selectedSocialQuality = it.quality.orEmpty()
                        }
                    }
                    if (value.mediaOptions.isNotEmpty() || value.requestId.isBlank()) {
                        finish(value.mediaOptions)
                    } else {
                        api.listMediaOptions(value.requestId) { optionsResult ->
                            rssOnMain {
                                optionsResult.onSuccess { finish(it) }
                                    .onFailure { finish(value.mediaOptions) }
                            }
                        }
                    }
                }.onFailure {
                    analysisError = it.message ?: "Analysis failed"
                    analyzing = false
                }
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab != 0) {
            analysis = null
            analysisError = null
            preview = false
            analyzing = false
        }
    }

    LaunchedEffect(tab, movieTab, movieSearch) {
        if (tab != 3) return@LaunchedEffect
        movieError = null
        val source = if (movieTab == 0) "tamil-movies" else "tamil-dubbed-movies"
        api.search(source, movieSearch) { result ->
            rssOnMain {
                result.onSuccess { movies = it }.onFailure { movieError = it.message ?: "Movie source error"; movies = emptyList() }
            }
        }
    }


    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val menuItems = listOf(
        RssMenuItem("Social Downloader", Icons.Default.Share) { tab = 0 },
        RssMenuItem("Audio Downloader", Icons.Default.Audiotrack) { tab = 1 },
        RssMenuItem("Image Downloader", Icons.Default.Image) { tab = 2 },
        RssMenuItem("Movies", Icons.Default.Movie) { tab = 3 },
        RssMenuItem("Settings", Icons.Default.Settings) { tab = 4 }
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RssSlideMenu(
                userName = prefs.getString("display_name", prefs.getString("name", null)),
                userEmail = prefs.getString("email", null),
                selectedTitle = menuItems[tab].title,
                items = menuItems.map { item ->
                    item.copy(onClick = {
                        item.onClick()
                        scope.launch { drawerState.close() }
                    })
                },
                logo = painterResource(com.riyaz.rssdownloader.R.drawable.rss_downloader_logo),
                companyName = RssBrand.COMPANY_NAME,
                companyWebsite = "www.rsscctvsolution.eu.cc",
                companyEmail = "rsscctvsolution@gmail.com",
                companyPhone = "077 115 5504 | 070 155 5504",
                appVersion = "v" + BuildConfig.VERSION_NAME
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (tab) { 0 -> "Social Downloader"; 1 -> "Audio Downloader"; 2 -> "Image Downloader"; 3 -> "Movies"; else -> "Settings" },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    menuItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title.removeSuffix(" Downloader")) }
                        )
                    }
                }
            }
        ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                    0 -> {
                        item {
                            Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    OutlinedTextField(
                                        value = url,
                                        onValueChange = { url = it.trim(); lastAnalyzed = ""; analysis = null; analysisError = null; preview = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Link, null) },
                                        label = { Text("Paste URL") }
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Button(onClick = { analyzeNow() }, modifier = Modifier.fillMaxWidth(), enabled = !analyzing) {
                                        Icon(Icons.Default.Search, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (analyzing) "Analyzing…" else "Analyze")
                                    }
                                    if (url.isNotBlank() && url.startsWith("http")) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(if (analysis != null) "Preview ready" else (analysisError ?: "Analyzing…"))
                                    }
                                }
                            }
                        }
                        if (preview) analysis?.let { result ->
                            item {
                                Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        result.thumbnailUrl?.let { thumb -> AsyncImage(model = thumb, contentDescription = result.title, modifier = Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Crop) }
                                        Text(result.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(result.normalizedUrl, color = MaterialTheme.colorScheme.onSurfaceVariant)
if (result.mediaOptions.isEmpty()) {
                                            Text("No download options returned by RSS Core.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            val formats = result.mediaOptions.map { it.format.ifBlank { "Original" }.uppercase() }.distinct()
                                            val formatOptions = result.mediaOptions.filter { it.format.ifBlank { "Original" }.uppercase() == selectedSocialFormat }
                                            val qualities = formatOptions.mapNotNull { it.quality?.takeIf { q -> q.isNotBlank() } }.distinct()
                                            val selected = formatOptions.firstOrNull { it.quality.orEmpty() == selectedSocialQuality } ?: formatOptions.firstOrNull() ?: result.mediaOptions.first()
                                            Box {
                                                OutlinedButton(onClick = { socialFormatExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                                    Text("Format: ${selected.format.ifBlank { "Original" }.uppercase()}")
                                                }
                                                DropdownMenu(expanded = socialFormatExpanded, onDismissRequest = { socialFormatExpanded = false }) {
                                                    formats.forEach { format ->
                                                        DropdownMenuItem(text = { Text(format) }, onClick = {
                                                            selectedSocialFormat = format
                                                            selectedSocialQuality = result.mediaOptions.firstOrNull { it.format.ifBlank { "Original" }.uppercase() == format }?.quality.orEmpty()
                                                            socialFormatExpanded = false
                                                        })
                                                    }
                                                }
                                            }
                                            if (qualities.isNotEmpty()) {
                                                Box {
                                                    OutlinedButton(onClick = { socialQualityExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                                        Text("Quality: ${selected.quality ?: "Available"}")
                                                    }
                                                    DropdownMenu(expanded = socialQualityExpanded, onDismissRequest = { socialQualityExpanded = false }) {
                                                        qualities.forEach { quality ->
                                                            DropdownMenuItem(text = { Text(quality) }, onClick = { selectedSocialQuality = quality; socialQualityExpanded = false })
                                                        }
                                                    }
                                                }
                                            }
                                            Button(
                                                onClick = { queueAndDeliver(context, api, prefs, result.requestId, selected.id, result.title) },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Download, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Download ${selected.format.ifBlank { "Original" }.uppercase()} • ${selected.quality ?: "Available"}")
                                            }
                                        }                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        item { Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Audio Downloader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); OutlinedTextField(value = url, onValueChange = { url = it.trim(); lastAnalyzed = ""; analysis = null; analysisError = null }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Link, null) }, label = { Text("Paste audio URL") });
Button(onClick = { analyzeNow() }, modifier = Modifier.fillMaxWidth(), enabled = !analyzing) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (analyzing) "Analyzing…" else "Analyze") }; if (analysis != null) { val audio = analysis!!.mediaOptions.filter { it.kind.equals("audio", true) || it.kind.equals("music", true) }; if (audio.isNotEmpty()) audio.forEach { option -> Button(onClick = { queueAndDeliver(context, api, prefs, analysis!!.requestId, option.id, analysis!!.title) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("${option.format.uppercase()} • ${option.quality ?: "Original"}") } } else Text("No authorized audio download option found.", color = MaterialTheme.colorScheme.onSurfaceVariant) } else if (url.isNotBlank()) Text(analysisError ?: "Analyzing…", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                    }
                    2 -> {
                        item {
                            Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(value = url, onValueChange = { url = it.trim(); lastAnalyzed = ""; analysis = null; analysisError = null }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Link, null) }, label = { Text("Paste image URL") })
Button(onClick = { analyzeNow() }, modifier = Modifier.fillMaxWidth(), enabled = !analyzing) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (analyzing) "Analyzing…" else "Analyze") }
                                    if (analysis != null) {
                                        val imageOptions = analysis!!.mediaOptions.filter { it.kind.equals("image", ignoreCase = true) }
                                        if (imageOptions.isNotEmpty()) {
                                            AsyncImage(model = analysis!!.thumbnailUrl ?: analysis!!.normalizedUrl, contentDescription = analysis!!.title, modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit)
                                            Text(analysis!!.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Box {
                                                OutlinedButton(onClick = { imageFormatExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Format: $selectedImageFormat") }
                                                DropdownMenu(expanded = imageFormatExpanded, onDismissRequest = { imageFormatExpanded = false }) {
                                                    imageOptions.map { it.format.ifBlank { "original" }.uppercase() }.distinct().forEach { format ->
                                                        DropdownMenuItem(text = { Text(format) }, onClick = { selectedImageFormat = format; imageFormatExpanded = false })
                                                    }
                                                }
                                            }
                                            Box {
                                                OutlinedButton(onClick = { imageSizeExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Size: $selectedImageSize") }
                                                DropdownMenu(expanded = imageSizeExpanded, onDismissRequest = { imageSizeExpanded = false }) {
                                                    DropdownMenuItem(text = { Text("Original size") }, onClick = { selectedImageSize = "Original"; imageSizeExpanded = false })
                                                }
                                            }
                                            val selected = imageOptions.firstOrNull { it.format.equals(selectedImageFormat, true) } ?: imageOptions.first()
                                            Button(onClick = { queueAndDeliver(context, api, prefs, analysis!!.requestId, selected.id, analysis!!.title) }, modifier = Modifier.fillMaxWidth()) {
                                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download Image")
                                            }
                                        } else Text("No downloadable image was found for this link.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (url.isNotBlank()) Text(analysisError ?: "Analyzing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    3 -> {
                        item {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(movieTab == 0, { movieTab = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Tamil") }
                                SegmentedButton(movieTab == 1, { movieTab = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Dubbed") }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = movieSearch,
                                onValueChange = { movieSearch = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                label = { Text("Search") }
                            )
                        }
                        movieError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                        items(movies) { movie ->
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(movie.title, fontWeight = FontWeight.Bold)
                                    val meta = listOfNotNull(movie.year?.toString(), movie.language, movie.releaseDate).joinToString(" • ")
                                    if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    movie.mediaOptions.forEach { option ->
                                        Button(
                                            onClick = { queueAndDeliver(context, api, prefs, movie.requestId ?: movie.id, option.id, movie.title) },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Download, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("${option.format.ifBlank { "Download" }.uppercase()} • ${option.quality ?: "Available"}")
                                        }
                                    }
                                }
                            }
                        }
                        if (movies.isEmpty() && movieError == null) item { Text("Loading catalogue…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    4 -> item { SettingsContent(prefs, { themeDialog = true }, { aboutDialog = true }) { action ->
                            when (action) {
                                "RSS Core account" -> settingsDialog = "Account: " + prefs.getString("email", "Not signed in")
                                "Download location" -> downloadLocationLauncher.launch(prefs.getString("download_tree_uri", null)?.let { Uri.parse(it) })
                                "Download history" -> api.listDownloads { r -> rssOnMain { r.onSuccess { historyJobs = it }.onFailure { premiumText = it.message ?: "Download history unavailable" } } }
                                "Premium" -> api.checkPremium(prefs.getString("email", "").orEmpty()) { r -> rssOnMain { r.onSuccess { premiumText = if (it) "Premium is active." else "Premium is not active." } .onFailure { premiumText = it.message ?: "Premium status unavailable" } } }
                                "Connection" -> premiumText = if (api.configured()) "RSS Core is configured at ${BuildConfig.RSS_HOST_BASE_URL}" else "RSS Core is not configured."
                                "Privacy Policy" -> openWebPage(context, "https://rsscore.cv/privacy")
                                "Terms & Conditions" -> openWebPage(context, "https://rsscore.cv/terms")
                                else -> settingsDialog = action
                            }
                        } }
                }
        }
    }
    }

    if (themeDialog) {
        val options = listOf("System default", "Light", "Dark")
        val current = prefs.getString("theme", "System default") ?: "System default"
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                prefs.edit().putString("theme", option).apply()
                                val mode = when (option) {
                                    "Light" -> AppCompatDelegate.MODE_NIGHT_NO
                                    "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
                                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                }
                                AppCompatDelegate.setDefaultNightMode(mode)
                                themeDialog = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (option == current) "● " else "○ ")
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Close") } }
        )
    }

    historyJobs?.let { jobs -> AlertDialog(onDismissRequest = { historyJobs = null }, title = { Text("Download history") }, text = { if (jobs.isEmpty()) Text("No downloads yet.") else LazyColumn { items(jobs) { job -> Text("${job.title ?: job.filename ?: job.jobId} • ${job.status}", modifier = Modifier.padding(vertical = 4.dp)) } } }, confirmButton = { TextButton(onClick = { historyJobs = null }) { Text("Close") } }) }
    premiumText?.let { msg -> AlertDialog(onDismissRequest = { premiumText = null }, title = { Text("RSS Core") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { premiumText = null }) { Text("Close") } }) }
    settingsDialog?.let { title ->
        AlertDialog(
            onDismissRequest = { settingsDialog = null },
            title = { Text(title) },
            text = {
                Text(
                    when (title) {
                        "RSS Core account" -> "Email: " + prefs.getString("email", "Not signed in")
                        "Connection" -> "Host: " + BuildConfig.RSS_HOST_BASE_URL + "\nStatus: " + if (api.configured()) "Configured" else "Not configured"
                        else -> "This setting is connected to RSS Core and the Android system."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { settingsDialog = null }) { Text("Close") } }
        )
    }
    if (aboutDialog) {
        AlertDialog(
            onDismissRequest = { aboutDialog = false },
            title = { Text("About") },
            text = { Text("RSS Downloader\n\n${RssBrand.COMPANY_NAME}") },
            confirmButton = { TextButton(onClick = { aboutDialog = false }) { Text("Close") } }
        )
    }
}

private fun openWebPage(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching {
        if (context.packageManager.resolveActivity(intent, 0) != null) context.startActivity(intent)
        else Toast.makeText(context, "No browser is available.", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(context, "Unable to open page.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SettingsContent(
    prefs: android.content.SharedPreferences,
    onTheme: () -> Unit,
    onAbout: () -> Unit,
    onSetting: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("RSS Downloader", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                RssSettingRow(Icons.Default.Storage, "Download location", "Choose where downloaded files are saved", Modifier.fillMaxWidth().clickable { onSetting("Download location") })
                Divider()
                RssSettingRow(Icons.Default.Download, "Download history", "View completed and queued downloads", Modifier.fillMaxWidth().clickable { onSetting("Download history") })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                RssSettingRow(Icons.Default.DarkMode, "Appearance", prefs.getString("theme", "System default"), Modifier.fillMaxWidth().clickable { onTheme() })
                Divider()
                RssSettingRow(Icons.Default.WorkspacePremium, "Premium", "Manage your RSS Downloader plan", Modifier.fillMaxWidth().clickable { onSetting("Premium") })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                RssSettingRow(Icons.Default.Info, "About", "RSS Downloader", Modifier.fillMaxWidth().clickable { onAbout() })
                Divider()
                RssSettingRow(Icons.Default.Description, "Privacy Policy", modifier = Modifier.fillMaxWidth().clickable { onSetting("Privacy Policy") })
                Divider()
                RssSettingRow(Icons.Default.Description, "Terms & Conditions", modifier = Modifier.fillMaxWidth().clickable { onSetting("Terms & Conditions") })
            }
        }

        Text(
            "Developing Ideas. Delivering Solutions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}