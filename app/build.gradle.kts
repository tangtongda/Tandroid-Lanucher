import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Secrets and per-machine settings live in local.properties (git-ignored), e.g.
//   tmdb.token=<TMDB API Read Access Token>
//   tmdb.apiBase=https://api.themoviedb.org/3/     (optional: a reverse proxy URL)
//   tmdb.imageBase=https://image.tmdb.org/t/p/     (optional: a reverse proxy URL)
//   tmdb.region=HK          (optional: region used to match streaming apps to TMDB "watch providers")
//   youtube.apiKey=<key>    (optional: YouTube Data API v3, for YouTube's own trending videos)
//   debug.demoApps=true     (optional: debug builds add fake Netflix/Spotify/… tiles for testing)
//   weather.city=上海        (optional: weather location; or weather.lat / weather.lon. Default: from IP)
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String, default: String = ""): String = localProps.getProperty(key, default)

android {
    namespace = "com.tangtongda.tlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tangtongda.tlauncher"
        minSdk = 29          // Sony A80J: Google TV / Android 10
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"

        buildConfigField("String", "TMDB_TOKEN", "\"${localProp("tmdb.token")}\"")
        buildConfigField("String", "TMDB_API_BASE", "\"${localProp("tmdb.apiBase", "https://api.themoviedb.org/3/")}\"")
        buildConfigField("String", "TMDB_IMAGE_BASE", "\"${localProp("tmdb.imageBase", "https://image.tmdb.org/t/p/")}\"")
        buildConfigField("String", "TMDB_REGION", "\"${localProp("tmdb.region", "HK")}\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${localProp("youtube.apiKey")}\"")
        buildConfigField("String", "WEATHER_CITY", "\"${localProp("weather.city")}\"")
        buildConfigField("String", "WEATHER_LAT", "\"${localProp("weather.lat")}\"")
        buildConfigField("String", "WEATHER_LON", "\"${localProp("weather.lon")}\"")
    }

    buildTypes {
        // Run ▶ in Android Studio = debug: the debug.* switches from local.properties apply.
        debug {
            buildConfigField("boolean", "DEMO_APPS", localProp("debug.demoApps", "false").toBoolean().toString())
            // Pretend to be another TV brand (sony, hisense, tcl, xiaomi, samsung, lg, philips, sharp, skyworth, google)
            buildConfigField("String", "DEBUG_TV_BRAND", "\"${localProp("debug.tvBrand")}\"")
        }
        // ./gradlew assembleRelease = the APK to install / share: test switches are always off,
        // whatever local.properties says. Signed with the local debug key so it installs over
        // earlier builds (same signature) without setting up a release keystore.
        release {
            buildConfigField("boolean", "DEMO_APPS", "false")
            buildConfigField("String", "DEBUG_TV_BRAND", "\"\"")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
