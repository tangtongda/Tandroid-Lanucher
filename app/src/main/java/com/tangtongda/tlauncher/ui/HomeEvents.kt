package com.tangtongda.tlauncher.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/** One-off signals from the Activity into the home UI. */
object HomeEvents {
    /** The remote's Home button was pressed while the launcher was already in front. */
    val goHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
