package com.tangtongda.tlauncher.data

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.tangtongda.tlauncher.R

/**
 * Built-in tiles that open system screens. On Google TV the Settings app is not a
 * LEANBACK_LAUNCHER activity, so it never shows up in the normal app list.
 * Icons: Icons8 PNGs (credited in CREDITS.md) and a local vector for Settings.
 */
data class SystemShortcut(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val action: String,
    val gradient: List<Color>,
    /** Single-colour (white) glyph that gets painted with [gradient]; false = full-colour artwork, shown as is. */
    val tinted: Boolean = true,
)

/** Pseudo action: open the TV's original home screen (handled by AppRepository, not an Intent action). */
const val ACTION_STOCK_HOME = "com.tangtongda.tlauncher.action.STOCK_HOME"

val defaultShortcuts = listOf(
    SystemShortcut(
        id = "stock_home", label = "原桌面", iconRes = R.drawable.ic8_tvdesk, tinted = false,
        action = ACTION_STOCK_HOME,
        gradient = listOf(Color(0xFF46DFBC), Color(0xFF0EA487)),
    ),
    SystemShortcut(
        id = "settings", label = "设置", iconRes = R.drawable.ic_sys_settings,
        action = Settings.ACTION_SETTINGS,
        gradient = listOf(Color(0xFF9AA4B2), Color(0xFF5B8DEF), Color(0xFF4B5563)), // grey with a blue sheen
    ),
    SystemShortcut(
        id = "network", label = "网络", iconRes = R.drawable.ic8_network,
        action = Settings.ACTION_WIFI_SETTINGS,
        gradient = listOf(Color(0xFF2EE6D6), Color(0xFF3A8DFF), Color(0xFF8B5CF6)), // teal → blue → violet
    ),
    SystemShortcut(
        id = "apps", label = "应用管理", iconRes = R.drawable.ic8_apps,
        action = Settings.ACTION_APPLICATION_SETTINGS,
        gradient = listOf(Color(0xFFFFB648), Color(0xFFFF5E8A), Color(0xFF8B5CF6)), // amber → pink → violet
    ),
)
