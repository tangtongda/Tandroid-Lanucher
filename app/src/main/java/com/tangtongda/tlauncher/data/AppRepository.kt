package com.tangtongda.tlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.tangtongda.tlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** Emits the app list now, and again whenever an app is installed, removed or updated. */
    fun observeApps(): Flow<List<AppInfo>> =
        packageChanges()
            .onStart { emit(Unit) }
            .map { loadApps() }
            .flowOn(Dispatchers.IO)

    /** Package of the TV's own Settings app, if it has a launcher entry. */
    val settingsPackage: String? by lazy {
        pm.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
    }

    /**
     * The TV's original home screen (e.g. Google TV's launcher): any other HOME activity,
     * skipping the system's FallbackHome placeholder that lives in the Settings packages.
     */
    private val stockHome: android.content.pm.ActivityInfo? by lazy {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo }
            .firstOrNull { ai ->
                ai.packageName != context.packageName &&
                    ai.packageName != "com.android.settings" &&
                    ai.packageName != "com.android.tv.settings" &&
                    !ai.name.endsWith("FallbackHome")
            }
    }

    val hasStockHome: Boolean get() = stockHome != null

    /** Jump back to the original home screen; returns false if there isn't one. */
    fun launchStockHome(): Boolean {
        val ai = stockHome ?: return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(ai.packageName, ai.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** The TV's live channels app (tuner / HDMI inputs): the system TV app, then known packages. */
    fun launchLiveTv(): Boolean {
        val view = Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.Channels.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(view) }.isSuccess) return true
        return listOf("com.google.android.tv", "com.android.tv", "com.sony.dtv.tvx").any { launch(it) }
    }

    /** System uninstall confirmation (needs REQUEST_DELETE_PACKAGES). The list refreshes via the package broadcast. */
    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** The system "App info" page (force stop, clear data, permissions…). */
    fun openAppInfo(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Packages that can open media files. Many video/music apps never declare an appCategory,
     * but players do register to open video or audio files, so this is a second "is it media?" signal.
     * (Note: never write a slash-star inside a Kotlin comment: it opens a nested comment.)
     */
    private fun mediaHandlers(mime: String): Set<String> =
        listOf("content://media/x", "https://example.com/x", "file:///x").flatMapTo(HashSet()) { uri ->
            pm.queryIntentActivities(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(uri), mime), 0)
                .map { it.activityInfo.packageName }
        }

    fun loadApps(): List<AppInfo> {
        videoHandlers = mediaHandlers("video/*")
        audioHandlers = mediaHandlers("audio/*")
        val tvApps = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneApps = query(Intent.CATEGORY_LAUNCHER)
        return (tvApps + phoneApps)
            .distinctBy { it.packageName } // TV entry wins, it was added first
            .filter { it.packageName != context.packageName }
            .let { installed -> if (BuildConfig.DEBUG && BuildConfig.DEMO_APPS) installed + demoApps(installed) else installed }
            .sortedBy { it.label.lowercase() }
    }

    private var videoHandlers: Set<String> = emptySet()
    private var audioHandlers: Set<String> = emptySet()

    private fun query(category: String): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0).map { ri ->
            val ai = ri.activityInfo
            AppInfo(
                packageName = ai.packageName,
                label = ri.loadLabel(pm).toString(),
                banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm),
                icon = ri.loadIcon(pm),
                // Media = declared category, or registers to open video / audio files.
                // (Video players usually open audio too: those count as video, not music.)
                isVideo = ai.applicationInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO ||
                    ai.packageName in videoHandlers,
                isAudio = ai.applicationInfo.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO ||
                    (ai.packageName in audioHandlers && ai.packageName !in videoHandlers),
                isSystem = ai.applicationInfo.flags and
                    (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
    }

    /** Prefer the TV entry point, fall back to the phone one. */
    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Open a system screen; fall back to main Settings if the TV doesn't have that page. */
    fun launchSystem(action: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val ok = runCatching { context.startActivity(Intent(action).addFlags(flags)) }.isSuccess
        if (!ok && action != android.provider.Settings.ACTION_SETTINGS) {
            runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(flags))
            }
        }
    }

    private fun packageChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
