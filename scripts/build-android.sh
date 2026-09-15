#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
GRADLE_VERSION="8.11.1"
GRADLE_HOME="$ROOT_DIR/.build-tools/gradle-$GRADLE_VERSION"

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
  GRADLE_CMD=""

  if command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD="$(command -v gradle)"
  else
    echo "System Gradle not found; bootstrapping Gradle $GRADLE_VERSION locally..."
    if ! command -v curl >/dev/null 2>&1; then
      echo "ERROR: curl is required to bootstrap Gradle $GRADLE_VERSION." >&2
      exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
      echo "ERROR: unzip is required to bootstrap Gradle $GRADLE_VERSION." >&2
      exit 1
    fi

    mkdir -p "$ROOT_DIR/.build-tools"
    if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
      TMP_ZIP="$ROOT_DIR/.build-tools/gradle-$GRADLE_VERSION-bin.zip"
      curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$TMP_ZIP"
      rm -rf "$GRADLE_HOME"
      unzip -q "$TMP_ZIP" -d "$ROOT_DIR/.build-tools"
      rm -f "$TMP_ZIP"
    fi
    GRADLE_CMD="$GRADLE_HOME/bin/gradle"
  fi

  echo "Generating Gradle wrapper $GRADLE_VERSION..."
  "$GRADLE_CMD" --no-daemon wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
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
