plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.riyaz.rssdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riyaz.rssdownloader"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "3.1"
        buildConfigField("String", "RSS_HOST_BASE_URL", "\"https://rsscore.cv\"")
        buildConfigField("String", "RSS_HOST_ACCESS_TOKEN", "\"\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.gms:play-services-ads:24.6.0")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
}

val syncRssDownloaderLogo by tasks.registering(Copy::class) {
    from(rootProject.file("../rss-downloader-logo.png"))
    into(layout.buildDirectory.dir("generated/rssLogo/res/drawable"))
    rename { "rss_downloader_logo.png" }
}

tasks.named("preBuild").configure { dependsOn(syncRssDownloaderLogo) }
android.sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/rssLogo/res"))
