@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.tangtongda.tlauncher.data.Weather
import com.tangtongda.tlauncher.data.WeatherKind

/**
 * Sky colors per condition, iOS Weather style: deeper at the top, lighter towards the bottom.
 * The clear-day pair is sampled from the iOS Weather card (#285B91 → #789DC3); the others
 * follow the same light-at-the-bottom recipe.
 */
fun weatherGradient(weather: Weather?): List<Color> = when {
    weather == null -> listOf(Color(0xFF285B91), Color(0xFF789DC3))
    !weather.isDay -> when (weather.kind) {
        WeatherKind.Clear, WeatherKind.PartlyCloudy -> listOf(Color(0xFF0B1730), Color(0xFF2C4470))
        WeatherKind.Drizzle, WeatherKind.Rain, WeatherKind.Thunder -> listOf(Color(0xFF15192A), Color(0xFF3A4560))
        else -> listOf(Color(0xFF1C2333), Color(0xFF4A5468))
    }
    else -> when (weather.kind) {
        WeatherKind.Clear -> listOf(Color(0xFF285B91), Color(0xFF789DC3))
        WeatherKind.PartlyCloudy -> listOf(Color(0xFF3A6290), Color(0xFF8AA8CB))
        WeatherKind.Cloudy -> listOf(Color(0xFF51606F), Color(0xFF9AA6B3))
        WeatherKind.Fog -> listOf(Color(0xFF68717C), Color(0xFFADB5BE))
        WeatherKind.Drizzle, WeatherKind.Rain -> listOf(Color(0xFF3A4A5E), Color(0xFF7C8CA0))
        WeatherKind.Snow -> listOf(Color(0xFF6B84A1), Color(0xFFBFCEDF))
        WeatherKind.Thunder -> listOf(Color(0xFF2B2843), Color(0xFF66638A))
    }
}

// ---------- Animated sky (hero background + detail screen), like Apple's Weather app ----------

/** Normalised particle: position 0..1, size/speed factor 0..1, phase 0..1. */
private class Particle(val x: Float, val y: Float, val s: Float, val p: Float)

private fun particles(count: Int, seed: Int): List<Particle> {
    val r = java.util.Random(seed.toLong())
    return List(count) { Particle(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) }
}

private fun Float.wrap(m: Float) = ((this % m) + m) % m

/**
 * Today's weather as a living sky: sun glow or moon and stars, drifting clouds, rain, snow,
 * lightning, fog. Everything is drawn on one Canvas from a single clock that is read only in the
 * draw phase, so it never recomposes, and particle counts stay small for TV GPUs.
 */
@Composable
fun WeatherScene(weather: Weather?, modifier: Modifier = Modifier) {
    val kind = weather?.kind ?: WeatherKind.Clear
    val isDay = weather?.isDay ?: true
    val colors = weatherGradient(weather)
    val clock = remember { mutableFloatStateOf(0f) } // seconds
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    val stars = remember { particles(70, 1) }
    val clouds = remember { particles(6, 2) }
    val drops = remember { particles(150, 3) }
    val flakes = remember { particles(90, 4) }

    Canvas(modifier.fillMaxSize()) {
        val t = clock.floatValue
        drawRect(Brush.verticalGradient(colors))
        val clear = kind == WeatherKind.Clear || kind == WeatherKind.PartlyCloudy
        if (clear && isDay) drawSun(t, rays = if (kind == WeatherKind.Clear) 1f else 0.5f)
        if (clear && !isDay) drawNight(t, stars)
        when (kind) {
            WeatherKind.Clear -> Unit
            WeatherKind.PartlyCloudy -> drawClouds(t, clouds, count = 3, alpha = 0.55f, dark = false)
            WeatherKind.Fog -> drawFog(t)
            WeatherKind.Snow -> {
                drawClouds(t, clouds, count = 5, alpha = 0.35f, dark = false)
                drawSnow(t, flakes)
            }
            else -> { // Cloudy, Drizzle, Rain, Thunder
                drawClouds(t, clouds, count = 6, alpha = 0.45f, dark = kind != WeatherKind.Cloudy)
                when (kind) {
                    WeatherKind.Drizzle -> drawRain(t, drops, count = 60, speed = 0.7f)
                    WeatherKind.Rain -> drawRain(t, drops, count = 130, speed = 1f)
                    WeatherKind.Thunder -> {
                        drawRain(t, drops, count = 150, speed = 1.15f)
                        drawLightning(t)
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * Sun in the top-right corner, half off-screen, with light shafts fanning down-left across the
 * sky. Each shaft is a thin wedge filled with a radial fade, swaying and breathing slowly.
 */
private fun DrawScope.drawSun(t: Float, rays: Float) {
    val c = Offset(size.width * 0.92f, size.height * 0.02f)
    val len = size.width * 1.25f
    if (rays > 0f) {
        val shaft = Brush.radialGradient(
            listOf(Color(0xFFFFF6D5).copy(alpha = 0.30f), Color(0xFFFFF1C4).copy(alpha = 0.10f), Color.Transparent),
            center = c, radius = len,
        )
        for (i in 0 until 9) {
            // Fan from almost straight down (100°) to almost horizontal-left (172°); y points down.
            val deg = 100f + i * 9f + 2.5f * kotlin.math.sin(t * 0.15f + i * 1.7f)
            val half = 1.2f + (i % 3) * 1.1f // shaft width in degrees
            val breathe = rays * (0.45f + 0.55f * (0.5f + 0.5f * kotlin.math.sin(t * 0.35f + i * 2.3f)))
            val a1 = Math.toRadians((deg - half).toDouble())
            val a2 = Math.toRadians((deg + half).toDouble())
            val path = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + (len * kotlin.math.cos(a1)).toFloat(), c.y + (len * kotlin.math.sin(a1)).toFloat())
                lineTo(c.x + (len * kotlin.math.cos(a2)).toFloat(), c.y + (len * kotlin.math.sin(a2)).toFloat())
                close()
            }
            drawPath(path, shaft, alpha = breathe)
        }
    }
    val pulse = 1f + 0.04f * kotlin.math.sin(t * 0.8f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF4C2).copy(alpha = 0.65f), Color(0xFFFFE08A).copy(alpha = 0.2f), Color.Transparent),
            center = c, radius = size.width * 0.42f * pulse,
        ),
        radius = size.width * 0.42f * pulse, center = c,
    )
    drawCircle(Color(0xFFFFFBEA), radius = size.minDimension * 0.09f, center = c)
}

private fun DrawScope.drawNight(t: Float, stars: List<Particle>) {
    stars.forEach { s ->
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(t * (0.6f + s.s * 1.6f) + s.p * 6.28f))
        drawCircle(
            Color.White.copy(alpha = 0.8f * twinkle),
            radius = 0.6f.dp.toPx() + s.s * 1.4f.dp.toPx(),
            center = Offset(s.x * size.width, s.y * size.height * 0.65f),
        )
    }
    val c = Offset(size.width * 0.8f, size.height * 0.18f)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = c, radius = size.width * 0.16f),
        radius = size.width * 0.16f, center = c,
    )
    drawCircle(Color(0xFFF2F4FA), radius = size.minDimension * 0.05f, center = c)
}

