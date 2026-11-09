package de.bartuan.standbyclock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
class ScreensTest {
    private val WJ = """{"current":{"time":"2026-11-02T14:00","temperature_2m":9.4,"weather_code":2},
      "daily":{"temperature_2m_max":[12.1,10],"temperature_2m_min":[3.2,2]},
      "hourly":{"time":["2026-11-02T14:00","2026-11-02T15:00","2026-11-02T16:00","2026-11-02T17:00"],
      "temperature_2m":[9.4,10.0,9.0,7.5],"weather_code":[2,3,61,0]}}"""

    @Test fun weatherParse() {
        val w = Weather.parse(WJ)!!
        assertEquals(9.4, w.temp, 1e-9); assertEquals(12.1, w.max, 1e-9); assertEquals(3, w.hours.size)
        assertEquals("15", w.hours[0].first); assertEquals("🌧", Weather.icon(61))
        assertNull(Weather.parse("{}"))
        assertTrue(Weather.url(52.520f, 13.405f).contains("latitude=52.5200"))
    }

    @Test fun render() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cfg = Cfg(ctx)
        cfg.setOn(W.WEATHER, true); cfg.weather = WJ
        cfg.tickers = "AAPL, MSFT, AMZN, NVDA, SAP.DE, TSLA"
        val q = listOf(Quote("AAPL", 169.38, 0.28, "EUR"), Quote("MSFT", 171.2, -1.12, "USD"), Quote("AMZN", 24.6, 4.8, "USD"),
            Quote("NVDA", 140.1, 2.1, "USD"), Quote("SAP.DE", 231.5, -0.4, "EUR"), Quote("TSLA", 250.0, -2.6, "USD"))
        val t = Calendar.getInstance().apply { set(2026, 10, 2, 23, 47) }.time
        val out = File(System.getProperty("user.dir"), "../screens"); out.mkdirs()
        fun shot(name: String) {
            val v = ClockView(ctx, animate = false).apply { quotes = q; battery = 82; watts = 7.5f; now = t; reload() }
            v.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 1600, 720)
            val bm = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888); v.draw(Canvas(bm))
            File(out, name).outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        NotifListener.items = listOf(NItem("a", "com.whatsapp", "WhatsApp", "Mama: Kommst du morgen zum Essen?", 5),
            NItem("b", "com.msfte.android.gm", "Gmail", "Rechnung Oktober", 4), NItem("c", "com.instagram.android", "Instagram", "pizza.demo hat ein Foto gepostet", 3),
            NItem("d", "com.spotify.music", "Spotify", "Neue Folge: Lage der Nation", 2), NItem("e", "de.db.navigator", "DB Navigator", "ICE 597 +5 Min", 1))
        cfg.setOn(W.NOTIF, true)
        cfg.design = 0; shot("20_Widgets_Klassisch.png")
        cfg.design = 9; shot("21_Widgets_Nothing_Dot.png")
        cfg.design = 5; shot("23_Widgets_StandBy_Gross.png")
        cfg.design = 2; shot("24_Widgets_Analog.png")
        cfg.design = 7; shot("22_Widgets_StandBy_Glas.png")
        // editor with smart guides: drag date close to the clock's centre line
        cfg.design = 0; cfg.grid = true
        run {
            val v = ClockView(ctx, edit = true, animate = false).apply { quotes = q; battery = 82; now = t; reload() }
            v.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 1600, 720)
            val bm = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888); v.draw(Canvas(bm))
            val d = v.boundsOf(W.DATE)!!; val c = v.boundsOf(W.CLOCK)!!
            fun ev(a: Int, x: Float, y: Float) = android.view.MotionEvent.obtain(0, 0, a, x, y, 0)
            v.onTouchEvent(ev(0, d.centerX(), d.centerY()))
            v.onTouchEvent(ev(2, d.centerX() + 30f, d.centerY() + 2f))
            v.onTouchEvent(ev(2, d.centerX() + 9f, d.centerY() + 1f))
assertTrue(v.guideX.isNotEmpty())
            v.draw(Canvas(bm)); assertEquals(c.centerX(), v.boundsOf(W.DATE)!!.centerX(), 1.5f)
            File(out, "30_Editor_Hilfslinien.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        cfg.grid = false; cfg.resetLayout()
        cfg.setOn(W.NOTIF, false)
        Themes.ALL.forEachIndexed { i, th ->
            cfg.design = i
            val v = ClockView(ctx, animate = false).apply { quotes = q; battery = 82; watts = 7.5f; now = t; reload() }
            v.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 1600, 720)
            val bm = Bitmap.createBitmap(1600, 720, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bm))
            File(out, "%02d_%s.png".format(i, th.name.replace(" ", "_").replace("ß", "ss"))).outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
