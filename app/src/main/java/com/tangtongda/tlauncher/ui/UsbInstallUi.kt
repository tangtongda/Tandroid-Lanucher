@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.data.AppStoreRepository
import com.tangtongda.tlauncher.data.UsbApk
import com.tangtongda.tlauncher.data.UsbApkRepository
import kotlinx.coroutines.launch

private sealed interface UsbState {
    data object Loading : UsbState
    data object NeedsPermission : UsbState
    data object NoDrive : UsbState
    data object NoApk : UsbState
    data class Found(val apks: List<UsbApk>) : UsbState
}

/**
 * App → 从 USB 设备安装: lists the APKs on plugged-in USB drives; OK on one opens the system
 * installer. Re-scans whenever we come back (after granting access, after an install) and when a
 * drive is plugged in or out.
 */
@Composable
fun UsbInstallPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { UsbApkRepository(context.applicationContext) }
    val store = remember { AppStoreRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UsbState>(UsbState.Loading) }
    var message by remember { mutableStateOf<String?>(null) }
    var busyPath by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            if (repo.access() != UsbApkRepository.Access.Granted) {
                state = UsbState.NeedsPermission
                return@launch
            }
            if (repo.drives().isEmpty()) {
                state = UsbState.NoDrive
                return@launch
            }
            val apks = repo.scan()
            state = if (apks.isEmpty()) UsbState.NoApk else UsbState.Found(apks)
        }
    }

    val askRuntime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            message = null
        } else {
            // No dialog came up (denied before with "don't ask again", e.g. from the Media page,
            // or the TV doesn't show it): send the user to our app's permission page instead.
            message = "请在打开的页面里进入「权限」，允许「存储」，然后返回"
            repo.openAppDetails()
        }
        reload()
    }
    // Plugging a drive in or out while the panel is open: scan again.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = reload()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LifecycleResumeEffect(Unit) {
        reload()
        onPauseOrDispose { }
    }

    val grant: () -> Unit = {
        when (repo.access()) {
            UsbApkRepository.Access.NeedsRuntimePermission -> askRuntime.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            UsbApkRepository.Access.NeedsAllFilesAccess -> repo.openAllFilesAccess()
            UsbApkRepository.Access.Granted -> reload()
        }
    }
    val install: (UsbApk) -> Unit = install@{ apk ->
        if (busyPath != null) return@install
        if (!store.canInstall()) {
            message = "请允许「安装未知应用」，返回后再按一次 OK"
            store.openInstallPermission()
            return@install
        }
        busyPath = apk.file.path
        message = "正在准备安装 ${apk.label}…"
        scope.launch {
            runCatching { store.install(repo.stage(apk)) }
                .onSuccess { message = null }
                .onFailure { message = "无法安装：${it.message ?: "读取 U 盘失败"}" }
            busyPath = null
        }
    }

    // Inline on the App tab (the page itself scrolls), capped so rows don't stretch edge to edge.
    Column(modifier.widthIn(max = 760.dp)) {
        Text(
            "插入 U 盘后，选择要安装的 APK。只查找根目录和往下 3 层文件夹。",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        message?.let {
            Text(it, color = Color(0xFFFFD27F), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }
        when (val s = state) {
            UsbState.Loading -> Hint("正在读取 U 盘…")
            UsbState.NeedsPermission -> {
                Hint("需要读取 U 盘的权限才能找到安装包。")
                Spacer(Modifier.height(14.dp))
                ActionRow(
                    "授予权限",
                    if (Build.VERSION.SDK_INT >= 30) "按 OK 打开「所有文件访问权限」，打开开关后返回"
                    else "按 OK 允许读取存储；如果没有弹出对话框，会打开应用权限页",
                    onClick = grant,
                )
            }
            UsbState.NoDrive -> {
                Hint("没有检测到 U 盘。插上后会自动刷新，也可以按 OK 重新检测。")
                Spacer(Modifier.height(14.dp))
                ActionRow("重新检测", "", onClick = ::reload)
            }
            UsbState.NoApk -> {
                Hint("U 盘里没有找到 APK。")
                Spacer(Modifier.height(14.dp))
                ActionRow("重新检测", "", onClick = ::reload)
            }
            is UsbState.Found -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.apks.forEach { apk ->
                    ApkRow(apk, busy = busyPath == apk.file.path) { install(apk) }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    UsbSurface(onClick, Modifier) { focused ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 12.sp, color = if (focused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ApkRow(apk: UsbApk, busy: Boolean, onClick: () -> Unit) {
    UsbSurface(onClick, Modifier) { focused ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                apk.icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp)) }
                    ?: Text("APK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(apk.label, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val version = apk.versionName?.let { "v$it" } ?: "无法读取版本"
                val status = when {
                    busy -> "正在准备…"
                    apk.packageName == null -> "安装包可能已损坏"
                    apk.isUpgrade -> "可更新（已装 ${apk.installedVersion}）"
                    apk.isSameOrOlder -> "已安装 ${apk.installedVersion}"
                    else -> "未安装"
                }
                val sub = Color.White.copy(alpha = 0.6f)
                Text(
                    "$version · ${"%.1f".format(apk.sizeMb)} MB · $status",
                    fontSize = 12.sp,
                    color = when {
                        focused -> Color.Black.copy(alpha = 0.6f)
                        apk.isUpgrade -> Color(0xFF8FE3A8)
                        apk.packageName == null -> Color(0xFFFF8A80)
                        else -> sub
                    },
                    maxLines = 1,
                )
                Text(
                    "${apk.drive} · ${apk.file.name}",
                    fontSize = 11.sp,
                    color = if (focused) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UsbSurface(onClick: () -> Unit, modifier: Modifier, content: @Composable (focused: Boolean) -> Unit) {
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
    ) { content(focused) }
}
