package com.tangtongda.tlauncher.data

import android.content.Context
import android.util.Log
import com.tangtongda.tlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

enum class WeatherKind { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Thunder }

data class HourForecast(val time: String, val temperature: Int, val emoji: String)

data class DayForecast(val label: String, val high: Int, val low: Int, val emoji: String, val rainChance: Int?)

data class Weather(
    val city: String,
    val temperature: Int,
    val feelsLike: Int?,
    val humidity: Int?,
    val windKmh: Int?,
    val high: Int?,
    val low: Int?,
    val description: String,
    val emoji: String,
    val kind: WeatherKind,
    val isDay: Boolean,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
)

/**
 * Weather from Open-Meteo (free, no API key; data CC BY 4.0, "Weather data by Open-Meteo.com").
 *
 * Location, in order: `weather.lat`/`weather.lon`, or `weather.city` (Open-Meteo geocoding), from
 * local.properties; otherwise the TV's approximate location from its public IP (ipwho.is).
 * Cached 30 min; if a refresh fails the last weather keeps being shown.
 */
class WeatherRepository(context: Context) {

    private val cache = JsonCache(context)

    private data class Place(val lat: Double, val lon: Double, val name: String)

    suspend fun load(): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val place = place() ?: return@runCatching null
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7"
            val json = cache.get("weather2:${place.lat},${place.lon}", ttlMs = 30 * 60 * 1000L) { httpGet(url) }
                ?: return@runCatching null
            parse(JSONObject(json), place.name)
        }.onFailure { Log.w("Weather", "load failed: ${it.message}") }.getOrNull()
    }

    private fun parse(root: JSONObject, city: String): Weather {
        val current = root.getJSONObject("current")
        val code = current.optInt("weather_code")
        val isDay = current.optInt("is_day", 1) == 1
        val kind = kindOf(code)

        // Next 12 hours, starting at the current hour.
        val hourly = mutableListOf<HourForecast>()
        root.optJSONObject("hourly")?.let { h ->
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val codes = h.getJSONArray("weather_code")
            val days = h.optJSONArray("is_day")
            val nowHour = current.optString("time").take(13) // "2026-10-28T14"
            var start = (0 until times.length()).firstOrNull { times.getString(it).take(13) == nowHour } ?: 0
            if (start < 0) start = 0
            for (i in start until minOf(start + 12, times.length())) {
                val t = times.getString(i)
                hourly += HourForecast(
                    time = if (i == start) "现在" else t.substring(11, 16),
                    temperature = temps.getDouble(i).roundToInt(),
                    emoji = emojiOf(kindOf(codes.getInt(i)), days?.optInt(i, 1) != 0),
                )
            }
        }

        val daily = mutableListOf<DayForecast>()
        root.optJSONObject("daily")?.let { d ->
            val dates = d.getJSONArray("time")
            for (i in 0 until dates.length()) {
                val date = LocalDate.parse(dates.getString(i))
                daily += DayForecast(
                    label = when (i) {
                        0 -> "今天"
                        1 -> "明天"
                        else -> weekday(date.dayOfWeek)
                    },
                    high = d.getJSONArray("temperature_2m_max").getDouble(i).roundToInt(),
                    low = d.getJSONArray("temperature_2m_min").getDouble(i).roundToInt(),
                    emoji = emojiOf(kindOf(d.getJSONArray("weather_code").getInt(i)), true),
                    rainChance = d.optJSONArray("precipitation_probability_max")?.optInt(i, -1)?.takeIf { it >= 0 },
                )
            }
        }

        return Weather(
            city = city,
            temperature = current.getDouble("temperature_2m").roundToInt(),
            feelsLike = current.optDouble("apparent_temperature").takeIf { !it.isNaN() }?.roundToInt(),
            humidity = current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
            windKmh = current.optDouble("wind_speed_10m").takeIf { !it.isNaN() }?.roundToInt(),
            high = daily.firstOrNull()?.high,
            low = daily.firstOrNull()?.low,
            description = textOf(kind),
            emoji = emojiOf(kind, isDay),
            kind = kind,
            isDay = isDay,
            hourly = hourly,
            daily = daily,
        )
    }

    private fun place(): Place? {
        val lat = BuildConfig.WEATHER_LAT.toDoubleOrNull()
        val lon = BuildConfig.WEATHER_LON.toDoubleOrNull()
        val city = BuildConfig.WEATHER_CITY
        if (lat != null && lon != null) return Place(lat, lon, city)

        if (city.isNotBlank()) {
            val q = URLEncoder.encode(city, "UTF-8")
            val json = cache.get("geo:$city", ttlMs = 30L * 24 * 60 * 60 * 1000) {
                httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=zh")
            } ?: return null
            val r = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
            return Place(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
        }

        // Approximate location from the public IP (city level), refreshed daily.
        val json = cache.get("ipgeo", ttlMs = 24 * 60 * 60 * 1000L) { httpGet("https://ipwho.is/?lang=zh-CN") }
            ?: return null
        val r = JSONObject(json)
        if (!r.optBoolean("success", false)) return null
        return Place(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("city"))
    }

    /** WMO weather interpretation codes. */
    private fun kindOf(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.Clear
        1, 2 -> WeatherKind.PartlyCloudy
        3 -> WeatherKind.Cloudy
        45, 48 -> WeatherKind.Fog
        51, 53, 55, 56, 57 -> WeatherKind.Drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.Rain
        71, 73, 75, 77, 85, 86 -> WeatherKind.Snow
        95, 96, 99 -> WeatherKind.Thunder
        else -> WeatherKind.Cloudy
    }

    private fun textOf(kind: WeatherKind) = when (kind) {
        WeatherKind.Clear -> "晴"
        WeatherKind.PartlyCloudy -> "多云"
        WeatherKind.Cloudy -> "阴"
        WeatherKind.Fog -> "雾"
        WeatherKind.Drizzle -> "小雨"
        WeatherKind.Rain -> "雨"
        WeatherKind.Snow -> "雪"
        WeatherKind.Thunder -> "雷阵雨"
    }

    private fun emojiOf(kind: WeatherKind, isDay: Boolean) = when (kind) {
        WeatherKind.Clear -> if (isDay) "☀️" else "🌙"
        WeatherKind.PartlyCloudy -> if (isDay) "⛅" else "☁️"
        WeatherKind.Cloudy -> "☁️"
        WeatherKind.Fog -> "🌫️"
        WeatherKind.Drizzle -> "🌦️"
        WeatherKind.Rain -> "🌧️"
        WeatherKind.Snow -> "❄️"
        WeatherKind.Thunder -> "⛈️"
    }

    private fun weekday(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
