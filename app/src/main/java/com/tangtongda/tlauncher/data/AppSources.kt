package com.tangtongda.tlauncher.data

import com.tangtongda.tlauncher.BuildConfig
import org.json.JSONObject

/**
 * Bilibili's public "popular" feed, for the Bilibili apps (phone/HD/TV editions).
 * No key needed. If Bilibili rejects the request (risk control, code != 0) we return null
 * and the chain falls back to TMDB.
 */
class BilibiliSource(private val cache: JsonCache) : BackdropSource {

    private val packages = setOf(
        "tv.danmaku.bili",
        "tv.danmaku.bilibilihd",
        "com.xiaodianshi.tv.yst", // 云视听小电视 (TV edition)
        "dev.aaa1115910.bv", // BV, open-source Bilibili TV client
    )

    // Third-party clients (BiliTV, BBLL, …) ship under many package names, so also match by
    // name: package containing "bili"/"blbl", or a label like "BiliTV", "BBLL", "哔哩哔哩", "B站".
    private val packageHint = Regex("bili|blbl", RegexOption.IGNORE_CASE)
    private val labelHint = Regex("bili|bbll|哔哩|b站", RegexOption.IGNORE_CASE)

    private fun isBilibili(app: AppInfo): Boolean =
        app.packageName in packages ||
            packageHint.containsMatchIn(app.packageName) ||
            labelHint.containsMatchIn(app.label)

    override suspend fun load(app: AppInfo): List<Backdrop>? {
        if (!isBilibili(app)) return null
        val json = cache.get("bili:popular", ttlMs = 2 * 60 * 60 * 1000L) {
            httpGet(
                "https://api.bilibili.com/x/web-interface/popular?ps=20&pn=1",
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
                    "Referer" to "https://www.bilibili.com/",
                ),
            )
        } ?: return null
        val root = JSONObject(json)
        if (root.optInt("code", -1) != 0) return null
        val list = root.optJSONObject("data")?.optJSONArray("list") ?: return null
        return (0 until list.length()).mapNotNull { i ->
            val item = list.getJSONObject(i)
            val pic = item.optString("pic").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Backdrop(
                title = item.optString("title"),
                imageUrl = pic.replaceFirst("http://", "https://"),
                caption = "B站热门",
            )
        }.ifEmpty { null }
    }
}

/** Browser-like headers; Bilibili's web API rejects bare clients. */
private val biliHeaders = mapOf(
    "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    "Referer" to "https://www.bilibili.com/",
)

/**
 * Last resort for video apps when TMDB can't be reached (api.themoviedb.org is often blocked on
 * mainland networks): Bilibili's film and TV-series charts, which load fine in China. Only
 * horizontal artwork is used (posters are portrait and would be cropped badly); if the charts
 * give none, Bilibili's general popular feed.
 */
class ChinaVideoSource(private val cache: JsonCache) : BackdropSource {

    override suspend fun load(app: AppInfo): List<Backdrop>? {
        if (!looksLikeVideoApp(app)) return null
        val movies = chart(2, "B站电影榜")
        val series = chart(5, "B站电视剧榜")
        val list = interleave(series, movies)
        if (list.isNotEmpty()) return list.shuffled(java.util.Random(app.packageName.hashCode().toLong()))
        return popular()
    }

    private fun chart(seasonType: Int, caption: String): List<Backdrop> {
        val json = cache.get("bili:pgc:$seasonType", ttlMs = 2 * 60 * 60 * 1000L) {
            httpGet("https://api.bilibili.com/pgc/web/rank/list?day=3&season_type=$seasonType", biliHeaders)
        } ?: return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        if (root.optInt("code", -1) != 0) return emptyList()
        val list = (root.optJSONObject("result") ?: root.optJSONObject("data"))?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val pic = item.optString("ss_horizontal_cover").ifBlank {
                item.optJSONObject("new_ep")?.optString("cover").orEmpty()
            }.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Backdrop(item.optString("title"), pic.replaceFirst("http://", "https://"), caption)
        }
    }

    private fun popular(): List<Backdrop>? {
        val json = cache.get("bili:popular", ttlMs = 2 * 60 * 60 * 1000L) {
            httpGet("https://api.bilibili.com/x/web-interface/popular?ps=20&pn=1", biliHeaders)
        } ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 0) return null
        val list = root.optJSONObject("data")?.optJSONArray("list") ?: return null
        return (0 until list.length()).mapNotNull { i ->
            val item = list.getJSONObject(i)
            val pic = item.optString("pic").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Backdrop(item.optString("title"), pic.replaceFirst("http://", "https://"), "B站热门")
        }.ifEmpty { null }
    }
}

