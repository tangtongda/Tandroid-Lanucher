package com.tangtongda.tlauncher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** One HDMI input: system id and what to call it ("HDMI 2", or the name the user gave it / the CEC device name). */
data class HdmiInput(val id: String, val label: String)

/**
 * HDMI inputs through the public TvInputManager API: hot-plug events and switching.
 *
 * "Connected" fires when a device is plugged in, or wakes up (a console or set-top box turning on
 * goes from standby to connected). Switching opens the TV's own input/Live TV app on that input via
 * the standard passthrough channel URI, which is how launchers are meant to do it.
 */
class HdmiRepository(private val context: Context) {

    private val tim: TvInputManager? = runCatching { context.getSystemService(TvInputManager::class.java) }.getOrNull()

    private fun hdmiInfo(id: String): TvInputInfo? =
        runCatching { tim?.getTvInputInfo(id) }.getOrNull()?.takeIf { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput }

    private fun label(info: TvInputInfo): String =
        runCatching { info.loadCustomLabel(context)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { info.loadLabel(context).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "HDMI"

    /** Emits an input each time it becomes connected (plugged in or woken up). Not a TV: never emits. */
    fun connections(): Flow<HdmiInput> {
        val tim = tim ?: return emptyFlow()
        return callbackFlow {
            val states = HashMap<String, Int>()
            runCatching {
                tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }.forEach { states[it.id] = tim.getInputState(it.id) }
            }
            fun changed(id: String, state: Int) {
                val before = states.put(id, state)
                if (state != TvInputManager.INPUT_STATE_CONNECTED || before == TvInputManager.INPUT_STATE_CONNECTED) return
                val info = hdmiInfo(id) ?: return
                Log.i(TAG, "HDMI connected: $id (${label(info)}), was $before")
                trySend(HdmiInput(id, label(info)))
            }
            val callback = object : TvInputManager.TvInputCallback() {
                override fun onInputStateChanged(inputId: String, state: Int) = changed(inputId, state)
                override fun onInputAdded(inputId: String) {
                    val state = runCatching { tim.getInputState(inputId) }.getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED)
                    changed(inputId, state)
                }
                override fun onInputRemoved(inputId: String) {
                    states.remove(inputId)
                }
            }
            runCatching { tim.registerCallback(callback, Handler(Looper.getMainLooper())) }
                .onFailure { Log.w(TAG, "can't watch HDMI inputs: ${it.message}") }
            awaitClose { runCatching { tim.unregisterCallback(callback) } }
        }
    }

    /** Switch the TV to that input (opens the TV's input app on it). */
    fun switchTo(input: HdmiInput): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "switch to ${input.id} failed: ${it.message}") }
            .isSuccess
    }

    private companion object {
        const val TAG = "Hdmi"
    }
}
