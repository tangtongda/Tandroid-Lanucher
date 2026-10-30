package com.tangtongda.tlauncher.ui

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The TV's own navigation and click sounds (the same ones Google TV's home screen plays).
 * They follow the system "sound effects" setting, so they're silent if the user turned them off.
 *
 * A focus change only clicks when it came from a D-pad press just now, not when the app moves
 * focus by itself (opening, returning to the top…).
 */
object UiSounds {
    private var lastKeyAt = 0L
    private var lastEffect = AudioManager.FX_FOCUS_NAVIGATION_DOWN

    /** Feed every key event of the screen here (from onPreviewKeyEvent; never consumes). */
    fun onKey(event: KeyEvent) {
        if (event.type != KeyEventType.KeyDown) return
        lastEffect = when (event.key) {
            Key.DirectionUp -> AudioManager.FX_FOCUS_NAVIGATION_UP
            Key.DirectionDown -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
            Key.DirectionLeft -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
            Key.DirectionRight -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            else -> return
        }
        lastKeyAt = SystemClock.uptimeMillis()
    }

    fun focusMoved(context: Context) {
        if (SystemClock.uptimeMillis() - lastKeyAt > 400) return
        lastKeyAt = 0 // one sound per key press
        play(context, lastEffect)
    }

    fun click(context: Context) = play(context, AudioManager.FX_KEY_CLICK)

    private fun play(context: Context, effect: Int) {
        runCatching { context.getSystemService(AudioManager::class.java)?.playSoundEffect(effect) }
    }
}