/**
 * YouTube: its own trending videos when `youtube.apiKey` is set (YouTube Data API v3).
 * Without a key, a TMDB mix instead: popular TV shows + popular movies for YouTube,
 * animation / kids titles for YouTube Kids.
 */
class YouTubeSource(private val cache: JsonCache, private val tmdb: TmdbClient) : BackdropSource {

    private val youtube = "com.google.android.youtube.tv"
    private val youtubeKids = "com.google.android.youtube.tvkids"

    override suspend fun load(app: AppInfo): List<Backdrop>? {
        if (app.packageName != youtube && app.packageName != youtubeKids) return null
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isNotBlank()) {
            ownTrending(key).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return if (app.packageName == youtubeKids) tmdbKids() else tmdbPopularMix()
    }

    private fun tmdbPopularMix(): List<Backdrop>? {
        val tv = tmdb.get("tv/popular?language=zh-CN")?.optJSONArray("results")
        val movies = tmdb.get("movie/popular?language=zh-CN")?.optJSONArray("results")
        return interleave(tmdb.backdrops(tv, "热门剧集"), tmdb.backdrops(movies, "热门电影")).ifEmpty { null }
    }

    private fun tmdbKids(): List<Backdrop>? {
        // TV genres: 16 Animation, 10762 Kids. Movie genres: 16 Animation, 10751 Family.
        val tv = tmdb.get("discover/tv?with_genres=16%7C10762&sort_by=popularity.desc&language=zh-CN")
            ?.optJSONArray("results")
        val movies = tmdb.get("discover/movie?with_genres=16%7C10751&sort_by=popularity.desc&language=zh-CN")
            ?.optJSONArray("results")
        return interleave(tmdb.backdrops(tv, "热门动画"), tmdb.backdrops(movies, "合家欢")).ifEmpty { null }
    }

    private fun ownTrending(key: String): List<Backdrop> {
        val json = cache.get("youtube:popular", ttlMs = 2 * 60 * 60 * 1000L) {
            httpGet(
                "https://www.googleapis.com/youtube/v3/videos" +
                    "?part=snippet&chart=mostPopular&maxResults=20&regionCode=${BuildConfig.TMDB_REGION}&key=$key",
            )
        } ?: return emptyList()
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val snippet = items.getJSONObject(i).optJSONObject("snippet") ?: return@mapNotNull null
            val thumbs = snippet.optJSONObject("thumbnails") ?: return@mapNotNull null
            val url = listOf("maxres", "standard", "high")
                .firstNotNullOfOrNull { thumbs.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) }
                ?: return@mapNotNull null
            Backdrop(title = snippet.optString("title"), imageUrl = url, caption = "YouTube 热门")
        }
    }
}

/**
 * Music apps: Apple Music's public "most played albums" chart (no key, RSS JSON).
 * TMDB only has movies and TV, so music needs its own chart. Album art is square, so these
 * backdrops are rendered as a large cover on the right rather than cropped full-bleed.
 */
class MusicChartSource(private val cache: JsonCache) : BackdropSource {

    private val musicApps = setOf(
        "com.spotify.tv.android",
        "com.google.android.youtube.tvmusic",
        "com.apple.android.music",
        "com.netease.cloudmusic.tv",
        "com.netease.cloudmusic", // phone edition, often sideloaded on TVs
        "com.tencent.qqmusictv",
        "com.tencent.qqmusic",
        "com.kugou.android.tv",
        "com.kugou.android",
        "com.amazon.music.tv",
        "com.aspiro.tidal",
    )

    override suspend fun load(app: AppInfo): List<Backdrop>? {
        if (!app.isAudio && app.packageName !in musicApps) return null
        val region = BuildConfig.TMDB_REGION.lowercase()
        val json = cache.get("music:$region", ttlMs = 2 * 60 * 60 * 1000L) {
            httpGet("https://rss.applemarketingtools.com/api/v2/$region/music/most-played/25/albums.json")
        } ?: return null
        val results = JSONObject(json).optJSONObject("feed")?.optJSONArray("results") ?: return null
        return (0 until results.length()).mapNotNull { i ->
            val item = results.getJSONObject(i)
            val art = item.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Backdrop(
                title = "${item.optString("name")} — ${item.optString("artistName")}",
                imageUrl = art.replace("100x100bb", "1200x1200bb"), // ask the CDN for a large cover
                caption = "音乐热榜",
                square = true,
            )
        }.ifEmpty { null }
    }
}
