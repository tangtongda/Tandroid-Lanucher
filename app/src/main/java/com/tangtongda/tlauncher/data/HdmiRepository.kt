package com.tangtongda.tlauncher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** One HDMI input: system id and what to call it ("HDMI 2", or the name the user gave it / the CEC device name). */
data class HdmiInput(val id: String, val label: String)

/**
 * User choices for the "HDMI 已连接" prompt: on/off, and inputs the user said never to ask about
 * again (by input id, so it's per port / per CEC device).
 */
class HdmiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hdmi", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    val ignored: Set<String> get() = prefs.getStringSet("ignored", emptySet()).orEmpty()

    fun ignore(id: String) = prefs.edit().putStringSet("ignored", ignored + id).apply()

    fun clearIgnored() = prefs.edit().remove("ignored").apply()
}

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

    /**
     * Sound bars / AV receivers on ARC or eARC: they "connect" every time the TV turns on, and
     * nobody wants to switch the picture to them. Recognised by the HDMI-CEC device type when the
     * system lets us read it (audio system = 5), otherwise by name.
     */
    private fun isAudioDevice(info: TvInputInfo): Boolean {
        val cecType = runCatching {
            val device = TvInputInfo::class.java.getMethod("getHdmiDeviceInfo").invoke(info) ?: return@runCatching null
            device.javaClass.getMethod("getDeviceType").invoke(device) as? Int
        }.getOrNull()
        if (cecType == CEC_AUDIO_SYSTEM) return true
        return AUDIO_NAME.containsMatchIn(label(info))
    }

    /** The port itself, or any CEC device behind it (child inputs), is an audio device. */
    private fun isAudioPort(info: TvInputInfo): Boolean {
        if (isAudioDevice(info)) return true
        val children = runCatching { tim?.tvInputList.orEmpty().filter { it.parentId == info.id } }.getOrDefault(emptyList())
        return children.any { isAudioDevice(it) }
    }

    private fun label(info: TvInputInfo): String =
        runCatching { info.loadCustomLabel(context)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { info.loadLabel(context).toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "HDMI"

    /** Emits an input each time it becomes connected (plugged in or woken up). Not a TV: never emits. */
    fun connections(): Flow<HdmiInput> {
        val tim = tim ?: return emptyFlow()
        return callbackFlow {
            // Right after the TV boots, the HDMI service comes up and reports every input that is
            // already plugged in as "added / connected". That's not someone plugging a device in,
            // so stay quiet for the first 45 s of a fresh boot (and 3 s after any launcher start).
            val bootQuiet = if (SystemClock.elapsedRealtime() < 5 * 60_000) 45_000 else 3_000
            val quietUntil = SystemClock.elapsedRealtime() + bootQuiet
            val states = HashMap<String, Int>()
            runCatching {
                tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }.forEach { states[it.id] = tim.getInputState(it.id) }
            }
            fun changed(id: String, state: Int) {
                val before = states.put(id, state)
                if (state != TvInputManager.INPUT_STATE_CONNECTED || before == TvInputManager.INPUT_STATE_CONNECTED) return
                val info = hdmiInfo(id) ?: return
                if (SystemClock.elapsedRealtime() < quietUntil) {
                    Log.i(TAG, "HDMI connected during start-up, not asking: $id (${label(info)})")
                    return
                }
                if (isAudioPort(info)) {
                    Log.i(TAG, "HDMI audio device (ARC/eARC), not asking: $id (${label(info)})")
                    return
                }
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
        const val CEC_AUDIO_SYSTEM = 5 // HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM
        val AUDIO_NAME = Regex(
            "soundbar|sound bar|音响|音箱|回音壁|功放|\\barc\\b|earc|receiver|\\bavr\\b|home ?theat|audio|" +
                "sonos|bose|denon|marantz|yamaha|onkyo|pioneer|harman|jbl|sennheiser|polk|vizio|" +
                "\\bht-|\\bhw-|\\bsc-|\\bsr-",
            RegexOption.IGNORE_CASE,
        )
    }
}
