package com.tangtongda.tlauncher.data

import com.tangtongda.tlauncher.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Thin TMDB v3 client (Bearer "API Read Access Token"), responses cached via [JsonCache]. */
class TmdbClient(private val cache: JsonCache) {

    val enabled: Boolean get() = BuildConfig.TMDB_TOKEN.isNotBlank()

    fun get(pathAndQuery: String, ttlMs: Long = 2 * 60 * 60 * 1000L): JSONObject? { // 2 h, same as images
        if (!enabled) return null
        val json = cache.get("tmdb:$pathAndQuery", ttlMs) {
            httpGet(
                BuildConfig.TMDB_API_BASE + pathAndQuery,
                mapOf(
                    "Authorization" to "Bearer ${BuildConfig.TMDB_TOKEN}",
                    "Accept" to "application/json",
                ),
            )
        } ?: return null
        return runCatching { JSONObject(json) }.getOrNull()
    }

    /** Turn a TMDB "results" array into backdrops (w1280 is plenty for a 1080p TV). */
    fun backdrops(results: JSONArray?, caption: String): List<Backdrop> {
        if (results == null) return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val item = results.getJSONObject(i)
            val path = item.optString("backdrop_path").takeIf { it.startsWith("/") } ?: return@mapNotNull null
            val title = item.optString("title").ifBlank { item.optString("name") }
            Backdrop(title, BuildConfig.TMDB_IMAGE_BASE + "w1280" + path, caption, isTmdb = true)
        }
    }
}

/**
 * Streaming apps: show what's actually on that service, using TMDB's "watch providers"
 * (JustWatch data). The provider id is looked up by name for [BuildConfig.TMDB_REGION],
 * so no ids are hard-coded. Package names are best-effort; unknown apps fall through.
 */
class TmdbProviderSource(private val tmdb: TmdbClient) : BackdropSource {

    private data class Service(val names: List<String>, val caption: String)

    private val services: Map<String, Service> = mapOf(
        "com.netflix.ninja" to Service(listOf("Netflix"), "Netflix 热门"),
        "com.disney.disneyplus" to Service(listOf("Disney Plus", "Disney+"), "Disney+ 热门"),
        "com.amazon.amazonvideo.livingroom" to Service(listOf("Amazon Prime Video", "Prime Video"), "Prime Video 热门"),
        "com.apple.atve.androidtv.appletv" to Service(listOf("Apple TV Plus", "Apple TV+"), "Apple TV+ 热门"),
        "com.hbo.hbonow" to Service(listOf("Max", "HBO Max"), "Max 热门"),
        "com.wbd.stream" to Service(listOf("Max", "HBO Max"), "Max 热门"),
        // Chinese platforms (TV editions); coverage depends on the TMDB region.
        "com.gitvdemo.video" to Service(listOf("iQIYI"), "爱奇艺热门"),
        "com.qiyi.video" to Service(listOf("iQIYI"), "爱奇艺热门"),
        "com.ktcp.video" to Service(listOf("Tencent Video", "WeTV"), "腾讯视频热门"),
        "com.youku.taitan.tv" to Service(listOf("Youku"), "优酷热门"),
        "com.hunantv.license" to Service(listOf("Mango TV", "MangoTV"), "芒果TV热门"),
    )

    // Editions differ by region/device (iQIYI international, 奇异果, 云视听极光, WeTV, 酷喵…), so
    // also recognise a service from words in the package name or the app label.
    private val hints: List<Pair<Regex, Service>> = listOf(
        Regex("netflix", RegexOption.IGNORE_CASE) to services.getValue("com.netflix.ninja"),
        Regex("disney", RegexOption.IGNORE_CASE) to services.getValue("com.disney.disneyplus"),
        Regex("amazonvideo|prime ?video", RegexOption.IGNORE_CASE) to services.getValue("com.amazon.amazonvideo.livingroom"),
        Regex("iqiyi|qiyi|gitv|爱奇艺|奇异果", RegexOption.IGNORE_CASE) to services.getValue("com.qiyi.video"),
        Regex("ktcp|tencent\\.qqlive|wetv|腾讯视频|云视听极光", RegexOption.IGNORE_CASE) to services.getValue("com.ktcp.video"),
        Regex("youku|优酷|酷喵", RegexOption.IGNORE_CASE) to services.getValue("com.youku.taitan.tv"),
        Regex("hunantv|mgtv|芒果", RegexOption.IGNORE_CASE) to services.getValue("com.hunantv.license"),
    )

