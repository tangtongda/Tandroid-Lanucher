package com.tangtongda.tlauncher.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * An app offered under 应用 → 推荐应用.
 *
 * Source, in order:
 * 1. A copy bundled in our own APK at `assets/store/<id>.apk` (put it there yourself; `*.apk`
 *    is git-ignored, so third-party binaries never end up in the public repo)
 * 2. The project's official download channel ([StoreSource]), matching this TV's CPU
 */
sealed interface StoreSource {
    /** Latest release on GitHub. */
    data class GitHub(val repo: String) : StoreSource
    /** Official Kodi mirrors: mirrors.kodi.tv/releases/android/<arch>/ */
    data object KodiMirror : StoreSource

    /**
     * A fixed download link: an app's official download URL, or an APK we host ourselves as an
     * asset of this repo's GitHub release (only for apps whose licence allows redistribution).
     */
    data class Url(val url: String, val sha256: String? = null) : StoreSource

    /** One build per CPU: 64-bit or 32-bit Android (many TVs run 32-bit Android on 64-bit chips). */
    data class ByAbi(val arm64: Url, val arm32: Url) : StoreSource
}

data class CatalogApp(
    val id: String,
    val name: String,
    val description: String,
    val source: StoreSource,
    /** Tried when [source] fails (unreachable, rate-limited, no matching APK). */
    val fallback: StoreSource? = null,
    /** Group on the 应用 page: 影视 / 音乐 / 电视. */
    val category: String = "影视",
    /** When a release has several flavours, prefer assets whose name matches (e.g. TVBox "java" over "python"). */
    val preferAsset: Regex? = null,
    /** Recognises the app once installed (package name or label). */
    val detect: Regex,
    val colors: List<Color>,
)

val appCatalog = listOf(
    CatalogApp(
        id = "bbll",
        name = "BBLL",
        description = "第三方哔哩哔哩 TV 客户端",
        source = StoreSource.GitHub("xiaye13579/BBLL"),
        fallback = StoreSource.Url(
            "https://github.com/tangtongda/Tandroid-Lanucher/releases/download/store-apps/BBLL-1.5.2.apk",
            sha256 = "fda091e6526b08dfa7c7ce30f0c1de46df27dd7a2b7bc3a48d228aa6762522f0",
        ),
        detect = Regex("bbll|com\\.xx\\.blbl", RegexOption.IGNORE_CASE), // package com.xx.blbl, label BBLL
        colors = listOf(Color(0xFFFF7EAA), Color(0xFFE0457B)),
    ),
    CatalogApp(
        id = "ibilitv",
        name = "iBiliTV",
        description = "第三方哔哩哔哩 TV 客户端",
        source = StoreSource.Url(
            "https://github.com/tangtongda/Tandroid-Lanucher/releases/download/store-apps/iBiliTV.apk",
            sha256 = "73229ae195f45cdbfd4577b81962a4b4c9345a36abcd846e00dbc0647c127ef2",
        ),
        detect = Regex("ibili", RegexOption.IGNORE_CASE),
        colors = listOf(Color(0xFF6FD3FF), Color(0xFF1E9BE0)),
    ),
    CatalogApp(
        id = "tvbox",
        name = "TVBox",
        description = "开源影视播放器，需自行配置内容源",
        source = StoreSource.GitHub("j4Uq/TVBoxOSC"),
        // Each release: java / java32 / java64 / python / python32 / python64. Java is the lighter
        // build and enough for normal configs; 32/64 is picked from the TV's CPU below.
        preferAsset = Regex("-java(32|64)?\\.apk$", RegexOption.IGNORE_CASE),
        detect = Regex("tvbox", RegexOption.IGNORE_CASE), // e.g. com.github.tvbox.osc
        colors = listOf(Color(0xFF4FA3FF), Color(0xFF2B5BD7)),
    ),
    CatalogApp(
        id = "kodi",
        name = "Kodi",
        description = "开源媒体中心，播放 U 盘 / NAS 视频",
        source = StoreSource.KodiMirror,
        // Our own copy of the official 21.3 builds (GPL, unmodified), for when the mirrors are slow.
        fallback = StoreSource.ByAbi(
            arm64 = StoreSource.Url(
                "https://github.com/tangtongda/Tandroid-Lanucher/releases/download/store-apps/kodi-21.3-Omega-arm64-v8a.apk",
                sha256 = "34e7526705a038fa12f70161d0c1cc38f10deb0fee305b46fbd46ff015d05b44",
            ),
            arm32 = StoreSource.Url(
                "https://github.com/tangtongda/Tandroid-Lanucher/releases/download/store-apps/kodi-21.3-Omega-armeabi-v7a.apk",
                sha256 = "12d75e895649f68f217e42c2d881a94fd3177d7b9a7937825852aed545520cc8",
            ),
        ),
        detect = Regex("org\\.xbmc\\.kodi"),
        colors = listOf(Color(0xFF17B2E7), Color(0xFF0E6E99)),
    ),
)

