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
    /** The app declares itself as a video app (ApplicationInfo.CATEGORY_VIDEO). */
    val isVideo: Boolean = false,
    /** The app declares itself as a music/audio app (ApplicationInfo.CATEGORY_AUDIO). */
    val isAudio: Boolean = false,
    /** Preinstalled system app: can't be uninstalled from the launcher. */
    val isSystem: Boolean = false,
)
