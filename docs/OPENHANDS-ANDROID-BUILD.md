# OpenHands Android Build

This repository can be built outside GitHub Actions, which is useful when GitHub Actions billing is unavailable.

## Build environment

Required:

- JDK 17 or newer
- Node.js and npm
- Android SDK with an installed API level 35 platform and build-tools
- Gradle 8.11.1 or newer compatible Gradle available as `gradle`

The repository intentionally does not require a committed Gradle wrapper. The build script generates a local Gradle wrapper when needed.

## Build

From the repository root:

```bash
chmod +x scripts/build-android.sh
./scripts/build-android.sh
```

The script:

1. Installs Node dependencies.
2. Runs the TypeScript typecheck.
3. Bundles `android/runtime-entry.ts` into `android/app/src/main/assets/runtime.js`.
4. Generates a local Gradle 8.11.1 wrapper if `android/gradlew` is missing.
5. Runs `assembleDebug`.
6. Prints the APK path, size, and SHA-256 checksum.

APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## OpenHands job prompt

Use the following task in the OpenHands workspace after cloning `riyazamra1/RSS-Downloader`:

```text
Build the RSS Downloader Android app from the repository source.

1. Clone/use the current repository and checkout the requested commit or main branch.
2. Ensure JDK 17+, Node.js/npm, Android SDK API 35, and compatible build-tools are available.
3. Run: chmod +x scripts/build-android.sh
4. Run: ./scripts/build-android.sh
5. If the build fails, diagnose and fix only actual build/runtime issues in the repository, then rerun the build.
6. Do not use GitHub Actions.
7. Do not add paid services or dependencies.
8. Do not change the RSS Downloader package ID: com.riyaz.rssdownloader.
9. On success, preserve the generated APK at android/app/build/outputs/apk/debug/app-debug.apk and report its SHA-256 checksum.
10. Do not claim success unless the APK file exists and assembleDebug exits successfully.
```
