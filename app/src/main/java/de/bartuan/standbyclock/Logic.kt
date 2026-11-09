package de.bartuan.standbyclock

import kotlin.math.log10
import kotlin.math.abs

data class Quote(val symbol: String, val price: Double, val pct: Double, val currency: String)
data class Pos(val x: Float, val y: Float, val s: Float, val v: Float = 1f)

object Logic {
    /** lux -> window brightness 0.01..0.6 (dark room very dim, bright brighter) */
    fun brightnessForLux(lux: Float): Float {
        val t = (log10((lux.coerceAtLeast(0f) + 1f).toDouble()) / 3.0).toFloat().coerceIn(0f, 1f) // 0..1000 lux
        return 0.01f + t * 0.59f
    }
    fun percentToBrightness(p: Int): Float = p.coerceIn(1, 100) / 100f

    fun parseYahoo(symbol: String, json: String): Quote? {
        fun num(k: String) = Regex("\"$k\"\\s*:\\s*(-?[0-9.eE+-]+)").find(json)?.groupValues?.get(1)?.toDoubleOrNull()
        val price = num("regularMarketPrice") ?: return null
        val prev = num("chartPreviousClose") ?: num("previousClose") ?: return null
        val cur = Regex("\"currency\"\\s*:\\s*\"([A-Za-z]+)\"").find(json)?.groupValues?.get(1) ?: ""
        val pct = if (prev != 0.0) (price - prev) / prev * 100.0 else 0.0
        return Quote(symbol, price, pct, cur)
    }
    fun encodeQuotes(q: List<Quote>) = q.joinToString(";") { "${it.symbol}|${it.price}|${it.pct}|${it.currency}" }
    fun decodeQuotes(s: String): List<Quote> = s.split(";").mapNotNull {
        val p = it.split("|"); if (p.size < 4) null else {
            val pr = p[1].toDoubleOrNull(); val pc = p[2].toDoubleOrNull()
            if (pr == null || pc == null) null else Quote(p[0], pr, pc, p[3]) }
    }
    fun parseTickers(s: String) = s.split(',', ' ', ';', '\n').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().take(8)
    /** charging power in W from CURRENT_NOW (µA or mA, device dependent) and voltage (mV); <0 if unknown */
    fun watts(current: Int, mV: Int): Float {
        if (current == 0 || current == Int.MIN_VALUE || mV <= 0) return -1f
        val a = abs(current.toLong())
        val mA = if (a > 20_000) a / 1000.0 else a.toDouble()   // > 20 A as mA is impossible → value is µA
        val w = (mA * (if (mV > 100_000) mV / 1000.0 else mV.toDouble()) / 1_000_000.0).toFloat()
        return if (w in 0.05f..150f) w else -1f
    }
    fun fmtWatts(w: Float) = String.format(java.util.Locale.GERMANY, "%.1f W", w)
    fun encodePos(p: Pos) = "${p.x},${p.y},${p.s},${p.v}"
    fun decodePos(s: String?, d: Pos): Pos {
        val a = s?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return d
        return if (a.size == 4) clampPos(Pos(a[0], a[1], a[2], a[3])) else if (a.size == 3) clampPos(Pos(a[0], a[1], a[2], d.v)) else d
    }
    fun clampPos(p: Pos) = Pos(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f), p.s.coerceIn(0.3f, 3f), p.v.coerceIn(0.3f, 3f))
    fun fmtPct(p: Double) = (if (p >= 0) "+" else "") + String.format(java.util.Locale.GERMANY, "%.2f %%", p)
    fun fmtPrice(p: Double) = String.format(java.util.Locale.GERMANY, "%.2f", p)
}