/**
 * GitHub release downloads redirect to githubusercontent.com, which is often unreachable or very
 * slow on mainland networks. These public "GitHub file proxies" fetch the same file for you; the
 * SHA-256 check after download catches any proxy that serves something else. Tried in order,
 * after the direct link. Replace with your own (e.g. a Cloudflare Worker gh-proxy) for reliability.
 */
val githubProxies = listOf(
    "https://ghfast.top/",
    "https://gh-proxy.com/",
)

/** The direct URL first, then the same URL through each proxy (GitHub links only). */
fun downloadCandidates(url: String): List<String> =
    if (url.startsWith("https://github.com/")) listOf(url) + githubProxies.map { it + url } else listOf(url)

sealed interface StoreState {
    data object Idle : StoreState
    data object Resolving : StoreState
    /** [progress] 0..1, or -1 while the size isn't known yet (connecting, redirecting). */
    data class Downloading(val progress: Float, val bytes: Long = 0) : StoreState
    data object Installing : StoreState
    /** Android 8+: the user has to allow "install unknown apps" for this launcher once. */
    data object NeedsPermission : StoreState
    data class Failed(val message: String) : StoreState
}

class AppStoreRepository(private val context: Context) {

    private data class Asset(val name: String, val url: String, val sha256: String?)

    /** Download URL and expected SHA-256 (if published) of the newest build for this TV's CPU. */
    suspend fun resolve(app: CatalogApp, source: StoreSource = app.source): Pair<String, String?> = when (source) {
        is StoreSource.GitHub -> resolveGitHub(source.repo, app.preferAsset)
        StoreSource.KodiMirror -> resolveKodi()
        is StoreSource.Url -> source.url to source.sha256
        is StoreSource.ByAbi -> {
            val pick = if (Build.SUPPORTED_ABIS.firstOrNull()?.contains("64") == true) source.arm64 else source.arm32
            pick.url to pick.sha256
        }
    }

    /**
     * Kodi publishes APKs per architecture on its official mirror. Pick the folder for the TV's
     * primary ABI (many TVs, the A80J included, run a 32-bit userland → "arm"), then the newest
     * stable version in the listing. The mirror (mirrorbits) answers `?sha256` with the checksum.
     */
    private suspend fun resolveKodi(): Pair<String, String?> = withContext(Dispatchers.IO) {
        val primary = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val (dir, abi) = when {
            primary.startsWith("arm64") -> "arm64-v8a" to "arm64-v8a"
            primary.startsWith("x86_64") -> "x86_64" to "x86_64"
            else -> "arm" to "armeabi-v7a"
        }
        val base = "https://mirrors.kodi.tv/releases/android/$dir/"
        val html = httpGet(base, mapOf("User-Agent" to "Tandroid-Launcher"))
        val re = Regex("""kodi-(\d+(?:\.\d+)*)-([A-Za-z]+)-${Regex.escape(abi)}\.apk""")
        val newest = re.findAll(html)
            .map { it.value to it.groupValues[1].split('.').map(String::toInt) }
            .distinctBy { it.first }
            .maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
            ?.first ?: error("没有找到 Kodi 安装包")
        val url = base + newest
        url to fetchShortHash("$url?sha256")
    }

