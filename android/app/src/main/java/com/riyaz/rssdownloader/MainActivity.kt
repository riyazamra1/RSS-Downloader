package com.riyaz.rssdownloader

import android.os.Bundle
import android.content.Context
import android.content.ClipboardManager
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloaderApp(api: NativeHostApi, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var movieTab by remember { mutableIntStateOf(0) }
    var movieSearch by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<NativeHostApi.Analysis?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var movies by remember { mutableStateOf<List<NativeHostApi.SearchResult>>(emptyList()) }
    var movieError by remember { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<NativeHostApi.SearchResult?>(null) }
    var themeDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var lastAnalyzed by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var settingsDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            if ((value.startsWith("http://") || value.startsWith("https://")) && value != url) url = value
            kotlinx.coroutines.delay(700)
        }
    }

    DisposableEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fun readClipboard() {
            val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            if (value.startsWith("http://") || value.startsWith("https://")) url = value
        }
        readClipboard()
        val listener = ClipboardManager.OnPrimaryClipChangedListener { readClipboard() }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose { clipboard.removePrimaryClipChangedListener(listener) }
    }

    LaunchedEffect(url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            kotlinx.coroutines.delay(500)
            if (url == lastAnalyzed) return@LaunchedEffect
            lastAnalyzed = url
            analysis = null
            analysisError = null
            api.analyze(url) { result ->
                result.onSuccess { analysis = it; preview = true }.onFailure { analysisError = it.message ?: "Analysis failed" }
            }
        }
    }

    LaunchedEffect(tab, movieTab, movieSearch) {
        if (tab != 1) return@LaunchedEffect
        movieError = null
        val source = if (movieTab == 0) "tamil-movies" else "tamil-dubbed-movies"
        api.search(source, movieSearch) { result ->
            result.onSuccess { movies = it }.onFailure { movieError = it.message ?: "Movie source error"; movies = emptyList() }
        }
    }

    BackHandler(enabled = selectedMovie != null) { selectedMovie = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(tab == 0, { tab = 0; selectedMovie = null }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Social") })
                NavigationBarItem(tab == 1, { tab = 1; selectedMovie = null }, icon = { Icon(Icons.Default.Movie, null) }, label = { Text("Movies") })
                NavigationBarItem(tab == 2, { tab = 2; selectedMovie = null }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedMovie != null) {
                item { MovieDetails(selectedMovie!!, api) { selectedMovie = null } }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(com.riyaz.rssdownloader.R.drawable.rss_downloader_logo),
                            contentDescription = "RSS Downloader",
                            modifier = Modifier.size(46.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (tab) { 0 -> "Social Downloader"; 1 -> "Movies"; else -> "Settings" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                when (tab) {
                    0 -> {
                        item {
                            Card(shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    OutlinedTextField(
                                        value = url,
                                        onValueChange = { url = it.trim(); lastAnalyzed = "" },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Link, null) },
                                        label = { Text("Paste URL") }
                                    )
                                    if (url.isNotBlank() && url.startsWith("http")) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(if (analysis != null) "Preview ready" else (analysisError ?: "Analyzing…"))
                                    }
                                }
                            }
                        }
                        if (preview) analysis?.let { result ->
                            item {
                                Card(shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(result.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(result.normalizedUrl, color = MaterialTheme.colorScheme.onSurfaceVariant)
OutlinedButton(onClick = {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.normalizedUrl))) }
        .onFailure { Toast.makeText(context, "Unable to open source", Toast.LENGTH_SHORT).show() }
}, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Open Source")
}
if (result.mediaOptions.isEmpty()) Text("No authorized download options were returned for this link.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        result.mediaOptions.forEach { option ->
                                            Button(
                                                onClick = {
                                                    api.createDownload(result.requestId, option.id) {
                                                        if (it.isSuccess) Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Download, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("${option.format.uppercase()} • ${option.quality ?: "Available"}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
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
                            Card(Modifier.fillMaxWidth().clickable { selectedMovie = movie }, shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(movie.title, fontWeight = FontWeight.Bold)
                                    val meta = listOfNotNull(movie.year?.toString(), movie.language, movie.releaseDate).joinToString(" • ")
                                    if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Details  ›", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                        if (movies.isEmpty() && movieError == null) item { Text("Loading catalogue…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    else -> item { SettingsContent(prefs, api, { themeDialog = true }, { aboutDialog = true }, { settingsDialog = it }) }
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

    settingsDialog?.let { title ->
        AlertDialog(
            onDismissRequest = { settingsDialog = null },
            title = { Text(title) },
            text = { Text("RSS Downloader settings") },
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

@Composable
private fun MovieDetails(movie: NativeHostApi.SearchResult, api: NativeHostApi, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ Movies") }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val meta = listOfNotNull(movie.year?.toString(), movie.language, movie.releaseDate, movie.runtime, movie.director).joinToString(" • ")
                if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                movie.synopsis?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(top = 12.dp)) }
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Quality", fontWeight = FontWeight.Bold)
                if (movie.mediaOptions.isEmpty()) {
                    Text("No authorized download options available.")
                } else movie.mediaOptions.forEach { option ->
                    Button(
                        onClick = {
                            api.createDownload(movie.requestId ?: movie.id, option.id) {
                                if (it.isSuccess) Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${option.format.uppercase()} • ${option.quality ?: "Available"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    prefs: android.content.SharedPreferences,
    api: NativeHostApi,
    onTheme: () -> Unit,
    onAbout: () -> Unit,
    onSetting: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(shape = RoundedCornerShape(18.dp)) {
            Column {
                RssSettingRow(Icons.Default.Cloud, "RSS Core account", "Account and session")
                Divider()
                RssSettingRow(Icons.Default.Storage, "Download location", "Device storage", Modifier.clickable { onSetting("Download location") })
                Divider()
                RssSettingRow(Icons.Default.Download, "Download history", "Queued and completed", Modifier.clickable { onSetting("Download history") })
            }
        }
        Card(shape = RoundedCornerShape(18.dp)) {
            Column {
                RssSettingRow(Icons.Default.DarkMode, "Theme", prefs.getString("theme", "System default"), Modifier.clickable { onTheme() })
                Divider()
                RssSettingRow(Icons.Default.WorkspacePremium, "Premium", "RSS Core entitlement", Modifier.clickable { onSetting("Premium") })
                Divider()
                RssSettingRow(Icons.Default.Cloud, "Connection", if (api.configured()) "RSS Core connected" else "Not configured", Modifier.clickable { onSetting("Connection") })
            }
        }
        Card(shape = RoundedCornerShape(18.dp)) {
            Column {
                RssSettingRow(Icons.Default.Info, "About", "RSS Downloader", Modifier.clickable { onAbout() })
                Divider()
                RssSettingRow(Icons.Default.Description, "Privacy Policy", modifier = Modifier.clickable { onSetting("Privacy Policy") })
                Divider()
                RssSettingRow(Icons.Default.Description, "Terms & Conditions", modifier = Modifier.clickable { onSetting("Terms & Conditions") })
            }
        }
        Text("${RssBrand.SHORT_NAME} • ${RssBrand.COMPANY_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
