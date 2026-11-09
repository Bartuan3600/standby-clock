package de.bartuan.standbyclock

import android.content.Context
import android.graphics.Color

object W { const val CLOCK = "clock"; const val DATE = "date"; const val BATTERY = "battery"; const val STOCKS = "stocks"; const val NOTIF = "notif"; const val WEATHER = "weather"
    val ALL = listOf(CLOCK, DATE, BATTERY, STOCKS, NOTIF, WEATHER)
    val NAMES = mapOf(CLOCK to "Uhr", DATE to "Datum", BATTERY to "Akku", STOCKS to "Aktienkurse", NOTIF to "Benachrichtigungen", WEATHER to "Wetter")
    val DEF = mapOf(CLOCK to Pos(0.57f, 0.42f, 0.9f), DATE to Pos(0.57f, 0.74f, 1f), BATTERY to Pos(0.57f, 0.83f, 1f),
        STOCKS to Pos(0.86f, 0.87f, 1f), NOTIF to Pos(0.16f, 0.5f, 1f, 1f), WEATHER to Pos(0.83f, 0.14f, 0.85f))
    val COLORS = intArrayOf(0xFF4FC3F7.toInt(), 0xFF26A69A.toInt(), 0xFF9CCC65.toInt(), 0xFFFFB300.toInt(),
        0xFFFF7043.toInt(), 0xFFEC407A.toInt(), 0xFFAB47BC.toInt(), Color.WHITE)
}

class Cfg(c: Context) {
    private val p = c.getSharedPreferences(Util.PREFS, 0)
    var brightness: Int get() = p.getInt("bright", 5); set(v) { p.edit().putInt("bright", v).apply() }
    var autoBright: Boolean get() = p.getBoolean("autoBright", false); set(v) { p.edit().putBoolean("autoBright", v).apply() }
    var design: Int get() = p.getInt("design", 0); set(v) { p.edit().putInt("design", v).apply() }
    var accent: Int get() = p.getInt("accent", W.COLORS[0]); set(v) { p.edit().putInt("accent", v).apply() }
    var tickers: String get() = p.getString("tickers", "AAPL, MSFT, SAP.DE")!!; set(v) { p.edit().putString("tickers", v).apply() }
    var quotes: String get() = p.getString("quotes", "")!!; set(v) { p.edit().putString("quotes", v).apply() }
    var quotesTime: Long get() = p.getLong("quotesTime", 0); set(v) { p.edit().putLong("quotesTime", v).apply() }
    var pureBlack: Boolean get() = p.getBoolean("pureBlack", false); set(v) { p.edit().putBoolean("pureBlack", v).apply() }
    var tickerRight: Boolean get() = p.getBoolean("tickerRight", false); set(v) { p.edit().putBoolean("tickerRight", v).apply() }
    var snap: Boolean get() = p.getBoolean("snap", true); set(v) { p.edit().putBoolean("snap", v).apply() }
    var grid: Boolean get() = p.getBoolean("grid", false); set(v) { p.edit().putBoolean("grid", v).apply() }
    var showWatts: Boolean get() = p.getBoolean("showWatts", true); set(v) { p.edit().putBoolean("showWatts", v).apply() }
    var city: String get() = p.getString("city", "Berlin")!!; set(v) { p.edit().putString("city", v).apply() }
    var lat: Float get() = p.getFloat("lat", 52.520f); set(v) { p.edit().putFloat("lat", v).apply() }
    var lon: Float get() = p.getFloat("lon", 13.405f); set(v) { p.edit().putFloat("lon", v).apply() }
    var weather: String get() = p.getString("weather", "")!!; set(v) { p.edit().putString("weather", v).apply() }
    var weatherTime: Long get() = p.getLong("weatherTime", 0); set(v) { p.edit().putLong("weatherTime", v).apply() }
    fun on(w: String) = if (w == W.CLOCK) true else p.getBoolean("w_$w", w != W.NOTIF && w != W.WEATHER)
    fun setOn(w: String, v: Boolean) = p.edit().putBoolean("w_$w", v).apply()
    fun pos(w: String, def: Pos = W.DEF[w]!!) = Logic.decodePos(p.getString("pos_$w", null), def)
    fun setPos(w: String, v: Pos) = p.edit().putString("pos_$w", Logic.encodePos(Logic.clampPos(v))).apply()
    fun resetLayout() { val e = p.edit(); W.ALL.forEach { e.remove("pos_$it") }; e.apply() }
}
