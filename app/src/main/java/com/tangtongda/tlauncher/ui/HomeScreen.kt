@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.R
import com.tangtongda.tlauncher.data.AppInfo
import com.tangtongda.tlauncher.ui.theme.Accent
import com.tangtongda.tlauncher.ui.theme.Background
import com.tangtongda.tlauncher.ui.theme.SurfaceColor
import com.tangtongda.tlauncher.ui.theme.TLauncherTheme
import com.tangtongda.tlauncher.ui.theme.TextPrimary
import com.tangtongda.tlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Design rules adapted from the tvOS HIG skill (rule IDs in comments).
// Unit note: a 1080p Android TV runs at xhdpi, so the screen is 960x540dp and 1dp = 2px.
// tvOS sizes are in pt at 1920x1080 (1pt = 1px), so tvOS "29pt" ≈ 15sp here.

// ---------- Stateful entry (real data) ----------

@Composable
fun HomeRoute(vm: HomeViewModel = viewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    HomeContent(apps = apps, onAppClick = vm::launch)
}

// ---------- Stateless UI (used by previews too) ----------

@Composable
fun HomeContent(
    apps: List<AppInfo>?,
    onAppClick: (AppInfo) -> Unit,
    now: LocalDateTime? = null, // fixed time for previews
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            // TV overscan-safe margins: 48dp horizontal / 27dp vertical
            .padding(vertical = 27.dp),
    ) {
        TopBar(now = now, modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.section_apps),
            color = TextPrimary,
            fontSize = 24.sp, // DISTANCE-01: titles ≥ 48pt
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        Spacer(Modifier.height(16.dp))

        when {
            apps == null -> Unit // loading: keep blank, avoids flicker
            apps.isEmpty() -> Text(
                text = stringResource(R.string.empty_apps),
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            else -> AppRow(apps = apps, onAppClick = onAppClick)
        }
    }
}

@Composable
private fun TopBar(now: LocalDateTime?, modifier: Modifier = Modifier) {
    var time by remember { mutableStateOf(now ?: LocalDateTime.now()) }
    if (now == null) {
        LaunchedEffect(Unit) {
            while (true) {
                time = LocalDateTime.now()
                delay(1_000L * (60 - time.second)) // tick on the minute
            }
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Tandroid", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Medium, // DISTANCE-07: no thin weights
            )
            Text(
                time.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppRow(apps: List<AppInfo>, onAppClick: (AppInfo) -> Unit) {
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // FOCUS-06: a default focused element when the launcher opens.
        runCatching { firstItem.requestFocus() }
    }
    LazyRow(
        // FOCUS-07: re-entering the row restores the last focused card, falling back to the first.
        modifier = Modifier.focusRestorer { firstItem },
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(
                app = app,
                onClick = { onAppClick(app) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }
}

private val CardShape = RoundedCornerShape(12.dp)

// 176x99dp = 352x198px: above the 250x150 minimum (FOCUS-05), and ~4.4 cards fit the
// safe area so the next card peeks in instead of a scroll indicator (DISTANCE-05, anti-patterns).
private val CardWidth = 176.dp
private val CardHeight = 99.dp
private val CardGap = 20.dp

@Composable
fun AppCard(app: AppInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.width(CardWidth)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .width(CardWidth)
                .height(CardHeight) // 16:9, same ratio as TV banners
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape = CardShape),
            // FOCUS-01: scale + border + glow, never color alone. REMOTE-07: press shrinks for click feedback.
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f, pressedScale = 0.95f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Accent.copy(alpha = 0.6f), elevation = 12.dp),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = SurfaceColor,
                focusedContainerColor = SurfaceColor,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, Color.White),
                    shape = CardShape,
                ),
            ),
        ) {
            AppArtwork(app)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = app.label,
            color = if (focused) TextPrimary else TextSecondary,
            fontSize = 16.sp, // DISTANCE-01: secondary labels ≥ 25pt
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AppArtwork(app: AppInfo) {
    val banner = rememberBitmap(app.banner)
    val icon = rememberBitmap(app.icon)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            banner != null -> Image(
                bitmap = banner, contentDescription = app.label,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            icon != null -> Image(
                bitmap = icon, contentDescription = app.label,
                modifier = Modifier.size(48.dp),
            )
            else -> Text( // preview / no artwork: first letter placeholder
                text = app.label.take(1).uppercase(),
                color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun rememberBitmap(drawable: Drawable?): ImageBitmap? = remember(drawable) {
    drawable?.let {
        runCatching {
            val w = it.intrinsicWidth.takeIf { v -> v > 0 } ?: 320
            val h = it.intrinsicHeight.takeIf { v -> v > 0 } ?: 180
            it.toBitmap(w, h).asImageBitmap()
        }.getOrNull()
    }
}

// ---------- Previews (no device needed) ----------

private val fakeApps = listOf(
    "YouTube", "Netflix", "Bilibili", "爱奇艺", "腾讯视频", "Kodi", "Plex", "设置",
).map { AppInfo(packageName = "fake.$it", label = it) }

private val previewTime = LocalDateTime.of(2026, 10, 26, 20, 30)

// What a 1080p Android TV actually reports: 1920x1080px at xhdpi = 960x540dp.
private const val TV_1080P_XHDPI = "spec:width=960dp,height=540dp,dpi=320"


@Preview(name = "Home 1080p", device = TV_1080P_XHDPI, showBackground = true)
@Composable
private fun HomePreview() {
    TLauncherTheme { HomeContent(apps = fakeApps, onAppClick = {}, now = previewTime) }
}

@Preview(name = "Home empty", device = TV_1080P_XHDPI, showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    TLauncherTheme { HomeContent(apps = emptyList(), onAppClick = {}, now = previewTime) }
}

@Preview(name = "Card", widthDp = 260, heightDp = 180, backgroundColor = 0xFF0E1116, showBackground = true)
@Composable
private fun CardPreview() {
    TLauncherTheme {
        Box(Modifier.padding(24.dp)) { AppCard(app = fakeApps.first(), onClick = {}) }
    }
}
