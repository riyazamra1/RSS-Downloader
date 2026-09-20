from pathlib import Path
import re

main = Path("android/app/src/main/java/com/riyaz/rssdownloader/MainActivity.kt")
s = main.read_text(encoding="utf-8")

# Release-only compatibility normalization. This script must be idempotent:
# the checked-in source may already contain the modern navigation implementation.
if "private fun iconButton(" not in s:
    marker = "    private fun button(label: String, size: Int, onClick: () -> Unit)"
    if marker in s:
        helper = """    private fun iconButton(iconRes: Int, size: Int, description: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(iconRes)
        imageTintList = android.content.res.ColorStateList.valueOf(textColor())
        background = rounded(surface2(), 14)
        contentDescription = description
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(2), 0, dp(2), 0) }
    }

"""
        s = s.replace(marker, helper + marker)

s = s.replace(
    'addView(button("⚙", 46) { showSettings() })',
    'addView(iconButton(R.drawable.ic_rss_settings, 46, "Settings") { showSettings() })'
)

# Only normalize the legacy navigation when its old tab-order implementation is
# actually present. Newer checked-in MainActivity versions must not be reparsed.
if "tabOrder.forEach" in s and "private fun navItem(" not in s:
    start = s.find("    private fun buildBottomNav(): View")
    end_marker = "    private fun showTabOrderDialog()"
    end = s.find(end_marker, start)
    if start >= 0 and end >= 0:
        nav = '''    private fun buildBottomNav(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(7), dp(8), dp(7))
        setBackgroundColor(surface())
        elevation = dp(10).toFloat()
        tabOrder.forEach { id ->
            addView(navItem(id, id == currentTab) { currentTab = id; showHome() },
                LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
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
            setImageResource(when (id) {
                TabOrder.SOCIAL -> R.drawable.ic_rss_download
                TabOrder.TAMIL -> R.drawable.ic_rss_movie
                else -> R.drawable.ic_rss_dubbed
            })
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

# Reorder controls use real icons when the old text-arrow controls exist.
s = s.replace('secondaryButton("↑")', 'iconButton(android.R.drawable.arrow_up_float, 44, "Move up")')
s = s.replace('secondaryButton("↓")', 'iconButton(android.R.drawable.arrow_down_float, 44, "Move down")')

# Accept URI clips and ordinary text clips. This is intentionally a no-op if the
# source already contains the improved clipboard implementation.
legacy_clip = '''        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        if (cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return
        val value = clip.getItemAt(0).coerceToText(this).toString().trim()'''
modern_clip = '''        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        val item = clip.getItemAt(0)
        val value = (item.text?.toString() ?: item.coerceToText(this).toString()).trim()'''
s = s.replace(legacy_clip, modern_clip)

# Build-time guard: never ship the known legacy UI constructs.
for forbidden in (
    'button("⚙"',
    'secondaryButton("↑"',
    'secondaryButton("↓"',
):
    if forbidden in s:
        raise SystemExit(f"Legacy UI remains: {forbidden}")

main.write_text(s, encoding="utf-8")
print("Android release source normalized and validated.")
