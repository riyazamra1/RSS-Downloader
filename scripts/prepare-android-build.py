from pathlib import Path
import re

main = Path("android/app/src/main/java/com/riyaz/rssdownloader/MainActivity.kt")
s = main.read_text(encoding="utf-8")

# Release-only compatibility normalization. The app is built from this exact
# normalized source, and the gates below prevent known-bad legacy UI from shipping.
if 'private fun iconButton(' not in s:
    marker = '    private fun button(label: String, size: Int, onClick: () -> Unit)'
    helper = '''    private fun iconButton(iconRes: Int, size: Int, description: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(iconRes)
        imageTintList = android.content.res.ColorStateList.valueOf(textColor())
        background = rounded(surface2(), 14)
        contentDescription = description
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(2), 0, dp(2), 0) }
    }

'''
    s = s.replace(marker, helper + marker)

s = s.replace('addView(button("⚙", 46) { showSettings() })', 'addView(iconButton(R.drawable.ic_rss_settings, 46, "Settings") { showSettings() })')

# Modern icon navigation; tabs remain reorderable and persisted.
start = s.index("    private fun buildBottomNav(): View")
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

# Reorder controls use real icons rather than text arrows.
s = s.replace('secondaryButton("↑")', 'iconButton(android.R.drawable.arrow_up_float, 44, "Move up")')
s = s.replace('secondaryButton("↓")', 'iconButton(android.R.drawable.arrow_down_float, 44, "Move down")')

# Keep the hero compact. Use exact Kotlin source replacements rather than fragile
# regexes around nested apply{} blocks.
s = s.replace('        hero.addView(text("Paste a link.\\nRSS handles the rest.", 34, textColor(), true).apply { setPadding(0, dp(4), 0, dp(8)) })\\n', '')
s = s.replace('        hero.addView(text("Copied HTTP(S) links are detected automatically while RSS Downloader is active.", 13, muted(), false))\\n', '')

# Clipboard handling: accept URI clips and ordinary text clips.
s = s.replace('        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n        val clip = cm.primaryClip ?: return\n        if (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return\n        val value = clip.getItemAt(0).coerceToText(this).toString().trim()', '        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n        val clip = cm.primaryClip ?: return\n        val item = clip.getItemAt(0)\n        val value = (item.text?.toString() ?: item.coerceToText(this).toString()).trim()')

# Preserve real movie search result metadata and thumbnails in the UI.
old = re.search(r'    private fun movieCard\(title: String, year: Int, tab: String\): View = panel\(\)\.apply \{.*?\n    \}\n\n    private fun analyzeUrl', s, flags=re.S)
if old:
    replacement = '''    private fun movieCard(item: NativeHostApi.SearchResult, tab: String): View = panel().apply {
        orientation = LinearLayout.VERTICAL
        val image = ImageView(this@MainActivity).apply { setImageResource(R.drawable.rss_downloader_logo); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = item.title }
        addView(image, LinearLayout.LayoutParams(-1, dp(145)))
        loadImage(item.thumbnailUrl, image)
        if (item.thumbnailUrl.isNullOrBlank()) loadWikipediaPoster(item.title, image)
        addView(text(item.title, 15, textColor(), true).apply { setPadding(dp(13), dp(7), dp(13), 0) })
        addView(text("${item.year ?: 2026} • Authorized provider options", 11, muted(), false).apply { setPadding(dp(13), 0, dp(13), dp(7)) })
        addView(secondaryButton("Get options") { movieSearch(tab, item.title) }, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(13), 0, dp(13), dp(13)) })
    }

    private fun analyzeUrl'''
    s = s[:old.start()] + replacement + s[old.end():]

s = s.replace('items.forEach { item ->\n                    grid.addView(movieCard(item.title, item.year ?: 2026, tab), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })\n                }', 'items.forEach { item ->\n                    grid.addView(movieCard(item, tab), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })\n                }')

# Build-time guard: fail only if the legacy source constructs remain.
for forbidden in (
    'button("⚙"',
    'secondaryButton("↑"',
    'secondaryButton("↓"',
    'hero.addView(text("Paste a link.',
    'hero.addView(text("Copied HTTP(S) links are detected automatically',
):
    if forbidden in s:
        raise SystemExit(f"Legacy UI remains: {forbidden}")


main.write_text(s, encoding="utf-8")
print("Android release source normalized and validated.")
