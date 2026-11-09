@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.BuildConfig
import com.tangtongda.tlauncher.data.SelfUpdate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsPage { Main, About, Disclaimer }

private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object UpToDate : CheckState
    data class Failed(val message: String) : CheckState
}

/**
 * Tandroid Launcher's own settings (the gear at the top right): Home button, update check,
 * about, disclaimer, and a way into the TV's system settings. A side panel on the right, like
 * Google TV's quick settings. Back goes from a sub-page to the list, then closes.
 */
@Composable
fun LauncherSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(SettingsPage.Main) }
    Dialog(
        onDismissRequest = { if (page != SettingsPage.Main) page = SettingsPage.Main else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(Color(0xF7181C24))
                    .padding(horizontal = 28.dp, vertical = 32.dp),
            ) {
                when (page) {
                    SettingsPage.Main -> MainPage(
                        onAbout = { page = SettingsPage.About },
                        onDisclaimer = { page = SettingsPage.Disclaimer },
                        onUpdateFound = { release ->
                            UpdateOffer.release = release // the update prompt takes it from here
                            onDismiss()
                        },
                        onSystemSettings = {
                            context.startSafely(Intent(Settings.ACTION_SETTINGS))
                            onDismiss()
                        },
                    )
                    SettingsPage.About -> AboutPage()
                    SettingsPage.Disclaimer -> DisclaimerPage()
                }
            }
        }
    }
}

@Composable
private fun MainPage(
    onAbout: () -> Unit,
    onDisclaimer: () -> Unit,
    onUpdateFound: (SelfUpdate.Release) -> Unit,
    onSystemSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { SelfUpdate(context.applicationContext) }
    var homeOk by remember { mutableStateOf(DefaultHome.homeButtonOk(context)) }
    var check by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    LifecycleResumeEffect(Unit) {
        homeOk = DefaultHome.homeButtonOk(context) // back from the 无障碍 page
        onPauseOrDispose { }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Text("Tandroid 设置", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val tv = remember { DefaultHome.isTv(context) }
        SettingsRow(
            title = if (tv) "主页键" else "设为默认桌面",
            subtitle = when {
                homeOk -> "已开启：按主页键会回到 Tandroid"
                tv -> "未开启：按 OK 打开「无障碍」，开启「Tandroid Launcher 主页键」"
                else -> "未设置：按 OK 选择默认主屏幕应用"
            },
            subtitleColor = if (homeOk) Color(0xFF8FE3A8) else Color(0xFFFFD27F),
            modifier = Modifier.focusRequester(first),
        ) {
            context.startSafely(Intent(if (tv) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_HOME_SETTINGS))
        }
        SettingsRow(
            title = "检查更新",
            subtitle = when (val c = check) {
                CheckState.Idle -> "当前版本 ${BuildConfig.VERSION_NAME}"
                CheckState.Checking -> "正在检查…"
                CheckState.UpToDate -> "已是最新版本 ${BuildConfig.VERSION_NAME}"
                is CheckState.Failed -> c.message
            },
            subtitleColor = if (check is CheckState.Failed) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.6f),
        ) {
            if (check == CheckState.Checking) return@SettingsRow
            check = CheckState.Checking
            scope.launch {
                check = when (val r = updater.checkNow()) {
                    is SelfUpdate.CheckResult.Available -> { onUpdateFound(r.release); CheckState.Idle }
                    SelfUpdate.CheckResult.UpToDate -> CheckState.UpToDate
                    is SelfUpdate.CheckResult.Failed -> CheckState.Failed(r.message)
                }
            }
        }
        SettingsRow(title = "关于", subtitle = "版本、构建日期、设备信息", onClick = onAbout)
        SettingsRow(title = "免责声明", subtitle = "第三方应用、商标与数据来源", onClick = onDisclaimer)
        SettingsRow(title = "系统设置", subtitle = "打开电视自带的设置", onClick = onSystemSettings)
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val installed = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(info.lastUpdateTime))
        }.getOrDefault("—")
    }
    PageHeader("关于")
    // Big gradient badge with the name, then a plain key/value list.
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF3A8DFF), Color(0xFF8B5CF6))), RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Column {
            Text("Tandroid Launcher", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("为电视设计的简洁桌面", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(18.dp))
    // Focusable so the remote has something to land on (Back closes the page).
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.focusRequester(focus).focusable(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoLine("版本", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        InfoLine("构建日期", BuildConfig.BUILD_DATE)
        InfoLine("本机安装 / 更新", installed)
        InfoLine("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoLine("系统", "Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
        InfoLine("项目主页", "github.com/tangtongda/Tandroid-Lanucher")
        InfoLine("作者", "tangtongda")
    }
}

@Composable
private fun DisclaimerPage() {
    PageHeader("免责声明")
    val paragraphs = listOf(
        "Tandroid Launcher 是个人开发的开源桌面，免费使用，按“现状”提供，不对使用中出现的任何问题承担责任。",
        "「推荐应用」只提供各应用官方渠道（官网、官方镜像或作者在 GitHub 发布的版本）的下载入口，" +
            "安装包的版权和责任归原作者所有。本应用不提供、不内置任何影视资源或直播源。",
        "界面中出现的电视品牌、游戏主机、应用名称和图标是其各自所有者的商标，仅用于识别，与本项目无关联、未获背书。",
        "图标来自 Icons8（icons8.com）。天气数据来自中国气象局和 Open-Meteo；影视背景图和信息来自 TMDB。" +
            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
        "「主页键」无障碍服务只用于检测当前在前台的应用，以便按主页键时回到本桌面；不读取屏幕内容、不记录按键、不上传任何数据。",
        "本应用只联网获取天气、背景图和版本更新信息，不收集、不上传个人数据。",
    )
    val scroll = rememberScrollState()
    Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        paragraphs.forEachIndexed { i, text -> FocusParagraph(text, requestFocus = i == 0) }
    }
}

/** A paragraph the D-pad can step through, so a long page scrolls with ↑/↓. */
@Composable
private fun FocusParagraph(text: String, requestFocus: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Text(
        text,
        color = Color.White.copy(alpha = if (focused) 0.95f else 0.7f),
        fontSize = 14.sp,
        lineHeight = 22.sp,
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun PageHeader(title: String) {
    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Text("按返回键回到设置", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Text(value, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleColor: Color = Color.White.copy(alpha = 0.6f),
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                // On the white focused card the coloured status would be unreadable: use dark grey there.
                color = if (focused) Color.Black.copy(alpha = 0.6f) else subtitleColor,
                maxLines = 2,
            )
        }
    }
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
