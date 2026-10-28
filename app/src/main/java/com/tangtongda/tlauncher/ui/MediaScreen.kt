@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.R
import com.tangtongda.tlauncher.data.MediaItem
import com.tangtongda.tlauncher.data.MediaKind
import com.tangtongda.tlauncher.data.MediaVolume
import com.tangtongda.tlauncher.ui.theme.TextPrimary
import com.tangtongda.tlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PageMargin = 48.dp
private val CardShape = RoundedCornerShape(12.dp)

/**
 * 媒体库: everything on plugged-in USB sticks / drives (and the TV's own storage), grouped by
 * drive, then by kind. Selecting an item hands it to the TV's player (or VLC/Kodi if installed).
 */
@Composable
fun MediaPage(
    volumes: List<MediaVolume>,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    /** Attached to the first focusable thing on the page, so D-pad Down from the nav bar lands here. */
    entry: FocusRequester,
) {
    Column {
        Text(
            "媒体库",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        Spacer(Modifier.height(16.dp))

        when {
            !permissionGranted -> Notice(
                title = "需要存储权限",
                body = "允许访问存储后，插入的 U 盘和移动硬盘里的视频、音乐和图片会显示在这里。",
                action = "允许访问",
                onAction = onRequestPermission,
                actionModifier = Modifier.focusRequester(entry),
            )
            volumes.none { it.items.isNotEmpty() } -> Notice(
                title = if (volumes.any { it.removable }) "没有找到媒体文件" else "插入 U 盘或移动硬盘",
                body = if (volumes.any { it.removable }) {
                    "已识别到外部存储，但里面没有视频、音乐或图片。刚插入的设备可能还在扫描，请稍等片刻。"
                } else {
                    "插入后会自动扫描，里面的视频、音乐和图片会显示在这里。"
                },
            )
            else -> volumes.filter { it.items.isNotEmpty() }.forEachIndexed { index, volume ->
                VolumeSection(volume, onOpen, entry = if (index == 0) entry else null)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun VolumeSection(volume: MediaVolume, onOpen: (MediaItem) -> Unit, entry: FocusRequester?) {
    var entryUsed = false
    Row(
        Modifier.padding(horizontal = PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_media_usb),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            (if (volume.removable) "外部存储 · " else "本机 · ") + volume.label,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    for (kind in MediaKind.entries) {
        val items = volume.items.filter { it.kind == kind }
        if (items.isEmpty()) continue
        Spacer(Modifier.height(10.dp))
        Text(
            "${kind.title} · ${items.size}",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        // Decide here (in composition), not inside the lazy DSL, which may run later.
        val first = items.first()
        val rowEntry = if (!entryUsed) entry else null
        entryUsed = true
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageMargin, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.uri.toString() }) { item ->
                val m = if (item == first && rowEntry != null) Modifier.focusRequester(rowEntry) else Modifier
                MediaCard(item, modifier = m, onClick = { onOpen(item) })
            }
        }
    }
}

private val MediaKind.title: String
    get() = when (this) {
        MediaKind.Video -> "视频"
        MediaKind.Audio -> "音乐"
        MediaKind.Image -> "图片"
    }

@Composable
private fun MediaCard(item: MediaItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(Modifier.width(176.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .width(176.dp)
                .height(99.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CardShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f, pressedScale = 0.97f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 16.dp),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF242A35),
                focusedContainerColor = Color(0xFF242A35),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                val thumb = rememberThumbnail(item)
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // No thumbnail (most music, some videos): a kind-colored tile with a glyph.
                    val (top, bottom, icon) = when (item.kind) {
                        MediaKind.Audio -> Triple(Color(0xFFFF5E7E), Color(0xFFE0245E), R.drawable.ic_media_music)
                        MediaKind.Video -> Triple(Color(0xFF45B0FF), Color(0xFF1462F5), R.drawable.ic_media_play)
                        MediaKind.Image -> Triple(Color(0xFF46DFBC), Color(0xFF0EA487), R.drawable.ic_media_play)
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(top, bottom))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                if (item.kind != MediaKind.Image && item.durationMs > 0) {
                    Text(
                        formatDuration(item.durationMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.folder.isNotBlank()) {
            Text(item.folder, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Video frame / album art / image thumbnail from MediaStore, loaded off the main thread. */
@Composable
private fun rememberThumbnail(item: MediaItem): ImageBitmap? {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, android.util.Size(384, 216), null).asImageBitmap()
            }.getOrNull()
        }
    }
    return thumb
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Notice(
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
    actionModifier: Modifier = Modifier,
) {
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .width(520.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(24.dp),
    ) {
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TextSecondary, fontSize = 15.sp)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = onAction,
                modifier = actionModifier,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = TextPrimary,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Text(
                    action,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                )
            }
        }
    }
}