/**
 * One cloud in the ☁️ shape (flat bottom, small bump left, big dome in the middle, bump right),
 * 1 unit wide, built once as a single merged path so overlaps don't show as darker seams.
 */
private val unitCloud: Path by lazy {
    fun circle(cx: Float, cy: Float, r: Float) = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
    }
    val base = Path().apply {
        addRoundRect(RoundRect(0f, 0.30f, 1f, 0.56f, CornerRadius(0.13f, 0.13f)))
    }
    listOf(
        circle(0.27f, 0.33f, 0.17f), // left bump
        circle(0.52f, 0.24f, 0.24f), // big dome
        circle(0.77f, 0.36f, 0.15f), // right bump
    ).fold(base) { acc, p -> Path.combine(PathOperation.Union, acc, p) }
}

/** Light from above: bright top, cooler shaded underside (in unit-cloud coordinates). */
private val lightCloud = Brush.verticalGradient(listOf(Color.White, Color(0xFFE6ECF3), Color(0xFFC9D3DF)), startY = 0f, endY = 0.56f)
private val darkCloud = Brush.verticalGradient(listOf(Color(0xFFD5DBE3), Color(0xFFA9B2BF), Color(0xFF7D8796)), startY = 0f, endY = 0.56f)

/** Clouds drifting slowly to the right and wrapping around; bigger ones are nearer and more opaque. */
private fun DrawScope.drawClouds(t: Float, clouds: List<Particle>, count: Int, alpha: Float, dark: Boolean) {
    val brush = if (dark) darkCloud else lightCloud
    clouds.take(count)
        .sortedBy { it.s } // far (small) first, near (big) on top
        .forEachIndexed { i, c ->
            val w = size.width * (0.22f + 0.22f * c.s)
            val span = size.width + w * 2
            val x = (c.x * span + t * size.width * (0.004f + 0.008f * c.s)).wrap(span) - w
            val y = size.height * (0.04f + 0.40f * (i.toFloat() / count) + 0.06f * c.y)
            withTransform({
                translate(x, y)
                scale(w, w, pivot = Offset.Zero)
            }) {
                drawPath(unitCloud, brush, alpha = (alpha * (0.65f + 0.5f * c.s)).coerceAtMost(0.95f))
            }
        }
}

