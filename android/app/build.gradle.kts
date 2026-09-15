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
        versionCode = 2
        versionName = "2.0"
        buildConfigField("String", "RSS_HOST_BASE_URL", "\"\"")
        buildConfigField("String", "RSS_HOST_ACCESS_TOKEN", "\"\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
