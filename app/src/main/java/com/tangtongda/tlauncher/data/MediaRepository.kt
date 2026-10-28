package com.tangtongda.tlauncher.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

enum class MediaKind { Video, Audio, Image }

data class MediaItem(
    val uri: Uri,
    val kind: MediaKind,
    val title: String,
    val folder: String,
    val durationMs: Long,
    val mimeType: String?,
)

/** One storage volume (USB stick, external drive, or the TV's own storage) and the media on it. */
data class MediaVolume(
    val name: String,
    val label: String,
    val removable: Boolean,
    val items: List<MediaItem>,
)

/**
 * Media on USB sticks / external drives (and internal storage), read from MediaStore: Android
 * scans a drive as soon as it is mounted, so this needs no file browsing and works with scoped
 * storage. Refreshes when a drive is plugged in/out or the scanner finds new files.
 */
class MediaRepository(private val context: Context) {

    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @OptIn(FlowPreview::class)
    fun observe(): Flow<List<MediaVolume>> =
        changes()
            .onStart { emit(Unit) }
            .debounce(800) // mounting + scanning fires bursts of events
            .map { load() }
            .flowOn(Dispatchers.IO)

    fun load(): List<MediaVolume> {
        if (!hasPermission()) return emptyList()
        val storage = context.getSystemService(StorageManager::class.java)
        return MediaStore.getExternalVolumeNames(context).map { name ->
            val volume = storage.storageVolumes.firstOrNull { v ->
                val vName = if (Build.VERSION.SDK_INT >= 30) v.mediaStoreVolumeName
                else if (v.isPrimary) MediaStore.VOLUME_EXTERNAL_PRIMARY else v.uuid?.lowercase()
                vName == name
            }
            val removable = volume?.isRemovable ?: (name != MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaVolume(
                name = name,
                label = volume?.getDescription(context) ?: if (removable) "外部存储" else "本机存储",
                removable = removable,
                items = query(name, MediaKind.Video) + query(name, MediaKind.Audio) + query(name, MediaKind.Image),
            )
        }.sortedByDescending { it.removable } // drives first
    }

    private fun query(volume: String, kind: MediaKind): List<MediaItem> {
        val base = when (kind) {
            MediaKind.Video -> MediaStore.Video.Media.getContentUri(volume)
            MediaKind.Audio -> MediaStore.Audio.Media.getContentUri(volume)
            MediaKind.Image -> MediaStore.Images.Media.getContentUri(volume)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val items = mutableListOf<MediaItem>()
        runCatching {
            context.contentResolver.query(base, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext() && items.size < MAX_PER_KIND) {
                    val id = c.getLong(0)
                    val display = c.getString(1).orEmpty()
                    val title = c.getString(2)?.takeIf { it.isNotBlank() } ?: display.substringBeforeLast('.')
                    items += MediaItem(
                        uri = ContentUris.withAppendedId(base, id),
                        kind = kind,
                        title = title,
                        folder = c.getString(3).orEmpty(),
                        durationMs = if (c.isNull(4)) 0 else c.getLong(4),
                        mimeType = c.getString(5),
                    )
                }
            }
        }
        return items
    }

    /** Hand the file to whatever player the TV has (system player, VLC, Kodi…). */
    fun open(item: MediaItem): Boolean {
        val type = item.mimeType ?: when (item.kind) {
            MediaKind.Video -> "video/*"
            MediaKind.Audio -> "audio/*"
            MediaKind.Image -> "image/*"
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun changes(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        awaitClose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    private companion object {
        const val MAX_PER_KIND = 200
    }
}
