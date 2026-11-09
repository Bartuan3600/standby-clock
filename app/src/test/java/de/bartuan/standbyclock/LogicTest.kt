package de.bartuan.standbyclock

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun brightness() {
        assertEquals(0.01f, Logic.brightnessForLux(0f), 0.001f)
        assertTrue(Logic.brightnessForLux(5f) < Logic.brightnessForLux(300f))
        assertEquals(0.6f, Logic.brightnessForLux(100000f), 0.001f)
        assertEquals(0.01f, Logic.percentToBrightness(0), 0.0001f)
        assertEquals(1f, Logic.percentToBrightness(100), 0.0001f)
    }
    @Test fun yahoo() {
        val j = """{"chart":{"result":[{"meta":{"currency":"EUR","symbol":"AAPL","regularMarketPrice":132.5,"chartPreviousClose":130.0,"previousClose":129}}]}}"""
        val q = Logic.parseYahoo("AAPL", j)!!
        assertEquals(132.5, q.price, 1e-9); assertEquals(1.923, q.pct, 0.001); assertEquals("EUR", q.currency)
        assertNull(Logic.parseYahoo("X", """{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
    }
    @Test fun roundtrip() {
        val l = listOf(Quote("MSFT", 170.1, -1.5, "USD"), Quote("AMZN", 20.0, 3.0, "USD"))
        assertEquals(l, Logic.decodeQuotes(Logic.encodeQuotes(l)))
        assertTrue(Logic.decodeQuotes("").isEmpty())
        val d = Pos(0.5f, 0.5f, 1f)
        assertEquals(Pos(0.2f, 0.3f, 1.5f), Logic.decodePos(Logic.encodePos(Pos(0.2f, 0.3f, 1.5f)), d))
        assertEquals(Pos(1f, 0f, 3f), Logic.decodePos("2,-1,9", d))
        assertEquals(d, Logic.decodePos("bad", d)); assertEquals(d, Logic.decodePos(null, d))
        assertEquals(listOf("AAPL", "MSFT", "AMZN"), Logic.parseTickers(" aapl, MSFT;amzn msft"))
        assertEquals("+1,50 %", Logic.fmtPct(1.5)); assertEquals("-0,25 %", Logic.fmtPct(-0.25))
    }
    @org.junit.Test fun watts() {
        org.junit.Assert.assertEquals(7.5f, Logic.watts(-1_500_000, 5000), 0.01f)  // µA
        org.junit.Assert.assertEquals(7.5f, Logic.watts(1500, 5000), 0.01f)        // mA
        org.junit.Assert.assertEquals(-1f, Logic.watts(0, 4000), 0f)
        org.junit.Assert.assertEquals(-1f, Logic.watts(1000, 0), 0f)
        org.junit.Assert.assertEquals("7,5 W", Logic.fmtWatts(7.5f))
    }
}
