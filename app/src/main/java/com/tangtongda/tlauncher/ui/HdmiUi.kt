@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.Icon
import com.tangtongda.tlauncher.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.data.HdmiInput

/**
 * "HDMI 2 已连接" card, bottom-right like a TV notification. 切换 is focused, so one OK switches;
 * Back or 取消 dismisses. The view model drops it by itself after 30 s.
 */
@Composable
fun HdmiPromptDialog(input: HdmiInput, onSwitch: () -> Unit, onDismiss: () -> Unit) {
    val switchButton = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(input) { runCatching { switchButton.requestFocus() } }
        Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp), contentAlignment = Alignment.BottomEnd) {
            Column(
                Modifier
                    .width(340.dp)
                    .background(Color(0xF21C2028), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InputIcon(input.label)
                    Spacer(Modifier.width(14.dp))
                    Text("${input.label} 已连接", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Text("检测到有设备接入或开机，要切换到这个输入吗？", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton("切换", primary = true, onClick = onSwitch, modifier = Modifier.focusRequester(switchButton))
                    DialogButton("取消", primary = false, onClick = onDismiss)
                }
            }
        }
    }
}

private enum class InputKind(val logo: String?) {
    PlayStation("ic_input_playstation"), Xbox("ic_input_xbox"), Nintendo("ic_input_nintendo"),
    Mac("ic_input_mac"), Windows("ic_input_windows"), Other(null),
}

/** Recognise a console or computer from the input's name (custom label or HDMI-CEC device name). */
private fun inputKind(label: String): InputKind = when {
    Regex("playstation|\\bps ?[2-5]\\b|\\bps\\b|sony interactive", RegexOption.IGNORE_CASE).containsMatchIn(label) -> InputKind.PlayStation
    Regex("xbox", RegexOption.IGNORE_CASE).containsMatchIn(label) -> InputKind.Xbox
    Regex("nintendo", RegexOption.IGNORE_CASE).containsMatchIn(label) -> InputKind.Nintendo
    Regex("\\bmac|imac|apple", RegexOption.IGNORE_CASE).containsMatchIn(label) -> InputKind.Mac
    Regex("windows|\\bpc\\b|computer|laptop|desktop|电脑|笔记本", RegexOption.IGNORE_CASE).containsMatchIn(label) -> InputKind.Windows
    else -> InputKind.Other
}

/**
 * Consoles: the brand's logo if one was dropped into res/drawable (ic_input_playstation.png,
 * ic_input_xbox.png, ic_input_nintendo.png), otherwise a generic game-controller icon.
 * Everything else: an HDMI port icon.
 */
@Composable
private fun InputIcon(label: String) {
    val context = LocalContext.current
    val kind = inputKind(label)
    val logo = remember(kind) {
        kind.logo?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    Box(
        Modifier.size(44.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != 0) {
            Image(painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(30.dp))
        } else {
            val generic = when (kind) {
                InputKind.Other, InputKind.Mac, InputKind.Windows -> R.drawable.ic_input_hdmi
                else -> R.drawable.ic_input_gamepad
            }
            Icon(
                painterResource(generic),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun DialogButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 26.dp, vertical = 9.dp))
    }
}
