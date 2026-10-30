package com.tangtongda.tlauncher.data

import android.os.Build
import com.tangtongda.tlauncher.BuildConfig

/**
 * 系统应用 folder: the TV maker's pre-installed extras (国行 Sony ships 视频, 音乐, 相册, 应用中心,
 * 乐播投屏, 生活空间装饰, BRAVIA 通知, 帮助…). They're system apps that aren't a recognisable
 * service, so they go into one folder tile instead of crowding the home grid.
 *
 * Stays on the home grid even if pre-installed: real services (Netflix, YouTube, 奇异果, 云视听极光,
 * B站, Spotify…), the Play Store, and the TV's own Settings. The user can move any app in or out
 * from the long-press menu; that choice wins.
 */
private val brandedService = Regex(
    "netflix|disney|amazonvideo|primevideo|prime video|hbo|wbd\\.stream|appletv|apple tv|youtube|" +
        "iqiyi|qiyi|gitv|爱奇艺|奇异果|ktcp|qqlive|wetv|腾讯视频|云视听|youku|优酷|酷喵|hunantv|mgtv|芒果|" +
        "bili|哔哩|bbll|spotify|cloudmusic|网易云|qqmusic|kugou|酷狗|tidal|plex|kodi|xbmc|jellyfin|vlc",
    RegexOption.IGNORE_CASE,
)

private val keepOnHome = setOf(
    "com.android.vending", // Google Play Store
    "com.google.android.tvrecommendations",
)

fun isFolderCandidate(app: AppInfo, settingsPackage: String?): Boolean =
    app.isSystem &&
        app.packageName != settingsPackage &&
        app.packageName !in keepOnHome &&
        !brandedService.containsMatchIn(app.packageName) &&
        !brandedService.containsMatchIn(app.label)

/** The TV's brand: display name ("SONY", "Hisense"…) and the key of its logo image, if we have one. */
data class TvBrand(val name: String, val logoKey: String?)

/**
 * The TV's brand, from Build.MANUFACTURER. Debug builds can fake it with `debug.tvBrand=sony`
 * (etc.) in local.properties, to check every logo on the emulator.
 */
fun tvBrand(): TvBrand {
    val fake = if (BuildConfig.DEBUG) BuildConfig.DEBUG_TV_BRAND.trim() else ""
    // BRAND first: it's the name on the box. MANUFACTURER can be the contract maker
    // (a Nexus 5X says MANUFACTURER=LGE, BRAND=google); on TVs both are usually the same.
    val raw = fake.ifBlank { Build.BRAND.ifBlank { Build.MANUFACTURER }.trim() }
    val name = brandName(raw)
    return TvBrand(name, logoKeys[name])
}

/** res/drawable/banner_brand_<key>.png */
private val logoKeys = mapOf(
    "SONY" to "sony", "Hisense" to "hisense", "TCL" to "tcl", "Xiaomi" to "xiaomi",
    "PHILIPS" to "philips", "SHARP" to "sharp", "Skyworth" to "skyworth", "Google TV" to "google",
    "SAMSUNG" to "samsung", "LG" to "lg",
)

private fun brandName(raw: String): String {
    return when {
        raw.contains("sony", true) -> "SONY"
        raw.contains("hisense", true) -> "Hisense"
        raw.contains("tcl", true) -> "TCL"
        raw.contains("xiaomi", true) || raw.contains("mitv", true) -> "Xiaomi"
        raw.contains("philips", true) || raw.contains("tpv", true) -> "PHILIPS"
        raw.contains("sharp", true) -> "SHARP"
        raw.contains("skyworth", true) -> "Skyworth"
        raw.contains("changhong", true) -> "CHANGHONG"
        raw.contains("konka", true) -> "KONKA"
        raw.contains("google", true) -> "Google TV"
        raw.contains("samsung", true) -> "SAMSUNG"
        raw.equals("lg", true) || raw.contains("lge", true) || raw.contains("lg electronics", true) -> "LG"
        raw.isBlank() -> "TV"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}
