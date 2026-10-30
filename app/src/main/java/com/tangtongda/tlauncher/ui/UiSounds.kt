package com.tangtongda.tlauncher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.tangtongda.tlauncher.R

/**
 * Our own interface sounds (res/raw, synthesized for this project): a soft glassy tick with a
 * short airy reverb for focus moves, slightly higher for left/right than up/down, and a rounder
 * two-note "pop" for select. The TV's stock click is barely audible on most sets.
 *
 * Still follows the system "sound effects" switch, so they stay silent if the user turned them off.
 * A focus change only sounds when it came from a D-pad press just now, not when the app moves
 * focus by itself (opening, returning to the top…).
 */
object UiSounds {
    private var pool: SoundPool? = null
    private var navH = 0
    private var navV = 0
    private var select = 0

    private var lastKeyAt = 0L
    private var lastHorizontal = false

    private fun ensure(context: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val app = context.applicationContext
        navH = p.load(app, R.raw.ui_nav_h, 1)
        navV = p.load(app, R.raw.ui_nav_v, 1)
        select = p.load(app, R.raw.ui_select, 1)
        pool = p
        return p
    }

    /** Load the sounds early so the very first key press isn't silent. */
    fun warmUp(context: Context) {
        ensure(context)
    }

    /** Feed every key event of the screen here (from onPreviewKeyEvent; never consumes). */
    fun onKey(event: KeyEvent) {
        if (event.type != KeyEventType.KeyDown) return
        lastHorizontal = when (event.key) {
            Key.DirectionLeft, Key.DirectionRight -> true
            Key.DirectionUp, Key.DirectionDown -> false
            else -> return
        }
        lastKeyAt = SystemClock.uptimeMillis()
    }

    fun focusMoved(context: Context) {
        if (SystemClock.uptimeMillis() - lastKeyAt > 400) return
        lastKeyAt = 0 // one sound per key press
        play(context, if (lastHorizontal) navH else navV, 0.7f)
    }

    fun click(context: Context) = play(context, select, 0.8f)

    private fun play(context: Context, id: Int, volume: Float) {
        runCatching {
            val enabled = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1
            if (!enabled) return
            ensure(context).play(id, volume, volume, 1, 0, 1f)
        }
    }
}
