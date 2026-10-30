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
    /** Attribution line shown in the detail view. */
    val source: String = "Weather data by Open-Meteo.com",
)

/**
 * Two free, key-less providers, so one being blocked or down doesn't leave the tile empty:
 *
 * - Open-Meteo (data CC BY 4.0, "Weather data by Open-Meteo.com"): current + hourly + 7 days.
 * - 中国气象局 weather.cma.cn: reachable on mainland networks, locates the TV by its IP, and
 *   gives current + 7 days (no hourly).
 *
 * Location: `weather.lat`/`weather.lon` or `weather.city` from local.properties; otherwise the
 * TV's IP location, from CMA (works in China) or ipwho.is (elsewhere).
 * Order: Open-Meteo for the place → CMA → nothing. Cached 30 min; stale data on failure.
 */
class WeatherRepository(context: Context) {

    private val cache = JsonCache(context)

    private data class Place(val lat: Double, val lon: Double, val name: String)

    /**
     * @param city picked on the TV (天气 → 切换城市), or null for automatic. A picked city is looked
     * up as a CMA station (works in China), else geocoded by Open-Meteo (works elsewhere).
     */
    suspend fun load(city: String? = null): Weather? = withContext(Dispatchers.IO) {
        if (city.isNullOrBlank()) auto() else forCity(city)
    }

    private fun forCity(city: String): Weather? {
        val station = runCatching { cmaStation(city) }.onFailure { Log.w(TAG, "cma station: ${it.message}") }.getOrNull()
        val cma = station?.let { id -> runCatching { cmaRaw(id) }.onFailure { Log.w(TAG, "cma: ${it.message}") }.getOrNull() }
        val place = cma?.let { placeFromCma(it)?.copy(name = city) }
            ?: runCatching { geocode(city) }.getOrNull()
        return place?.let { p -> runCatching { openMeteo(p) }.onFailure { Log.w(TAG, "open-meteo: ${it.message}") }.getOrNull() }
            ?: cma?.let { runCatching { parseCma(it).copy(city = city) }.getOrNull() }
            ?: null.also { Log.w(TAG, "no weather for $city") }
    }

    private fun auto(): Weather? {
        val configured = configuredPlace()
        // Auto-location: CMA first; it is the provider most likely to work on a Chinese network
        // and also tells us where we are (city + coordinates) for Open-Meteo.
        val cma = if (configured == null) runCatching { cmaRaw() }.onFailure { Log.w(TAG, "cma: ${it.message}") }.getOrNull() else null
        val place = configured
            ?: cma?.let { placeFromCma(it) }
            ?: runCatching { ipPlace() }.getOrNull()
        return place?.let { p -> runCatching { openMeteo(p) }.onFailure { Log.w(TAG, "open-meteo: ${it.message}") }.getOrNull() }
            ?: runCatching { parseCma(cma ?: cmaRaw()) }.onFailure { Log.w(TAG, "cma fallback: ${it.message}") }.getOrNull()
            ?: null.also { Log.w(TAG, "no weather provider reachable") }
    }

    /** CMA station id for a city name ("58457|杭州|Hangzhou|中国" → 58457); exact name match preferred. */
    private fun cmaStation(city: String): String? {
        val q = URLEncoder.encode(city, "UTF-8")
        val json = cache.get("cma:ac:$city", ttlMs = 30L * 24 * 60 * 60 * 1000) {
            httpGet("https://weather.cma.cn/api/autocomplete?q=$q&limit=10", mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://weather.cma.cn/"))
        } ?: return null
        val list = JSONObject(json).optJSONArray("data") ?: return null
        val rows = (0 until list.length()).map { list.optString(it).split('|') }.filter { it.size >= 2 }
        return (rows.firstOrNull { it[1] == city } ?: rows.firstOrNull())?.get(0)
    }

    private fun openMeteo(place: Place): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"
        val json = cache.get("weather2:${place.lat},${place.lon}", ttlMs = 30 * 60 * 1000L) { httpGet(url) }
            ?: return null
        return parse(JSONObject(json), place.name)
    }

    // ---------- 中国气象局 ----------

