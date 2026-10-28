@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.tangtongda.tlauncher.ui

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
import androidx.compose.runtime.Composable
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

/** Full-screen detail: now, the next 12 hours, the next 7 days. Back closes it. */
@Composable
fun WeatherDetailDialog(weather: Weather?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(weatherGradient(weather)))
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

                // Next 12 hours
                Panel("逐小时预报") {
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
                Spacer(Modifier.height(14.dp))

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
                    Text("Weather data by Open-Meteo.com", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
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
