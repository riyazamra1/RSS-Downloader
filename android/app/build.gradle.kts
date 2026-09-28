plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.riyaz.rssdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riyaz.rssdownloader"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "3.2"
        buildConfigField("String", "RSS_HOST_BASE_URL", "\"https://rsscore.cv\"")
        buildConfigField("String", "RSS_HOST_ACCESS_TOKEN", "\"\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.0.21"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":rss-common"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
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
