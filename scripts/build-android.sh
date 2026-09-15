#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"

cd "$ROOT_DIR"

echo "== RSS Downloader Android build =="
echo "Root: $ROOT_DIR"

echo "Android: $ANDROID_DIR"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java/JDK is required. Install JDK 17+ in the OpenHands build environment." >&2
  exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
echo "Java: ${JAVA_VERSION:-unknown}"

if ! command -v npm >/dev/null 2>&1; then
  echo "ERROR: Node.js/npm is required to bundle android/runtime-entry.ts." >&2
  exit 1
fi

if [ -f package-lock.json ]; then
  npm ci
else
  npm install
fi

npm run typecheck

mkdir -p "$ANDROID_DIR/app/src/main/assets"
npx esbuild android/runtime-entry.ts \
  --bundle \
  --platform=browser \
  --format=iife \
  --outfile=android/app/src/main/assets/runtime.js

cd "$ANDROID_DIR"

if [ ! -x ./gradlew ]; then
  if ! command -v gradle >/dev/null 2>&1; then
    echo "ERROR: Gradle is not installed and android/gradlew is absent." >&2
    echo "Install Gradle 8.11.1 (or newer compatible Gradle) in OpenHands, then rerun this script." >&2
    exit 1
  fi

  echo "Generating Gradle wrapper 8.11.1..."
  gradle --no-daemon wrapper --gradle-version 8.11.1 --distribution-type bin
fi

chmod +x ./gradlew
./gradlew --no-daemon assembleDebug

APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "ERROR: Build reported success but APK was not found at $APK" >&2
  exit 1
fi

SHA256="$(sha256sum "$APK" | awk '{print $1}')"
SIZE="$(du -h "$APK" | awk '{print $1}')"

echo ""
echo "BUILD_SUCCESS=true"
echo "APK=$APK"
echo "SIZE=$SIZE"
echo "SHA256=$SHA256"
echo ""
echo "The APK is ready for the OpenHands/Cloudflare artifact upload step."
