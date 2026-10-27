package com.tangtongda.tlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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

    fun loadApps(): List<AppInfo> {
        val tvApps = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneApps = query(Intent.CATEGORY_LAUNCHER)
        return (tvApps + phoneApps)
            .distinctBy { it.packageName } // TV entry wins, it was added first
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun query(category: String): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0).map { ri ->
            val ai = ri.activityInfo
            AppInfo(
                packageName = ai.packageName,
                label = ri.loadLabel(pm).toString(),
                banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm),
                icon = ri.loadIcon(pm),
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
