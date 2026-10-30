package com.tangtongda.tlauncher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * tv-material's clickable Surface only reacts to the remote (D-pad OK) and accessibility, not to
 * touch. Adding this makes taps and long presses work too, for phones, tablets and touch TVs.
 * Dragging still scrolls the page: a tap is cancelled once the finger moves.
 */
fun Modifier.touchClickable(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onClick)
    val longPress by rememberUpdatedState(onLongClick)
    pointerInput(Unit) {
        detectTapGestures(
            onTap = { tap() },
            onLongPress = { longPress?.invoke() },
        )
    }
}