    private fun serviceFor(app: AppInfo): Service? =
        services[app.packageName]
            ?: hints.firstOrNull { (re, _) -> re.containsMatchIn(app.packageName) || re.containsMatchIn(app.label) }?.second

    override suspend fun load(app: AppInfo): List<Backdrop>? {
        val service = serviceFor(app) ?: return null
        if (!tmdb.enabled) return null
        val region = BuildConfig.TMDB_REGION
        // Service not listed for this TMDB region (common for Chinese platforms): it's still a
        // streaming app, so show this week's trending titles rather than nothing.
        val providerId = findProviderId(service.names, region) ?: return trendingFallback()
        val query = "with_watch_providers=$providerId&watch_region=$region&sort_by=popularity.desc&language=zh-CN"
        val tv = tmdb.get("discover/tv?$query")?.optJSONArray("results")
        val movies = tmdb.get("discover/movie?$query")?.optJSONArray("results")
        val list = interleave(
            tmdb.backdrops(tv, service.caption),
            tmdb.backdrops(movies, service.caption),
        )
        return list.ifEmpty { trendingFallback() }
    }

    private fun trendingFallback(): List<Backdrop>? =
        tmdb.backdrops(tmdb.get("trending/all/week?language=zh-CN")?.optJSONArray("results"), "本周热门").ifEmpty { null }

    private fun findProviderId(names: List<String>, region: String): Int? {
        val r = URLEncoder.encode(region, "UTF-8")
        for (kind in listOf("tv", "movie")) {
            val results = tmdb.get("watch/providers/$kind?watch_region=$r", ttlMs = 7 * 24 * 60 * 60 * 1000L)
                ?.optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                val p = results.getJSONObject(i)
                val name = p.optString("provider_name")
                if (names.any { it.equals(name, ignoreCase = true) }) return p.optInt("provider_id")
            }
        }
        return null
    }

}

/** a1, b1, a2, b2… so mixed feeds alternate between TV and movies. */
fun <T> interleave(a: List<T>, b: List<T>): List<T> =
    (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

/**
 * Any media app we don't specifically know (players, unknown streaming apps, regional editions):
 * a mix of TMDB trending / popular TV / popular movies, shuffled per app so different apps
 * don't all open on the same picture. Non-media apps (tools, stores, settings) get nothing.
 */
class TmdbTrendingSource(private val tmdb: TmdbClient) : BackdropSource {

    private val knownVideoApps = setOf(
        "com.plexapp.android",
        "org.xbmc.kodi",
        "org.jellyfin.androidtv",
        "org.videolan.vlc",
        "com.stremio.one",
        "com.google.android.videos",
    )

    override suspend fun load(app: AppInfo): List<Backdrop>? {
        if (!app.isVideo && app.packageName !in knownVideoApps) return null
        val trending = tmdb.backdrops(tmdb.get("trending/all/week?language=zh-CN")?.optJSONArray("results"), "本周热门")
        val tv = tmdb.backdrops(tmdb.get("tv/popular?language=zh-CN")?.optJSONArray("results"), "热门剧集")
        val movies = tmdb.backdrops(tmdb.get("movie/popular?language=zh-CN")?.optJSONArray("results"), "热门电影")
        val day = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        return (trending + tv + movies)
            .distinctBy { it.imageUrl }
            .shuffled(java.util.Random(app.packageName.hashCode() * 31L + day)) // stable for a day
            .ifEmpty { null }
    }
}
