from pathlib import Path

# Release builds must compile the checked-in Android source exactly as authored.
# This script is intentionally validation-only; it must never rewrite application code.
required = [
    Path("android/app/src/main/AndroidManifest.xml"),
    Path("android/app/src/main/java/com/riyaz/rssdownloader/MainActivity.kt"),
    Path("android/app/src/main/java/com/riyaz/rssdownloader/NativeHostApi.kt"),
    Path("android/app/src/main/java/com/riyaz/rssdownloader/TabOrder.kt"),
]
missing = [str(path) for path in required if not path.exists()]
if missing:
    raise SystemExit("Missing Android source files: " + ", ".join(missing))

main = required[1].read_text(encoding="utf-8")
for forbidden in (
    "Paste a link.\\nRSS handles the rest.",
    "Copied HTTP(S) links are detected automatically while RSS Downloader is active.",
):
    if forbidden in main:
        raise SystemExit(f"Unwanted UI copy remains in MainActivity.kt: {forbidden}")

if 'button("⚙"' in main or 'secondaryButton("↑"' in main or 'secondaryButton("↓"' in main:
    raise SystemExit("Legacy emoji/text-only controls remain in MainActivity.kt")

if 'hostUrl' in main or 'hostToken' in main:
    raise SystemExit("User-editable host settings must not be present in MainActivity.kt")

print("Android source validation passed; no source rewriting performed.")
