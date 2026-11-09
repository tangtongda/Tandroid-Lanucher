@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.HomeButtonService

/** Is the Home button wired to us? Phones: the real default-home setting. Google TV: our accessibility service. */
object DefaultHome {
    fun isTv(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun isDefault(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    fun serviceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, HomeButtonService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun homeButtonOk(context: Context) = isDefault(context) || serviceEnabled(context)

    /** True while the prompt is on screen, so other pop-ups (HDMI) wait instead of stacking on top. */
    private var homePromptVisible by mutableStateOf(false)
    internal var updatePromptVisible by mutableStateOf(false)

    /** Any of our own prompts (default launcher / update) on screen. */
    val promptVisible: Boolean get() = homePromptVisible || updatePromptVisible

    /** Just the default-launcher prompt: the update prompt waits for it. */
    internal val homePromptShowing: Boolean get() = homePromptVisible

    internal fun setPromptVisible(visible: Boolean) { homePromptVisible = visible }
}

/**
 * Shown on start (and re-checked whenever we come back to the front) while Home doesn't lead here.
 * TV: explains the 无障碍 switch and opens that settings page. Phone: the system "default home app" dialog.
 */
@Composable
fun DefaultHomePrompt() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("default_home", Context.MODE_PRIVATE) }
    var neverAsk by remember { mutableStateOf(prefs.getBoolean("never", false)) }
    var laterThisSession by rememberSaveable { mutableStateOf(false) }
    var ok by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        ok = DefaultHome.homeButtonOk(context)
        onPauseOrDispose { }
    }
    val show = !(ok || neverAsk || laterThisSession)
    DisposableEffect(show) {
        DefaultHome.setPromptVisible(show)
        onDispose { DefaultHome.setPromptVisible(false) }
    }
    if (!show) return

    val tv = remember { DefaultHome.isTv(context) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ok = DefaultHome.homeButtonOk(context)
    }
    val openSettings: () -> Unit = {
        val roles = context.getSystemService(RoleManager::class.java)
        when {
            tv -> context.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME) ->
                runCatching { roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                    .onFailure { context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) }
            else -> context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    val primary = remember { FocusRequester() }
    Dialog(onDismissRequest = { laterThisSession = true }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(520.dp)
                    .background(Color(0xF21C2028), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                    .padding(26.dp),
            ) {
                Text("设为默认启动器", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val body = if (tv) {
                    "这台电视不能更换默认桌面，所以遥控器的主页键还会回到电视自带的桌面。\n\n" +
                        "在「设置 → 无障碍」里打开「Tandroid Launcher 主页键」，按主页键就会回到这里。" +
                        "它只检测前台是哪个应用，不读取屏幕内容。"
                } else {
                    "把 Tandroid Launcher 设为默认主屏幕应用，按主页键就会回到这里。"
                }
                Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PromptButton(if (tv) "打开无障碍设置" else "设为默认", primary = true, onClick = openSettings,
                        modifier = Modifier.focusRequester(primary))
                    PromptButton("以后再说", primary = false, onClick = { laterThisSession = true })
                    PromptButton("不再提示", primary = false, onClick = {
                        prefs.edit().putBoolean("never", true).apply()
                        neverAsk = true
                    })
                }
            }
        }
    }
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
internal fun PromptButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp))
    }
}
