package com.tangtongda.tlauncher.data

import android.graphics.drawable.Drawable

/**
 * One launchable app on the home screen.
 * [banner] is the 16:9 TV artwork (preferred); [icon] is the square fallback.
 * Both are nullable so previews can use fake data without real drawables.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val banner: Drawable? = null,
    val icon: Drawable? = null,
)
