@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.BuildConfig
import com.tangtongda.tlauncher.data.SelfUpdate
import kotlinx.coroutines.launch

private sealed interface UpdateStep {
    data object Offer : UpdateStep
    data class Downloading(val progress: Float, val bytes: Long) : UpdateStep
    data object Installing : UpdateStep
    data object NeedsPermission : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

/**
 * "发现新版本" — checks this repo's GitHub releases when the launcher comes to the front (at most
 * every 6 hours, see [SelfUpdate]) and offers to download and install the new APK.
 * Waits while the default-launcher prompt is up, so the two never stack.
 */
/** The release the update prompt is offering; set by the periodic check or by 设置 → 检查更新. */
object UpdateOffer {
    var release by mutableStateOf<SelfUpdate.Release?>(null)
}

@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val updater = remember { SelfUpdate(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var release by UpdateOffer::release
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Offer) }

    LifecycleResumeEffect(Unit) {
        if (release == null) scope.launch { updater.check()?.let { if (release == null) release = it } }
        onPauseOrDispose { }
    }

    val current = release
    val show = current != null && !DefaultHome.homePromptShowing
    DisposableEffect(show) {
        DefaultHome.updatePromptVisible = show
        onDispose { DefaultHome.updatePromptVisible = false }
    }
    if (current == null || DefaultHome.homePromptShowing) return

    // Only a running download blocks closing; if the user backs out of the system installer, they can close or retry.
    val busy = step is UpdateStep.Downloading
    val close: () -> Unit = {
        if (!busy) {
            // "以后再说": the 6-hour check interval decides when it's offered again.
            release = null
            step = UpdateStep.Offer
        }
    }
    val start: () -> Unit = start@{
        if (busy) return@start
        if (!updater.canInstall()) {
            step = UpdateStep.NeedsPermission
            updater.openInstallPermission()
            return@start
        }
        scope.launch {
            step = UpdateStep.Downloading(-1f, 0)
            runCatching {
                updater.install(current) { p, bytes -> step = UpdateStep.Downloading(p, bytes) }
            }.onSuccess {
                step = UpdateStep.Installing // system installer is on screen; we restart after it
            }.onFailure {
                step = UpdateStep.Failed(it.message ?: "更新失败")
            }
        }
    }

    val primary = remember { FocusRequester() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(520.dp)
                    .background(Color(0xF21C2028), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                    .padding(26.dp),
            ) {
                Text("发现新版本 ${current.version}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("当前版本 ${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                if (current.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        current.notes.take(400),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = 8,
                        modifier = Modifier.heightIn(max = 180.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                when (val s = step) {
                    is UpdateStep.Downloading -> {
                        val text = when {
                            s.progress >= 0f -> "下载中 ${(s.progress * 100).toInt()}%"
                            s.bytes > 0 -> "下载中 · 已下载 ${"%.1f".format(s.bytes / 1_048_576f)} MB"
                            else -> "正在连接下载服务器…"
                        }
                        Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(s.progress.coerceIn(0f, 1f)).height(4.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        }
                    }
                    UpdateStep.Installing -> Text("正在打开安装程序，按「更新」确认即可", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    UpdateStep.NeedsPermission -> Text("请允许「安装未知应用」，返回后再按一次「立即更新」", color = Color(0xFFFFD27F), fontSize = 13.sp)
                    is UpdateStep.Failed -> {
                        Text(s.message, color = Color(0xFFFF8A80), fontSize = 13.sp)
                        // GitHub's file servers are often unreachable from mainland networks without a
                        // VPN; offer the manual route instead of retrying forever.
                        if (!s.message.startsWith("新版本的签名")) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "电视连不上 GitHub 的下载服务器时，可以手动安装：在电脑上打开\n" +
                                    "github.com/tangtongda/Tandroid-Lanucher/releases\n" +
                                    "下载 Tandroid-Launcher-${current.version}.apk，用 U 盘或 adb 装到电视上，设置不会丢。",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                    UpdateStep.Offer -> Unit
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PromptButton(
                        when (step) { is UpdateStep.Failed -> "重试"; UpdateStep.Installing -> "重新安装"; else -> "立即更新" },
                        primary = true,
                        onClick = start,
                        modifier = Modifier.focusRequester(primary),
                    )
                    PromptButton("以后再说", primary = false, onClick = close)
                    PromptButton("跳过此版本", primary = false, onClick = {
                        if (!busy) {
                            updater.skip(current)
                            release = null
                            step = UpdateStep.Offer
                        }
                    })
                }
            }
        }
    }
}
