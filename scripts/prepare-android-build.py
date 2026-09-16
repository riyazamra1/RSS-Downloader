from pathlib import Path
import re

main = Path("android/app/src/main/java/com/riyaz/rssdownloader/MainActivity.kt")
s = main.read_text()

if "import androidx.appcompat.app.AppCompatDelegate" not in s:
    s = s.replace("import androidx.appcompat.app.AppCompatActivity", "import androidx.appcompat.app.AppCompatActivity\nimport androidx.appcompat.app.AppCompatDelegate")

s = s.replace(
    "    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)",
    "    override fun onCreate(savedInstanceState: Bundle?) {\n        val savedLight = getSharedPreferences(\"rss-downloader\", MODE_PRIVATE).getBoolean(\"light\", false)\n        AppCompatDelegate.setDefaultNightMode(if (savedLight) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)\n        super.onCreate(savedInstanceState)",
)
s = s.replace('        addView(button("⚙", 46) { showSettings() })', '        addView(iconButton(R.drawable.ic_rss_settings, 46) { showSettings() })')

start = s.index("    private fun buildBottomNav(): View = LinearLayout(this).apply {")
end = s.index("    private fun showTabOrderDialog()", start)
s = s[:start] + '''    private fun buildBottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(7), dp(8), dp(7))
        setBackgroundColor(surface())
        elevation = dp(10).toFloat()
        tabOrder.forEach { id ->
            val selected = id == currentTab
            addView(navItem(id, selected) { if (currentTab != id) { currentTab = id; showHome() } }, LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
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
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        alpha = 0.82f
        val icon = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_rss_settings)
            imageTintList = android.content.res.ColorStateList.valueOf(muted())
            contentDescription = "Settings"
        }
        addView(icon, LinearLayout.LayoutParams(dp(25), dp(25)))
        addView(text("Settings", 10, muted(), true))
        setOnClickListener { showSettings() }
    }

''' + s[end:]

s = re.sub(r'\n        hero\.addView\(text\("Paste a link\\nRSS handles the rest\..*?\)\)', '', s, count=1, flags=re.S)
s = re.sub(r'\n        hero\.addView\(text\("Copied HTTP\(S\) links are detected automatically while RSS Downloader is active\.".*?\)\)', '', s, count=1, flags=re.S)
s = s.replace(
    '        if (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return\n        val value = clip.getItemAt(0).coerceToText(this).toString().trim()',
    '        val item = clip.getItemAt(0)\n        val value = (item.text?.toString() ?: item.coerceToText(this).toString()).trim()',
)

if "private fun iconButton(" not in s:
    marker = "    private fun button(label: String, size: Int, onClick: () -> Unit)"
    helper = '''    private fun iconButton(iconRes: Int, size: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(iconRes)
        imageTintList = android.content.res.ColorStateList.valueOf(textColor())
        background = rounded(surface2(), 14)
        contentDescription = "Settings"
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(0, 0, dp(8), 0) }
    }

'''
    s = s.replace(marker, helper + marker)

main.write_text(s)

res = Path("android/app/src/main/res")
(res / "values-night").mkdir(parents=True, exist_ok=True)
(res / "values-night/colors.xml").write_text("<resources>\n    <color name=\"splash_background\">#070B16</color>\n</resources>\n")
(res / "values-night/styles.xml").write_text("<resources>\n    <style name=\"AppTheme\" parent=\"Theme.AppCompat.DayNight.NoActionBar\">\n        <item name=\"android:windowActionModeOverlay\">true</item>\n        <item name=\"android:windowBackground\">@color/splash_background</item>\n    </style>\n</resources>\n")
(res / "values-v31/styles.xml").write_text("<resources>\n    <style name=\"AppTheme\" parent=\"Theme.AppCompat.DayNight.NoActionBar\">\n        <item name=\"android:windowActionModeOverlay\">true</item>\n        <item name=\"android:windowSplashScreenBackground\">@color/splash_background</item>\n        <item name=\"android:windowSplashScreenAnimatedIcon\">@drawable/rss_downloader_splash_icon</item>\n    </style>\n</resources>\n")

print("Prepared Android source for release build")
