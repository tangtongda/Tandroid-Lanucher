package com.tangtongda.tlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import com.tangtongda.tlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Updates for the launcher itself, from this repo's GitHub releases.
 *
 * A release counts when its tag is a version ("v0.6.2" / "0.6.2") and it has an APK attached;
 * other releases (e.g. "store-apps", which only hosts recommended apps) are ignored. The newest
 * such version wins and is offered when it's higher than the installed [BuildConfig.VERSION_NAME].
 *
 * Downloads reuse the store's path: direct link (15 s to start) → SHA-256 check → system
 * installer; if GitHub can't be reached the prompt explains how to install manually.
 * The APK must be signed with the same key as the installed copy, otherwise Android refuses to update; that's checked before the installer opens so the user gets a clear message.
 */
class SelfUpdate(private val context: Context) {

    data class Release(val version: String, val url: String, val sha256: String?, val notes: String)

    private val prefs = context.getSharedPreferences("self_update", Context.MODE_PRIVATE)
    private val store = AppStoreRepository(context)

    /** Newest release if it's newer than us and the user hasn't skipped it; null otherwise (or offline). */
    suspend fun check(force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return@withContext null
        val latest = runCatching { fetchLatest() }.getOrNull() ?: return@withContext null
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        latest.takeIf {
            compare(it.version, BuildConfig.VERSION_NAME) > 0 &&
                (force || prefs.getString(KEY_SKIPPED, null) != it.version)
        }
    }

    fun skip(release: Release) = prefs.edit().putString(KEY_SKIPPED, release.version).apply()

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class Available(val release: Release) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /** "检查更新" in our settings: always asks GitHub, ignores "跳过此版本", and says why when it can't. */
    suspend fun checkNow(): CheckResult = withContext(Dispatchers.IO) {
        runCatching { fetchLatest() }.fold(
            onSuccess = { latest ->
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (latest != null && compare(latest.version, BuildConfig.VERSION_NAME) > 0) {
                    CheckResult.Available(latest)
                } else {
                    CheckResult.UpToDate
                }
            },
            onFailure = { CheckResult.Failed("连不上 GitHub，请稍后再试") },
        )
    }

    private fun fetchLatest(): Release? {
        val headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "Tandroid-Launcher")
        val list = JSONArray(httpGet("https://api.github.com/repos/$REPO/releases?per_page=20", headers))
        return (0 until list.length()).asSequence()
            .map { list.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .mapNotNull { r ->
                val version = VERSION.find(r.optString("tag_name"))?.value ?: return@mapNotNull null
                val assets = r.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    .sortedByDescending { it.optString("name").contains("tandroid", ignoreCase = true) }
                    .firstOrNull() ?: return@mapNotNull null
                Release(
                    version = version,
                    url = apk.optString("browser_download_url"),
                    sha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
                    notes = r.optString("body").trim(),
                )
            }
            .maxWithOrNull { a, b -> compare(a.version, b.version) }
    }

    /** Download, verify, check the signature, then open the system installer. */
    suspend fun install(release: Release, onProgress: (progress: Float, bytes: Long) -> Unit) {
        val self = CatalogApp(
            id = "tandroid-update",
            name = "Tandroid Launcher ${release.version}",
            description = "",
            source = StoreSource.Url(release.url, release.sha256),
            detect = Regex("^$"),
            colors = emptyList(),
        )
        var lastError: Throwable? = null
        // Direct link only: if GitHub's file server doesn't answer within 15 s, fail fast and let
        // the prompt show the manual-install hint (public proxies proved too unreliable to wait for).
        for (candidate in listOf(release.url)) {
            try {
                val file = store.download(self, candidate, firstByteTimeoutMs = 15_000, onProgress = onProgress)
                if (!store.verify(file, release.sha256)) error("安装包校验失败")
                if (!sameSigner(file)) {
                    error("新版本的签名和已安装的不同，无法直接覆盖安装。请用同一台电脑打包，或先卸载再安装")
                }
                store.install(file)
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // Signature mismatch won't get better through another proxy.
                if (e.message?.startsWith("新版本的签名") == true) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    fun canInstall() = store.canInstall()
    fun openInstallPermission() = store.openInstallPermission()

    @Suppress("DEPRECATION")
    private suspend fun sameSigner(file: File): Boolean = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val theirs = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)?.signatures
        val ours = runCatching { pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures }.getOrNull()
        // If either can't be read, let the system installer decide.
        if (theirs.isNullOrEmpty() || ours.isNullOrEmpty()) true else theirs.toSet() == ours.toSet()
    }

    companion object {
        private const val REPO = "tangtongda/Tandroid-Lanucher"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private val VERSION = Regex("\\d+(\\.\\d+)+")

        fun compare(a: String, b: String): Int {
            val x = a.split('.').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}
