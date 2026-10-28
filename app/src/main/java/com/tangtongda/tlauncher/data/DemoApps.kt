package com.tangtongda.tlauncher.data

/**
 * Debug-only fake tiles (enable with `debug.demoApps=true` in local.properties) so the
 * backdrop chain can be tested without installing every app. They use the real package
 * names, so each one exercises its real source; clicking them does nothing.
 * Installed apps with the same package always win.
 */
internal fun demoApps(installed: List<AppInfo>): List<AppInfo> {
    val have = installed.mapTo(HashSet()) { it.packageName }
    return listOf(
        // Streaming → TMDB watch providers
        AppInfo("com.netflix.ninja", "Netflix", isVideo = true),
        AppInfo("com.disney.disneyplus", "Disney+", isVideo = true),
        AppInfo("com.amazon.amazonvideo.livingroom", "Prime Video", isVideo = true),
        AppInfo("com.apple.atve.androidtv.appletv", "Apple TV", isVideo = true),
        AppInfo("com.gitvdemo.video", "爱奇艺", isVideo = true),
        AppInfo("com.ktcp.video", "腾讯视频", isVideo = true),
        AppInfo("com.youku.taitan.tv", "优酷", isVideo = true),
        AppInfo("com.hunantv.license", "芒果TV", isVideo = true),
        // Own feeds
        AppInfo("com.xiaodianshi.tv.yst", "哔哩哔哩", isVideo = true),
        AppInfo("com.google.android.youtube.tvkids", "YouTube Kids", isVideo = true),
        // Music → Apple Music chart
        AppInfo("com.spotify.tv.android", "Spotify", isAudio = true),
        AppInfo("com.netease.cloudmusic.tv", "网易云音乐", isAudio = true),
        // Generic video → TMDB trending
        AppInfo("com.plexapp.android", "Plex", isVideo = true),
        AppInfo("org.xbmc.kodi", "Kodi", isVideo = true),
        // Not media → no backdrop, banner/tint fallback
        AppInfo("com.example.demo.files", "文件管理"),
        AppInfo("com.example.demo.browser", "浏览器"),
    ).filter { it.packageName !in have }
}
