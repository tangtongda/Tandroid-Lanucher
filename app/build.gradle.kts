import java.time.LocalDate
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
        versionCode = 9
        versionName = "0.7.1"

        // Shown under 设置 → 关于.
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
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
            // R8: strips unused code and resources and optimises the rest. Smaller APK, faster
            // start-up on slow TV CPUs. Resources looked up by name are kept in res/raw/keep.xml.
            isMinifyEnabled = true
            isShrinkResources = true
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
    // Applies the baseline profiles (ours in src/main/baseline-prof.txt + Compose's own) when the
    // APK is sideloaded, so hot code is precompiled instead of interpreted on first use.
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
