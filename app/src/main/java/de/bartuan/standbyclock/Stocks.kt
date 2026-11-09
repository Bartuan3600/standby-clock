package de.bartuan.standbyclock

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Stocks {
    /** Blocking; call from background thread. */
    fun fetch(symbols: List<String>): List<Quote> = symbols.mapNotNull { s ->
        try {
            val c = URL("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(s, "UTF-8")}?range=1d&interval=1d")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) StandbyUhr/1.1")
            val body = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
            Logic.parseYahoo(s, body)
        } catch (_: Exception) { null }
    }
}
