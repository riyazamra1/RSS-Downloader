from pathlib import Path
import re

main = Path("android/app/src/main/java/com/riyaz/rssdownloader/MainActivity.kt")
if not main.exists():
    raise SystemExit("MainActivity.kt is missing")
s = main.read_text(encoding="utf-8")

# This script only normalizes the checked-in legacy UI before release compilation.
# It is deliberately deterministic and ends with validation of the resulting source.
if "import androidx.appcompat.app.AppCompatDelegate" not in s:
    s = s.replace("import androidx.appcompat.app.AppCompatActivity", "import androidx.appcompat.app.AppCompatActivity\nimport androidx.appcompat.app.AppCompatDelegate")

s = s.replace("import android.content.ClipDescription\n", "")
s = s.replace("    private var lightMode = false\n", "    private var appearance = \"system\"\n")
s = s.replace("        super.onCreate(savedInstanceState)\n        // Remove legacy user-entered host values so an old accidental change can never redirect the app.\n        prefs.edit().remove(\"hostUrl\").remove(\"hostToken\").apply()\n        lightMode = prefs.getBoolean(\"light\", false)\n", "        appearance = prefs.getString(\"appearance\", null) ?: if (prefs.contains(\"light\")) if (prefs.getBoolean(\"light\", false)) \"light\" else \"dark\" else \"system\"\n        AppCompatDelegate.setDefaultNightMode(when (appearance) { \"light\" -> AppCompatDelegate.MODE_NIGHT_NO; \"dark\" -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })\n        super.onCreate(savedInstanceState)\n        prefs.edit().remove(\"hostUrl\").remove(\"hostToken\").remove(\"light\").apply()\n")

# Modern top-bar settings icon.
s = s.replace('addView(button("⚙", 46) { showSettings() })', 'addView(iconButton(R.drawable.ic_rss_settings, 46, "Settings") { showSettings() })')

# Replace the text-only bottom navigation with icon navigation.
start = s.index("    // Clean fixed navigation:") if "    // Clean fixed navigation:" in s else s.index("    private fun buildBottomNav(): View")
end = s.index("    private fun showTabOrderDialog()", start)
nav = '''    private fun buildBottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(7), dp(8), dp(7))
        setBackgroundColor(surface())
        elevation = dp(10).toFloat()
        tabOrder.forEach { id ->
            addView(navItem(id, id == currentTab) { currentTab = id; showHome() }, LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        addView(navSettingsItem(), LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        layoutParams = LinearLayout.LayoutParams(-1, dp(76))
    }

    private fun navItem(id: String, selected: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(5), dp(4), dp(4))
        background = rounded(if (selected) accent() else Color.TRANSPARENT, 16)
        alpha = if (selected) 1f else 0.82f
        val icon = ImageView(this@MainActivity).apply {
            setImageResource(when (id) { TabOrder.SOCIAL -> R.drawable.ic_rss_download; TabOrder.TAMIL -> R.drawable.ic_rss_movie; else -> R.drawable.ic_rss_dubbed })
            imageTintList = android.content.res.ColorStateList.valueOf(if (selected) Color.WHITE else muted())
            contentDescription = tabLabel(id)
        }
        addView(icon, LinearLayout.LayoutParams(dp(25), dp(25)))
        addView(text(tabShortLabel(id), 10, if (selected) Color.WHITE else muted(), true))
        setOnClickListener { onClick() }
        setOnLongClickListener { showTabOrderDialog(); true }
    }

    private fun navSettingsItem(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(5), dp(4), dp(4))
        val icon = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_rss_settings)
            imageTintList = android.content.res.ColorStateList.valueOf(muted())
            contentDescription = "Settings"
        }
        addView(icon, LinearLayout.LayoutParams(dp(25), dp(25)))
        addView(text("Settings", 10, muted(), true))
        setOnClickListener { showSettings() }
    }

'''
s = s[:start] + nav + s[end:]