    /** Without a station id the API answers for the caller's IP location. */
    private fun cmaRaw(stationId: String? = null): JSONObject {
        val query = stationId?.let { "?stationid=$it" }.orEmpty()
        val json = cache.get("cma:view$query", ttlMs = 30 * 60 * 1000L) {
            httpGet("https://weather.cma.cn/api/weather/view$query", mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://weather.cma.cn/"))
        } ?: error("no data")
        val root = JSONObject(json)
        if (root.optInt("code", -1) != 0) error("code ${root.optInt("code")}")
        return root.getJSONObject("data")
    }

    private fun placeFromCma(data: JSONObject): Place? {
        val loc = data.optJSONObject("location") ?: return null
        val lat = loc.optDouble("latitude")
        val lon = loc.optDouble("longitude")
        if (lat.isNaN() || lon.isNaN()) return null
        return Place(lat, lon, loc.optString("name"))
    }

    private fun parseCma(data: JSONObject): Weather {
        val now = data.getJSONObject("now")
        val days = data.optJSONArray("daily")
        val hour = java.time.LocalTime.now().hour
        val isDay = hour in 6..17
        val today = days?.optJSONObject(0)
        val code = today?.optInt(if (isDay) "dayCode" else "nightCode", 9999) ?: 9999
        val text = today?.optString(if (isDay) "dayText" else "nightText").orEmpty()
        val kind = cmaKind(code)
        val daily = (0 until (days?.length() ?: 0)).map { i ->
            val d = days!!.getJSONObject(i)
            val date = runCatching { LocalDate.parse(d.getString("date").replace('/', '-')) }.getOrNull()
            DayForecast(
                label = when (i) {
                    0 -> "今天"
                    1 -> "明天"
                    else -> date?.let { weekday(it.dayOfWeek) } ?: ""
                },
                high = d.optDouble("high").roundToInt(),
                low = d.optDouble("low").roundToInt(),
                emoji = emojiOf(cmaKind(d.optInt("dayCode", 9999)), true),
                rainChance = null,
            )
        }
        return Weather(
            city = data.optJSONObject("location")?.optString("name").orEmpty(),
            temperature = now.getDouble("temperature").roundToInt(),
            feelsLike = now.optDouble("feelst").takeIf { !it.isNaN() && it < 999 }?.roundToInt(),
            humidity = now.optDouble("humidity").takeIf { !it.isNaN() && it < 999 }?.roundToInt(),
            windKmh = now.optDouble("windSpeed").takeIf { !it.isNaN() && it < 999 }?.let { (it * 3.6).roundToInt() },
            high = daily.firstOrNull()?.high,
            low = daily.firstOrNull()?.low,
            description = text.ifBlank { textOf(kind) },
            emoji = emojiOf(kind, isDay),
            kind = kind,
            isDay = isDay,
            hourly = emptyList(),
            daily = daily,
            source = "数据来源：中国气象局",
        )
    }

    /** CMA / 中国天气 phenomenon codes. */
    private fun cmaKind(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.Clear
        1 -> WeatherKind.PartlyCloudy
        2 -> WeatherKind.Cloudy
        4, 5 -> WeatherKind.Thunder
        7 -> WeatherKind.Drizzle
        3, 8, 9, 10, 11, 12, 19, 21, 22, 23, 24, 25, 301 -> WeatherKind.Rain
        6, 13, 14, 15, 16, 17, 26, 27, 28, 302 -> WeatherKind.Snow
        18, 20, 29, 30, 31, 32, 49, 53, 54, 55, 56, 57, 58 -> WeatherKind.Fog
        else -> WeatherKind.Cloudy
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

    /** From local.properties, or null for automatic (IP) location. */
    private fun configuredPlace(): Place? = runCatching {
        val lat = BuildConfig.WEATHER_LAT.toDoubleOrNull()
        val lon = BuildConfig.WEATHER_LON.toDoubleOrNull()
        val city = BuildConfig.WEATHER_CITY
        if (lat != null && lon != null) return@runCatching Place(lat, lon, city)
        if (city.isBlank()) null else geocode(city)
    }.getOrNull()

    private fun geocode(city: String): Place? {
        val q = URLEncoder.encode(city, "UTF-8")
        val json = cache.get("geo:$city", ttlMs = 30L * 24 * 60 * 60 * 1000) {
            httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=zh")
        } ?: return null
        val r = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
        return Place(r.getDouble("latitude"), r.getDouble("longitude"), city)
    }

    private fun ipPlace(): Place? {
        // Approximate location from the public IP (city level), refreshed daily.
        val json = cache.get("ipgeo", ttlMs = 24 * 60 * 60 * 1000L) { httpGet("https://ipwho.is/?lang=zh-CN") }
            ?: return null
        val r = JSONObject(json)
        if (!r.optBoolean("success", false)) return null
        return Place(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("city"))
    }

    private companion object {
        const val TAG = "Weather"
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
