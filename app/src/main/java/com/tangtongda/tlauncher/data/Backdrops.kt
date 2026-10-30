package com.tangtongda.tlauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * One full-screen hero image.
 * @param caption where it comes from, shown before the title ("Netflix 热门", "B站热门"…)
 * @param isTmdb TMDB requires attribution whenever its images are on screen
 * @param square cover art (albums): shown as a big cover on the right instead of cropped full-bleed
 */
data class Backdrop(
    val title: String,
    val imageUrl: String,
    val caption: String,
    val isTmdb: Boolean = false,
    val square: Boolean = false,
)

/**
 * A place that can supply hero images for some apps.
 * Return `null` for "not my app / nothing usable" so the next source in the chain is tried;
 * return a list (possibly empty) to claim the app.
 */
interface BackdropSource {
    suspend fun load(app: AppInfo): List<Backdrop>?
}

/**
 * Per-app hero images, resolved through a chain, most specific first:
 *
 * 1. The app's own public feed, or the right kind of chart for its category:
 *    Bilibili popular; YouTube trending (API key) or TMDB popular TV + movies;
 *    music apps → Apple Music most-played albums
 * 2. TMDB titles that are actually on that streaming service ("watch providers": Netflix shows
 *    for Netflix, Disney+ shows for Disney+…)
 * 3. Generic TMDB trending, only for apps that are video apps
 * 4. TMDB unreachable: Bilibili's film / TV-series charts (reachable in mainland China)
 * 5. Nothing: the hero falls back to the app's banner / tile color (Play Store, Settings, tools…)
 *
 * (The "real" per-app API on Android TV is preview channels in TvProvider, but third-party
 * launchers aren't allowed to read other apps' channels, so these public feeds stand in.)
 */
class BackdropRepository(context: Context) {

    private val cache = JsonCache(context)
    private val tmdb = TmdbClient(cache)

    private val sources: List<BackdropSource> = listOf(
        BilibiliSource(cache),
        YouTubeSource(cache, tmdb),
        MusicChartSource(cache),
        TmdbProviderSource(tmdb),
        TmdbTrendingSource(tmdb),
        ChinaVideoSource(cache), // TMDB unreachable (common on mainland networks): B站影视榜
    )

    /** Resolved lists per package, kept for 2 h; after that the chain runs again (and JsonCache
     *  falls back to the previous data if the refresh fails). */
    private val memory = ConcurrentHashMap<String, Pair<Long, List<Backdrop>>>()

    suspend fun forApp(app: AppInfo): List<Backdrop> {
        memory[app.packageName]
            ?.takeIf { (time, list) ->
                // Nothing found (often: no network yet right after boot) → try again soon, not in 2 h.
                System.currentTimeMillis() - time < if (list.isEmpty()) RETRY_EMPTY_MS else REFRESH_MS
            }
            ?.let { return it.second }
        val result = withContext(Dispatchers.IO) {
            for (source in sources) {
                val list = runCatching { source.load(app) }
                    .onFailure { Log.w(TAG, "${source.javaClass.simpleName} failed for ${app.packageName}: ${it.message}") }
                    .getOrNull()
                if (list != null) {
                    Log.i(TAG, "${app.packageName}: ${list.size} backdrops from ${source.javaClass.simpleName}")
                    return@withContext list
                }
            }
            Log.i(TAG, "${app.packageName}: no backdrop source (isVideo=${app.isVideo}, isAudio=${app.isAudio}) -> color glow")
            emptyList()
        }
        // Keep showing the previous images if this refresh came back empty.
        val previous = memory[app.packageName]?.second.orEmpty()
        val final = result.ifEmpty { previous }
        memory[app.packageName] = System.currentTimeMillis() to final
        return final
    }

    /** Network came back: retry every app that ended up with nothing. */
    fun forgetEmpty() {
        memory.entries.removeIf { it.value.second.isEmpty() }
    }

    private companion object {
        const val TAG = "Backdrops"
        const val REFRESH_MS = 2 * 60 * 60 * 1000L
        const val RETRY_EMPTY_MS = 3 * 60 * 1000L
    }
}

/** Response cache in SharedPreferences: fresh within [ttlMs], otherwise refetch; stale data is used if the network fails. */
class JsonCache(context: Context) {
    private val prefs = context.getSharedPreferences("backdrop_cache", Context.MODE_PRIVATE)

    fun get(key: String, ttlMs: Long = 6 * 60 * 60 * 1000L, fetch: () -> String): String? {
        val cached = prefs.getString("$key#body", null)
        val age = System.currentTimeMillis() - prefs.getLong("$key#time", 0)
        if (cached != null && age < ttlMs) return cached
        return runCatching(fetch)
            .onSuccess { prefs.edit().putString("$key#body", it).putLong("$key#time", System.currentTimeMillis()).apply() }
            .onFailure { Log.w("Backdrops", "fetch failed ($key): ${it.message}") }
            .getOrNull() ?: cached
    }
}

/** Minimal blocking GET; call from Dispatchers.IO. */
fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (conn.responseCode !in 200..299) {
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("HTTP ${conn.responseCode} ${body.take(200)}")
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