# Remove instructional hero copy: the behavior is automatic and needs no permanent label.
s = re.sub(r'\n        hero\.addView\(text\("Paste a link\\nRSS handles the rest\..*?\)\)', '', s, count=1, flags=re.S)
s = re.sub(r'\n        hero\.addView\(text\("Copied HTTP\(S\) links are detected automatically while RSS Downloader is active\.".*?\)\)', '', s, count=1, flags=re.S)

# Movie cards must preserve backend thumbnails and request IDs instead of discarding them.
old_card = re.search(r'    private fun movieCard\(title: String, year: Int, tab: String\): View = panel\(\)\.apply \{.*?\n    \}\n\n    private fun analyzeUrl', s, flags=re.S)
if old_card:
    new_card = '''    private fun movieCard(item: NativeHostApi.SearchResult, tab: String): View = panel().apply {
        orientation = LinearLayout.VERTICAL
        val image = ImageView(this@MainActivity).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = item.title }
        addView(image, LinearLayout.LayoutParams(-1, dp(145)))
        loadImage(item.thumbnailUrl, image)
        if (item.thumbnailUrl.isNullOrBlank()) loadWikipediaPoster(item.title, image)
        addView(text(item.title, 15, textColor(), true).apply { setPadding(dp(13), dp(7), dp(13), 0) })
        addView(text("${item.year ?: 2026} • Authorized provider options", 11, muted(), false).apply { setPadding(dp(13), 0, dp(13), dp(7)) })
        addView(secondaryButton("Get options") { selectMovie(item, tab) }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(13), 0, dp(13), dp(13)) })
    }

    private fun selectMovie(item: NativeHostApi.SearchResult, tab: String) {
        val requestId = item.requestId
        if (requestId == null) { toast("RSS Core did not return a request ID for this movie."); return }
        content.removeAllViews()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        box.addView(text(tabLabel(tab), 10, accent(), true))
        box.addView(text(item.title, 24, textColor(), true).apply { setPadding(0, dp(8), 0, dp(8)) })
        val image = ImageView(this).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = item.title }
        box.addView(image, LinearLayout.LayoutParams(-1, dp(210)))
        loadImage(item.thumbnailUrl, image)
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        box.addView(options)
        content.addView(ScrollView(this).apply { addView(box) })
        if (item.mediaOptions.isNotEmpty()) renderOptions(options, requestId, item.mediaOptions)
        else api.listMediaOptions(requestId) { result -> runOnUiThread { result.onSuccess { renderOptions(options, requestId, it) }.onFailure { options.addView(text(it.message ?: "Failed to load quality options.", 13, muted(), false)) } } }
    }

    private fun analyzeUrl'''
    s = s[:old_card.start()] + new_card + s[old_card.end():]

# Change latest-movie rendering to retain the complete SearchResult object.
s = s.replace('items.forEach { item ->\n                    grid.addView(movieCard(item.title, item.year ?: 2026, tab), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })\n                }', 'items.forEach { item ->\n                    grid.addView(movieCard(item, tab), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })\n                }')

# Search result selection uses the same movie details flow.
s = re.sub(r'                    row\.addView\(primaryButton\("Select"\) \{.*?                    \}\)\n                    panel\.addView\(row\)', '                    row.addView(primaryButton("Select") { selectMovie(item, tab) })\n                    panel.addView(row)', s, count=1, flags=re.S)

# Settings: three persistent appearance modes.
old_setting = re.search(r'        box\.addView\(settingRow\("Appearance".*?\n        box\.addView\(settingRow\("Auto-paste copied URL"', s, flags=re.S)
if old_setting:
    s = s[:old_setting.start()] + '''        box.addView(appearanceRow())
        box.addView(settingRow("Auto-paste copied URL", "Detect copied HTTP(S) URLs", prefs.getBoolean("clipboard", true))''' + s[old_setting.end():]
    # The replacement above retains the original lambda tail after the second setting label.

# If the regex-based replacement left an invalid duplicate, normalize the exact common form.
s = s.replace('settingRow("Auto-paste copied URL", "Detect a copied HTTP(S) URL when RSS Downloader is active", prefs.getBoolean("clipboard", true))', 'settingRow("Auto-paste copied URL", "Detect copied HTTP(S) URLs", prefs.getBoolean("clipboard", true))')

