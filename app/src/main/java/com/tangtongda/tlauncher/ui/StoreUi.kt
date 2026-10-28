@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.data.CatalogApp
import com.tangtongda.tlauncher.data.StoreState

/** 应用 → 推荐应用: apps we can install for the user. Each card hides once that app is installed. */
@Composable
fun StoreSection(
    apps: List<CatalogApp>,
    states: Map<String, StoreState>,
    onInstall: (CatalogApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("推荐应用", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            apps.forEach { app -> StoreCard(app, states[app.id] ?: StoreState.Idle) { onInstall(app) } }
        }
    }
}

@Composable
private fun StoreCard(app: CatalogApp, state: StoreState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(360.dp).height(96.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
        ),
    ) {
        Row(Modifier.fillMaxHeight().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Placeholder icon: gradient square with the initials (we don't ship the app's own icon).
            Box(
                Modifier.size(68.dp).background(Brush.linearGradient(app.colors), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(app.name.take(2), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(app.description, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                StatusLine(state)
            }
        }
    }
}

@Composable
private fun StatusLine(state: StoreState) {
    val (text, color) = when (state) {
        StoreState.Idle -> "按 OK 安装" to Color(0xFF9FD3FF)
        StoreState.Resolving -> "正在获取最新版本…" to Color.White.copy(alpha = 0.8f)
        is StoreState.Downloading -> "下载中 ${(state.progress * 100).toInt()}%" to Color.White.copy(alpha = 0.8f)
        StoreState.Installing -> "正在打开安装程序…" to Color.White.copy(alpha = 0.8f)
        StoreState.NeedsPermission -> "请允许「安装未知应用」，返回后再按一次 OK" to Color(0xFFFFD27F)
        is StoreState.Failed -> state.message to Color(0xFFFF8A80)
    }
    if (state is StoreState.Downloading) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(state.progress).height(3.dp).background(Color.White, RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.height(4.dp))
    }
    Text(text, color = color, fontSize = 12.sp, maxLines = 1)
}