    /** Reads at most 1 KB, so a mirror that ignored `?sha256` and sent the APK can't flood memory. */
    private fun fetchShortHash(url: String): String? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Tandroid-Launcher")
            if (conn.responseCode !in 200..299) return@runCatching null
            val buf = ByteArray(1024)
            val n = conn.inputStream.use { it.read(buf) }.coerceAtLeast(0)
            Regex("^[0-9a-fA-F]{64}").find(String(buf, 0, n).trim())?.value
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** Latest GitHub release APK for this device's ABI: prefer an exact ABI match, then universal, then any APK. */
    private suspend fun resolveGitHub(repo: String, prefer: Regex?): Pair<String, String?> = withContext(Dispatchers.IO) {
        val headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "Tandroid-Launcher")
        // "latest" skips pre-releases and 404s when a repo only has those: then take the newest
        // release (of the last few) that actually has an APK attached.
        val release = runCatching { JSONObject(httpGet("https://api.github.com/repos/$repo/releases/latest", headers)) }
            .getOrNull()
            ?.takeIf { r -> r.optJSONArray("assets")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("name").endsWith(".apk", true) } } == true }
            ?: org.json.JSONArray(httpGet("https://api.github.com/repos/$repo/releases?per_page=10", headers)).let { list ->
                (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { r ->
                    r.optJSONArray("assets")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("name").endsWith(".apk", true) } } == true
                }
            }
            ?: error("这个项目的发布里没有找到安装包")
        val assets = release.optJSONArray("assets") ?: error("没有找到安装包")
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            .map {
                Asset(
                    name = it.optString("name"),
                    url = it.optString("browser_download_url"),
                    sha256 = it.optString("digest").takeIf { d -> d.startsWith("sha256:") }?.removePrefix("sha256:"),
                )
            }
        if (apks.isEmpty()) error("最新版本里没有 APK")
        val pool = prefer?.let { re -> apks.filter { re.containsMatchIn(it.name) } }?.ifEmpty { null } ?: apks
        val pick = pickForDevice(pool)
        pick.url to pick.sha256
    }

    /**
     * The APK that fits this TV's CPU. Many TVs (the A80J included) run 32-bit Android even on a
     * 64-bit chip; SUPPORTED_ABIS then lists only 32-bit ABIs, and a 64-bit APK would fail to install.
     *
     * 1. exact ABI in the name ("arm64-v8a", "armeabi-v7a")
     * 2. bitness in the name: "64"/"arm64"/"aarch64" or "32"/"armeabi"/"armv7"/"arm32"
     * 3. a universal build (no CPU marker at all), which runs everywhere
     * 4. anything
     */
    private fun pickForDevice(apks: List<Asset>): Asset {
        val abis = Build.SUPPORTED_ABIS.toList() // e.g. [armeabi-v7a, armeabi]
        val is64 = abis.firstOrNull()?.contains("64") == true
        val mark64 = Regex("arm64|aarch64|x86_64|(?<![0-9])64(?![0-9])", RegexOption.IGNORE_CASE)
        val mark32 = Regex("armeabi|armv7|arm32|(?<![0-9])32(?![0-9])", RegexOption.IGNORE_CASE)
        return abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.name.contains(abi, ignoreCase = true) } }
            ?: apks.firstOrNull { (if (is64) mark64 else mark32).containsMatchIn(it.name) }
            ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.firstOrNull { !mark64.containsMatchIn(it.name) && !mark32.containsMatchIn(it.name) }
            ?: apks.first()
    }

    /** Download with the system DownloadManager into app-private storage, reporting 0..1 progress. */
    suspend fun download(app: CatalogApp, url: String, onProgress: (progress: Float, bytes: Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("没有可用的存储空间")
        val file = File(dir, "${app.id}.apk").apply { delete() }
        val dm = context.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue( // Kodi is ~80 MB, so this goes through DownloadManager rather than a plain GET
            DownloadManager.Request(Uri.parse(url))
                .setTitle(app.name)
                .setDestinationUri(Uri.fromFile(file))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE),
        )
        onProgress(-1f, 0) // queued: show "connecting" right away, not "looking for the latest version"
        // Give up when nothing arrives for a while (pending forever, stalled CDN…), so the next
        // source can be tried instead of the card sitting on one state indefinitely.
        var lastBytes = -1L
        var lastChange = System.currentTimeMillis()
        try {
            while (true) {
                dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) error("下载被取消")
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f, done)
                    if (done != lastBytes) {
                        lastBytes = done
                        lastChange = System.currentTimeMillis()
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@withContext file
                        DownloadManager.STATUS_FAILED -> error("下载失败，请检查网络")
                    }
                    val stalledFor = System.currentTimeMillis() - lastChange
                    if (stalledFor > (if (done == 0L) 30_000 else 60_000)) error("下载超时，网络太慢或无法连接")
                }
                delay(500)
            }
        } catch (e: Throwable) {
            dm.remove(id) // cancel the system download too
            throw e
        }
        @Suppress("UNREACHABLE_CODE")
        file
    }

    /** Compare against the SHA-256 GitHub publishes for the asset, when it does. */
    suspend fun verify(file: File, expectedSha256: String?): Boolean = withContext(Dispatchers.IO) {
        if (expectedSha256 == null) return@withContext true
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }.equals(expectedSha256, ignoreCase = true)
    }

    /** True when a copy ships inside our APK (assets/store/<id>.apk). */
    fun hasBundled(app: CatalogApp): Boolean =
        runCatching { context.assets.list("store")?.contains("${app.id}.apk") == true }.getOrDefault(false)

    /** Copies the bundled APK out of our assets so the system installer can read it. */
    suspend fun extractBundled(app: CatalogApp): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("没有可用的存储空间")
        val file = File(dir, "${app.id}.apk")
        context.assets.open("store/${app.id}.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this launcher to install apps. */
    fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Hands the APK to the system installer (the user confirms there). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
