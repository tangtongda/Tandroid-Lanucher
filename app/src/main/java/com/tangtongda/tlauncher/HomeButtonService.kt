package com.tangtongda.tlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Makes the remote's Home button open Tandroid Launcher, whatever launcher the TV would show:
 * the built-in one (Google TV, vendor ROMs that pin Home and have no "default home app"
 * setting) or any other installed launcher.
 *
 * The usual workaround (Projectivy and others do the same) is an accessibility service: whenever
 * another home screen comes to the front, bring ours to the front instead. The user turns it on
 * once under 设置 → 无障碍.
 *
 * It only listens for "a window changed" events (package name); it never reads what is on
 * screen and never sees key presses.
 */
class HomeButtonService : AccessibilityService() {

    private var homes: Set<String> = emptySet()
    private var homesAt = 0L
    private val recent = ArrayDeque<Long>()
    private var pausedUntil = 0L

    override fun onServiceConnected() = refreshHomes()

    private fun refreshHomes() {
        homes = HomeOverride.otherHomes(this)
        homesAt = SystemClock.uptimeMillis()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Pick up launchers installed after the service started (re-scan at most once a minute).
        if (SystemClock.uptimeMillis() - homesAt > 60_000) refreshHomes()
        if (pkg !in homes || HomeOverride.bypassed()) return
        // Another launcher with its own Home-button service would fight us forever (each one
        // pulling itself back to the front). If we've jumped 4 times in 4 s, stand down for a bit.
        val now = SystemClock.uptimeMillis()
        if (now < pausedUntil) return
        while (recent.isNotEmpty() && now - recent.first() > 4_000) recent.removeFirst()
        recent.addLast(now)
        if (recent.size >= 4) {
            recent.clear()
            pausedUntil = now + 15_000
            return
        }
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit
}

/** Shared between the service and the app (same process). */
object HomeOverride {
    @Volatile private var bypassUntil = 0L

    /** The "原桌面" tile opens the stock launcher on purpose: don't bounce back for a moment. */
    fun allowStockHomeBriefly() {
        bypassUntil = SystemClock.uptimeMillis() + 5_000
    }

    fun bypassed(): Boolean = SystemClock.uptimeMillis() < bypassUntil

    /**
     * Every other launcher on the device: built-in or user-installed, plus the known Google ones.
     * Left alone: Settings (its fallback home), and setup wizards / provisioning, which register
     * as HOME but also run account setup screens later.
     */
    fun otherHomes(context: Context): Set<String> {
        val found = runCatching {
            context.packageManager
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        val known = listOf(
            "com.google.android.apps.tv.launcherx", // Google TV
            "com.google.android.tvlauncher",         // Android TV
            "com.google.android.leanbacklauncher",   // older Android TV
        )
        val skip = Regex("setupwizard|setupwraith|provisioning", RegexOption.IGNORE_CASE)
        return (found + known).toSet()
            .filterNot { skip.containsMatchIn(it) }
            .toSet() - context.packageName - setOf("com.android.settings", "com.android.tv.settings")
    }
}
