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
        Spacer(Modifier.height(12.dp))
        // One block per category (影视 / 音乐 / 电视), in the order they first appear in the catalog.
        apps.groupBy { it.category }.forEach { (category, group) ->
        Text(category, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        // Three cards per row, sharing the width (three fixed 360dp cards ran off the right edge).
        group.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { app ->
                    StoreCard(app, states[app.id] ?: StoreState.Idle, Modifier.weight(1f)) { onInstall(app) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(20.dp))
        }
        }
    }
}

@Composable
private fun StoreCard(app: CatalogApp, state: StoreState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(96.dp).touchClickable(onClick = onClick),
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
