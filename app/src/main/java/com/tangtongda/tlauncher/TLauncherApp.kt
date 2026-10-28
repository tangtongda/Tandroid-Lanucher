package com.tangtongda.tlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * App-wide image loader for hero backdrops (TMDB, Bilibili, Apple Music covers), with a
 * 2-hour, stale-if-error disk cache:
 *
 * - Within 2 h: served from disk, no network.
 * - After 2 h: fetched again. Success → the new image replaces the cached one.
 * - Fetch fails (offline, CDN blocked, 5xx): the old cached image keeps being used.
 *
 * Implemented in OkHttp (its HTTP cache can serve stale entries on demand), so Coil's own
 * disk cache is turned off and Coil only keeps a memory cache on top.
 */
class TLauncherApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .cache(Cache(cacheDir.resolve("image_http_cache"), 200L * 1024 * 1024))
            // Fresh for 2 h regardless of what the CDN says.
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .header("Cache-Control", "public, max-age=$IMAGE_CACHE_SECONDS")
                    .removeHeader("Pragma")
                    .removeHeader("Expires")
                    .build()
            }
            // Stale-if-error: if the refresh fails, fall back to whatever is cached, however old.
            .addInterceptor { chain ->
                val request = chain.request()
                val staleRequest = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
                val response = try {
                    chain.proceed(request)
                } catch (e: IOException) {
                    val cached = chain.proceed(staleRequest)
                    if (cached.isSuccessful) return@addInterceptor cached
                    cached.close()
                    throw e
                }
                if (response.code >= 500) {
                    val cached = chain.proceed(staleRequest)
                    if (cached.isSuccessful) {
                        response.close()
                        return@addInterceptor cached
                    }
                    cached.close()
                }
                response
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .diskCache(null) // OkHttp's cache above does the disk caching
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .build()
    }

    private companion object {
        const val IMAGE_CACHE_SECONDS = 2 * 60 * 60
    }
}