if "private fun appearanceRow()" not in s:
    marker = '    private fun moveTab(index: Int, delta: Int)'
    helper = '''    private fun appearanceRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        background = rounded(surface(), 16)
        addView(text("Appearance", 15, textColor(), true))
        addView(text("Choose the app theme", 11, muted(), false).apply { setPadding(0, dp(3), 0, dp(10)) })
        val row = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (value, label) ->
            row.addView(RadioButton(this@MainActivity).apply {
                text = label
                textSize = 12f
                setTextColor(textColor())
                isChecked = appearance == value
                setOnClickListener { appearance = value; prefs.edit().putString("appearance", value).apply(); applyTheme(); recreate() }
            }, RadioGroup.LayoutParams(0, dp(48), 1f))
        }
        addView(row)
    }

'''
    s = s.replace(marker, helper + marker)

# Clipboard: accept URI or text clips rather than requiring a plain-text MIME declaration.
s = s.replace('        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n        val clip = cm.primaryClip ?: return\n        if (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return\n        val value = clip.getItemAt(0).coerceToText(this).toString().trim()', '        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n        val clip = cm.primaryClip ?: return\n        val item = clip.getItemAt(0)\n        val value = (item.text?.toString() ?: item.coerceToText(this).toString()).trim()')

# Theme helpers: explicit Light/Dark/System.
s = re.sub(r'    private fun applyTheme\(\) =.*?\n    private fun bg\(\) =', '    private fun applyTheme() {\n        AppCompatDelegate.setDefaultNightMode(when (appearance) { "light" -> AppCompatDelegate.MODE_NIGHT_NO; "dark" -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })\n        if (::root.isInitialized) { root.setBackgroundColor(bg()); window.statusBarColor = bg(); window.navigationBarColor = bg() }\n    }\n    private fun isLight(): Boolean = when (appearance) { "light" -> true; "dark" -> false; else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES }\n    private fun bg() =', s, count=1, flags=re.S)
s = s.replace('Color.parseColor(if (lightMode)', 'Color.parseColor(if (isLight())')
s = s.replace('    private fun button(label: String, size: Int, onClick: () -> Unit)', '    private fun iconButton(iconRes: Int, size: Int, description: String, onClick: () -> Unit) = ImageButton(this).apply { setImageResource(iconRes); imageTintList = android.content.res.ColorStateList.valueOf(textColor()); background = rounded(surface2(), 14); contentDescription = description; setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { onClick() }; layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(2), 0, dp(2), 0) } }\n    private fun button(label: String, size: Int, onClick: () -> Unit)')

# Modern reorder controls without text arrows.
s = s.replace('secondaryButton("↑")', 'iconButton(android.R.drawable.arrow_up_float, 44, "Move up")')
s = s.replace('secondaryButton("↓")', 'iconButton(android.R.drawable.arrow_down_float, 44, "Move down")')

# Remove any accidental legacy host setting references from the UI.
s = s.replace('prefs.edit().remove("hostUrl").remove("hostToken").apply()', 'prefs.edit().remove("hostUrl").remove("hostToken").apply()')

main.write_text(s, encoding="utf-8")

# Validation gates: fail the release before Gradle if the unwanted legacy UI remains.
for forbidden in (
    'button("⚙"',
    'secondaryButton("↑"',
    'secondaryButton("↓"',
    'Paste a link.\\nRSS handles the rest.',
    'Copied HTTP(S) links are detected automatically while RSS Downloader is active.',
):
    if forbidden in s:
        raise SystemExit(f"Legacy UI remains: {forbidden}")

for required in ('AppCompatDelegate', 'appearanceRow()', 'iconButton(', 'selectMovie(item, tab)'):
    if required not in s:
        raise SystemExit(f"Release source normalization failed: {required}")

print("Prepared Android source with modern UI, persistent Light/Dark/System theme, robust clipboard handling, and movie-result selection.")
