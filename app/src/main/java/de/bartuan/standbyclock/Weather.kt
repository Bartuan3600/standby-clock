package de.bartuan.standbyclock

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class WeatherNow(val temp: Double, val code: Int, val max: Double, val min: Double, val hours: List<Triple<String, Double, Int>>)

object Weather {
    fun url(lat: Float, lon: Float) = String.format(Locale.US,
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min&hourly=temperature_2m,weather_code&forecast_days=2&timezone=auto", lat, lon)

    fun parse(json: String): WeatherNow? = try {
        val o = JSONObject(json); val c = o.getJSONObject("current"); val d = o.getJSONObject("daily")
        val now = c.getString("time").take(13) // yyyy-MM-ddTHH
        val h = o.getJSONObject("hourly"); val t = h.getJSONArray("time"); val tt = h.getJSONArray("temperature_2m"); val wc = h.getJSONArray("weather_code")
        var start = 0; while (start < t.length() && t.getString(start).take(13) <= now) start++
        val hours = (start until minOf(start + 3, t.length())).map { Triple(t.getString(it).substring(11, 13), tt.getDouble(it), wc.getInt(it)) }
        WeatherNow(c.getDouble("temperature_2m"), c.getInt("weather_code"),
            d.getJSONArray("temperature_2m_max").getDouble(0), d.getJSONArray("temperature_2m_min").getDouble(0), hours)
    } catch (_: Exception) { null }

    /** WMO code -> symbol */
    fun icon(code: Int) = when (code) {
        0 -> "☀"; 1, 2 -> "⛅"; 3 -> "☁"; 45, 48 -> "🌫"; in 51..67, in 80..82 -> "🌧"; in 71..77, 85, 86 -> "❄"; in 95..99 -> "⛈"; else -> "☁"
    }

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "StandbyUhr/1.2")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
    fun fetch(lat: Float, lon: Float): String? = try { get(url(lat, lon)).takeIf { parse(it) != null } } catch (_: Exception) { null }

    /** returns (name, lat, lon) or null */
    fun geocode(name: String): Triple<String, Float, Float>? = try {
        val r = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=de&name=" + URLEncoder.encode(name, "UTF-8")))
            .getJSONArray("results").getJSONObject(0)
        Triple(r.getString("name") + (r.optString("country_code").let { if (it.isNotEmpty()) " ($it)" else "" }),
            r.getDouble("latitude").toFloat(), r.getDouble("longitude").toFloat())
    } catch (_: Exception) { null }
}
