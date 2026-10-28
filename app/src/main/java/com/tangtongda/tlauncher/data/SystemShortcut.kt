package com.tangtongda.tlauncher.data

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.tangtongda.tlauncher.R

/**
 * Built-in tiles that open system screens. On Google TV the Settings app is not a
 * LEANBACK_LAUNCHER activity, so it never shows up in the normal app list.
 * Icons are local vector drawables (no icon library), so previews render reliably.
 */
data class SystemShortcut(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val action: String,
    val gradient: List<Color>,
)

/** Pseudo action: open the TV's original home screen (handled by AppRepository, not an Intent action). */
const val ACTION_STOCK_HOME = "com.tangtongda.tlauncher.action.STOCK_HOME"

val defaultShortcuts = listOf(
    SystemShortcut(
        id = "stock_home", label = "原桌面", iconRes = R.drawable.ic_sys_home,
        action = ACTION_STOCK_HOME,
        gradient = listOf(Color(0xFF46DFBC), Color(0xFF0EA487)),
    ),
    SystemShortcut(
        id = "settings", label = "设置", iconRes = R.drawable.ic_sys_settings,
        action = Settings.ACTION_SETTINGS,
        gradient = listOf(Color(0xFFB3B5BA), Color(0xFF6A6D73)),
    ),
    SystemShortcut(
        id = "network", label = "网络", iconRes = R.drawable.ic_sys_network,
        action = Settings.ACTION_WIFI_SETTINGS,
        gradient = listOf(Color(0xFF45B0FF), Color(0xFF1462F5)),
    ),
    SystemShortcut(
        id = "apps", label = "应用管理", iconRes = R.drawable.ic_sys_apps,
        action = Settings.ACTION_APPLICATION_SETTINGS,
        gradient = listOf(Color(0xFFA88BFF), Color(0xFF6738EE)),
    ),
)