private fun DrawScope.drawRain(t: Float, drops: List<Particle>, count: Int, speed: Float) {
    val stroke = 1.4f.dp.toPx()
    drops.take(count).forEach { d ->
        val len = size.height * (0.035f + 0.03f * d.s)
        val y = (d.y * size.height + t * size.height * speed * (0.9f + 0.5f * d.s)).wrap(size.height + len) - len
        val x = (d.x * size.width - y * 0.12f).wrap(size.width)
        drawLine(
            Color.White.copy(alpha = 0.18f + 0.22f * d.p),
            start = Offset(x, y), end = Offset(x - len * 0.12f, y + len),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(t: Float, flakes: List<Particle>) {
    flakes.forEach { f ->
        val r = 1.2f.dp.toPx() + f.s * 2.8f.dp.toPx()
        val y = (f.y * size.height + t * size.height * (0.04f + 0.05f * f.s)).wrap(size.height + r * 2) - r
        val x = f.x * size.width + kotlin.math.sin(t * 0.7f + f.p * 6.28f) * 14.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.55f + 0.35f * f.p), radius = r, center = Offset(x, y))
    }
}

/** A double flash every ~8 s. */
private fun DrawScope.drawLightning(t: Float) {
    val phase = t % 8f
    val a = when {
        phase < 0.08f -> 0.45f
        phase in 0.18f..0.26f -> 0.3f
        else -> 0f
    }
    if (a > 0f) drawRect(Color.White.copy(alpha = a))
}

private fun DrawScope.drawFog(t: Float) {
    for (i in 0 until 4) {
        val bandH = size.height * 0.16f
        val y = size.height * (0.15f + i * 0.17f)
        val shift = (t * size.width * (0.01f + i * 0.004f)).wrap(size.width)
        val brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent),
            startX = shift - size.width * 0.6f, endX = shift + size.width * 0.6f,
        )
        drawRect(brush, topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, bandH))
    }
}

/** The home-screen tile: sky gradient, big temperature, condition and city. */
@Composable
fun WeatherArtwork(weather: Weather?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(weatherGradient(weather))),
        contentAlignment = Alignment.Center,
    ) {
        if (weather == null) {
            Text("天气", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            return@Box
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weather.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("${weather.temperature}°", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    weather.city.ifBlank { weather.description },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Cities offered by 切换城市 (typing on a TV remote is painful, so a fixed list). null = automatic. */
private val pickableCities: List<String?> = listOf(
    null, "北京", "上海", "广州", "深圳", "杭州", "苏州", "南京",
    "成都", "重庆", "武汉", "西安", "天津", "长沙", "郑州", "青岛",
    "厦门", "福州", "合肥", "济南", "沈阳", "哈尔滨", "昆明", "三亚",
    "无锡", "宁波", "大连", "南宁", "贵阳", "香港", "澳门", "台北",
)

/** Full-screen detail: now, the next 12 hours, the next 7 days. Back closes it. */
@Composable
fun WeatherDetailDialog(
    weather: Weather?,
    onDismiss: () -> Unit,
    city: String? = null,
    onPickCity: (String?) -> Unit = {},
) {
    var picking by remember { mutableStateOf(false) }
    val cityButton = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
        WeatherScene(weather)
        // The only focusable thing on this screen: focus it so OK opens the city list.
        PillButton(
            text = "📍 ${city ?: "自动定位"} · 切换城市",
            onClick = { picking = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .zIndex(1f) // above the forecast content, which is composed after it
                .focusRequester(cityButton),
        )
        LaunchedEffect(picking) { if (!picking) runCatching { cityButton.requestFocus() } }
        if (picking) {
            CityPicker(
                current = city,
                onPick = {
                    picking = false
                    onPickCity(it)
                },
                onClose = { picking = false },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            if (weather == null) {
                Text("正在获取天气…", color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            Column(Modifier.fillMaxSize()) {
                // Now
                Text(weather.city.ifBlank { "当前位置" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weather.emoji, fontSize = 52.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("${weather.temperature}°", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(weather.description, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        if (weather.high != null && weather.low != null) {
                            Text("最高 ${weather.high}°  最低 ${weather.low}°", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                        }
                        Text(
                            listOfNotNull(
                                weather.feelsLike?.let { "体感 $it°" },
                                weather.humidity?.let { "湿度 $it%" },
                                weather.windKmh?.let { "风速 $it km/h" },
                            ).joinToString("  ·  "),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Next 12 hours (Open-Meteo only; the CMA fallback has no hourly data)
                if (weather.hourly.isNotEmpty()) Panel("逐小时预报") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        weather.hourly.forEach { h ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(h.time, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                Text(h.emoji, fontSize = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
                                Text("${h.temperature}°", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (weather.hourly.isNotEmpty()) Spacer(Modifier.height(14.dp))

                // Next 7 days
                Panel("7 天预报") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        weather.daily.forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(d.emoji, fontSize = 22.sp, modifier = Modifier.padding(vertical = 4.dp))
                                Text("${d.high}°", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${d.low}°", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                d.rainChance?.takeIf { it > 0 }?.let {
                                    Text("💧$it%", color = Color(0xFFBFE3FF), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth()) {
                    Text("按返回键关闭", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(weather.source, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
    }
}

/** Grid of cities over a dim layer; the current choice is focused first. Back closes it. */
@Composable
private fun CityPicker(current: String?, onPick: (String?) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        Modifier
            .zIndex(2f)
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 64.dp, vertical = 40.dp),
    ) {
        Column {
            Text("选择城市", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            pickableCities.chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        PillButton(
                            text = c ?: "📍 自动定位",
                            selected = c == current,
                            onClick = { onPick(c) },
                            modifier = if (c == current) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("自动定位按网络 IP 判断城市，可能不准；选好后会记住", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
