package de.bartuan.standbyclock

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class ClockView(c: Context, val edit: Boolean = false, val themeOverride: Int? = null, val animate: Boolean = true) : View(c) {
    val cfg = Cfg(c)
    var shiftX = 0f; var shiftY = 0f
    var battery = -1
    var watts = -1f
    var quotes: List<Quote> = emptyList()
    var weather: WeatherNow? = null
    var now: Date? = null // fixed time for screenshots
    var selected: String? = null
    var onSelect: ((String?) -> Unit)? = null
    private val bounds = HashMap<String, RectF>()
    private val anchor = HashMap<String, PointF>()
    private val pos = HashMap<String, Pos>()
    private val icons = HashMap<String, Drawable?>()
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tf = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val df = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY)
    private val dens = c.resources.displayMetrics.density
    private lateinit var th: Theme
    private var acc = 0; private var fg = 0; private var sec = 0

    init { setLayerType(LAYER_TYPE_SOFTWARE, null); reload() }
    fun reload() { pos.clear(); weather = Weather.parse(cfg.weather); invalidate() }
    fun resetLayout() { cfg.resetLayout(); reload() }

    private fun dim(col: Int, f: Float) = Color.rgb((Color.red(col) * f).toInt(), (Color.green(col) * f).toInt(), (Color.blue(col) * f).toInt())
    private fun font(bold: Boolean = false) = if (th.mono) Typeface.MONOSPACE else Typeface.create(th.font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    private fun up(s: String) = if (th.mono) s.uppercase(Locale.GERMANY) else s

    override fun onDraw(cv: Canvas) {
        val h = height.toFloat(); val wd = width.toFloat(); if (h <= 0f) return
        th = Themes.get(themeOverride ?: cfg.design)
        acc = if (th.red) 0xFF9E1414.toInt() else th.fixedAccent ?: cfg.accent
        fg = th.fg ?: dim(acc, 0.85f); sec = th.sec
        drawBg(cv, wd, h)
        if (edit && cfg.grid) {
            lp.reset(); lp.color = Color.argb(28, 255, 255, 255); lp.strokeWidth = 1f
            for (i in 1 until 20) cv.drawLine(wd * i / 20, 0f, wd * i / 20, h, lp)
            for (i in 1 until 10) cv.drawLine(0f, h * i / 10, wd, h * i / 10, lp)
            lp.color = Color.argb(55, 255, 255, 255); cv.drawLine(wd / 2, 0f, wd / 2, h, lp); cv.drawLine(0f, h / 2, wd, h / 2, lp)
        }
        val t = now ?: Date()
        for (w in W.ALL) {
            if (!cfg.on(w)) continue
            val p = pos.getOrPut(w) { cfg.pos(w, Themes.def(th, w)) }; val cx = p.x * wd + if (edit) 0f else shiftX; val cy = p.y * h + if (edit) 0f else shiftY
            val s = p.s
            fun body(cv: Canvas): RectF {
            tp.reset(); tp.isAntiAlias = true; tp.textAlign = Paint.Align.CENTER
            val card = th.cards && w != W.CLOCK
            if (card) cv.saveLayer(null, null)
            val r: RectF = when (w) {
                W.CLOCK -> drawClock(cv, cx, cy, h, s, t)
                W.DATE -> { secStyle(h, s); text(cv, up(df.format(t)), cx, cy) }
                W.BATTERY -> { secStyle(h, s); text(cv, if (battery < 0) "⚡ – %" else if (watts > 0f && cfg.showWatts) "$battery % · ⚡ ${Logic.fmtWatts(watts)}" else "⚡ $battery %", cx, cy) }
                W.STOCKS -> drawStocks(cv, cx, cy, wd, h, s)
                W.WEATHER -> drawWeather(cv, cx, cy, h * 0.05f * s)
                else -> drawNotifs(cv, cx, cy, wd, h, s)
            }
            if (card) {
                lp.reset(); lp.isAntiAlias = true; lp.color = Color.argb(40, 255, 255, 255)
                lp.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                val pad = h * 0.025f; cv.drawRoundRect(RectF(r).apply { inset(-pad * 1.4f, -pad) }, h * 0.05f, h * 0.05f, lp)
                cv.restore()
            }
            return r }
            // measure first, then keep the widget fully inside the screen
            val pic = Picture(); val rr = body(pic.beginRecording(wd.toInt(), h.toInt())); pic.endRecording()
            val ext = RectF(rr); if (th.cards && w != W.CLOCK) { val pad = h * 0.025f; ext.inset(-pad * 1.4f, -pad) }
            val m = 4 * dens
            val dx = if (ext.width() > wd - 2 * m) 0f else if (ext.left < m) m - ext.left else if (ext.right > wd - m) wd - m - ext.right else 0f
            val dy = if (ext.height() > h - 2 * m) 0f else if (ext.top < m) m - ext.top else if (ext.bottom > h - m) h - m - ext.bottom else 0f
            cv.save(); cv.translate(dx, dy); cv.drawPicture(pic); cv.restore()
            val r = RectF(rr).apply { offset(dx, dy) }
            bounds[w] = r; anchor[w] = PointF(p.x * wd, p.y * h)
            if (edit) {
                lp.reset(); lp.isAntiAlias = true; lp.style = Paint.Style.STROKE; lp.strokeWidth = 3f
                lp.color = if (w == selected) acc else Color.rgb(70, 70, 70)
                lp.pathEffect = if (w == selected) null else DashPathEffect(floatArrayOf(12f, 8f), 0f)
                cv.drawRoundRect(RectF(r).apply { inset(-10f, -10f) }, 12f, 12f, lp)
            }
        }
        if (edit && (guideX.isNotEmpty() || guideY.isNotEmpty())) {
            lp.reset(); lp.isAntiAlias = true; lp.color = Color.rgb(255, 40, 40); lp.strokeWidth = max(1.5f, dens)
            guideX.forEach { cv.drawLine(it, 0f, it, h, lp) }; guideY.forEach { cv.drawLine(0f, it, wd, it, lp) }
        }
    }

    private fun drawBg(cv: Canvas, w: Float, h: Float) {
        cv.drawColor(Color.BLACK)
        if (cfg.pureBlack && !edit) return
        lp.reset(); lp.isAntiAlias = true
        when (th.bg) {
            1 -> { lp.shader = RadialGradient(w * 0.25f, h * 0.1f, w * 0.9f, intArrayOf(0xFF1B1530.toInt(), 0xFF0C1022.toInt(), Color.BLACK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, lp) }
            3 -> { lp.shader = LinearGradient(0f, 0f, w, h, intArrayOf(dim(acc, 0.28f), 0xFF20183A.toInt(), 0xFF0A0A14.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, lp) }
            2 -> { lp.color = 0xFF1E1E1E.toInt(); val step = h / 28f
                var y = step / 2; while (y < h) { var x = step / 2; while (x < w) { cv.drawCircle(x, y, step * 0.09f, lp); x += step }; y += step } }
        }
        if (th.glyph) {
            lp.reset(); lp.isAntiAlias = true; lp.style = Paint.Style.STROKE; lp.strokeCap = Paint.Cap.ROUND; lp.strokeWidth = h * 0.012f
            lp.color = 0xFF2A2A2A.toInt()
            cv.drawArc(RectF(w * 0.04f, h * 0.06f, w * 0.18f, h * 0.38f), 180f, 90f, false, lp)
            cv.drawLine(w * 0.82f, h * 0.94f, w * 0.96f, h * 0.94f, lp)
            cv.drawLine(w * 0.96f, h * 0.62f, w * 0.96f, h * 0.86f, lp)
            lp.color = dim(Themes.NRED, 0.75f); cv.drawLine(w * 0.82f, h * 0.94f, w * 0.86f, h * 0.94f, lp)
            lp.style = Paint.Style.FILL; cv.drawCircle(w * 0.11f, h * 0.06f + 0f, h * 0.012f, lp)
        }
    }

    private fun drawClock(cv: Canvas, cx: Float, cy: Float, h: Float, s: Float, t: Date): RectF {
        when (th.clock) {
            2 -> return drawAnalog(cv, cx, cy, h * 0.27f * s, t)
            6 -> return drawDots(cv, tf.format(t), cx, cy, h * 0.32f * s)
            0 -> { tp.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL); tp.textSize = h * 0.38f * s }
            1 -> { tp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); tp.textSize = h * 0.36f * s; tp.setShadowLayer(h * 0.04f * s, 0f, 0f, acc) }
            3 -> { tp.typeface = Typeface.create("sans-serif-black", Typeface.BOLD); tp.textSize = h * 0.46f * s }
            5 -> { tp.typeface = Typeface.create("sans-serif-black", Typeface.BOLD); tp.textSize = h * 0.5f * s; tp.letterSpacing = -0.04f }
            else -> { tp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); tp.textSize = h * 0.2f * s }
        }
        tp.color = if (th.clock == 3 && th.fg == null) dim(acc, 0.7f) else fg
        val r = text(cv, tf.format(t), cx, cy)
        if (th.clock == 5) { // rounded look: soften with a stroke of same colour
            tp.style = Paint.Style.STROKE; tp.strokeJoin = Paint.Join.ROUND; tp.strokeWidth = h * 0.012f * s
            text(cv, tf.format(t), cx, cy); tp.style = Paint.Style.FILL
        }
        return r
    }

    private fun drawDots(cv: Canvas, str: String, cx: Float, cy: Float, height: Float): RectF {
        val cell = height / 7f; val rad = cell * 0.38f
        val totalCols = str.sumOf { (Themes.DOTS[it]?.get(0)?.length ?: 3) + 1 } - 1
        var x0 = cx - totalCols * cell / 2; val y0 = cy - height / 2
        lp.reset(); lp.isAntiAlias = true
        for (ch in str) {
            val g = Themes.DOTS[ch] ?: arrayOf("..."); val cols = g[0].length
            for (r in 0 until 7) for (c in 0 until cols) {
                val on = g[r][c] == '#'
                lp.color = if (!on) 0xFF141414.toInt() else if (ch == ':') acc else fg
                if (on) cv.drawCircle(x0 + c * cell + cell / 2, y0 + r * cell + cell / 2, if (on) rad else rad * 0.5f, lp)
            }
            x0 += (cols + 1) * cell
        }
        return RectF(cx - totalCols * cell / 2, y0, cx + totalCols * cell / 2, y0 + height)
    }

    private fun secStyle(h: Float, s: Float) {
        tp.typeface = font(); tp.textSize = h * (if (th.clock == 4) 0.045f else 0.06f) * s
        tp.color = if (th.clock == 1) dim(acc, 0.55f) else sec
        if (th.clock == 1) tp.setShadowLayer(h * 0.015f * s, 0f, 0f, dim(acc, 0.6f))
        if (th.mono) tp.letterSpacing = 0.12f
    }

    private fun text(cv: Canvas, t: String, cx: Float, cy: Float): RectF {
        val fm = tp.fontMetrics; val wdt = tp.measureText(t)
        val base = cy - (fm.ascent + fm.descent) / 2
        val x = when (tp.textAlign) { Paint.Align.LEFT -> cx - wdt / 2; else -> cx }
        cv.drawText(t, x, base, tp)
        return RectF(cx - wdt / 2, base + fm.ascent, cx + wdt / 2, base + fm.descent)
    }

    private fun drawAnalog(cv: Canvas, cx: Float, cy: Float, r: Float, t: Date): RectF {
        val cal = Calendar.getInstance().apply { time = t }
        lp.reset(); lp.isAntiAlias = true; lp.style = Paint.Style.STROKE; lp.strokeCap = Paint.Cap.ROUND
        if (th.numerals) { lp.style = Paint.Style.FILL; lp.color = Color.argb(30, 255, 255, 255); cv.drawCircle(cx, cy, r, lp); lp.style = Paint.Style.STROKE }
        else { lp.color = Color.rgb(60, 60, 60); lp.strokeWidth = r * 0.02f; cv.drawCircle(cx, cy, r, lp) }
        for (i in 0 until 12) { val a = i * PI / 6
            if (th.numerals) {
                tp.reset(); tp.isAntiAlias = true; tp.textAlign = Paint.Align.CENTER; tp.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                tp.textSize = r * 0.18f; tp.color = sec
                text(cv, (if (i == 0) 12 else i).toString(), cx + (r * 0.8f * sin(a)).toFloat(), cy - (r * 0.8f * cos(a)).toFloat())
            } else {
                lp.strokeWidth = if (i % 3 == 0) r * 0.04f else r * 0.02f
                lp.color = if (i % 3 == 0) Color.rgb(140, 140, 140) else Color.rgb(80, 80, 80)
                cv.drawLine(cx + (r * 0.85f * sin(a)).toFloat(), cy - (r * 0.85f * cos(a)).toFloat(), cx + (r * 0.95f * sin(a)).toFloat(), cy - (r * 0.95f * cos(a)).toFloat(), lp)
            }
        }
        val m = cal.get(Calendar.MINUTE); val hr = cal.get(Calendar.HOUR) + m / 60f
        fun hand(a: Double, len: Float, wdt: Float, col: Int) { lp.color = col; lp.strokeWidth = wdt
            cv.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), lp) }
        hand(hr * PI / 6, r * 0.5f, r * 0.07f, fg)
        hand(m * PI / 30, r * 0.74f, r * 0.045f, dim(acc, 0.85f))
        lp.style = Paint.Style.FILL; lp.color = dim(acc, 0.85f); cv.drawCircle(cx, cy, r * 0.05f, lp)
        return RectF(cx - r, cy - r, cx + r, cy + r)
    }

    private fun ease(f: Float) = f * f * (3 - 2 * f)
    private fun anim(r: RectF) { if (animate) postInvalidateDelayed(33, r.left.toInt() - 4, r.top.toInt() - 4, r.right.toInt() + 4, r.bottom.toInt() + 4) }

    /** one stock at a time, switches every 4 s with fade + slide */
    private fun drawStocks(cv: Canvas, cx: Float, cy: Float, wd: Float, h: Float, s: Float): RectF {
        val q = if (quotes.isEmpty()) Logic.parseTickers(cfg.tickers).map { Quote(it, Double.NaN, 0.0, "") } else quotes
        val bw = h * 0.48f * s; val bh = h * 0.15f * s
        val rect = RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
        tp.typeface = font(); tp.textAlign = Paint.Align.CENTER; tp.clearShadowLayer()
        if (th.mono) tp.letterSpacing = 0.06f
        if (q.isEmpty()) { tp.textSize = bh * 0.3f; tp.color = sec; text(cv, "Keine Aktien", cx, cy); return rect }
        val green = if (th.red) 0xFF8A1212.toInt() else if (th.mono) 0xFFBBBBBB.toInt() else Color.rgb(70, 170, 100)
        val red = if (th.red) 0xFF5A0A0A.toInt() else if (th.mono) Themes.NRED else Color.rgb(200, 80, 80)
        val period = 4000L; val tr = 500L
        val ms = if (animate) SystemClock.uptimeMillis() else 0L
        val idx = ((ms / period) % q.size).toInt(); val ph = ms % period
        fun one(qt: Quote, alpha: Float, dy: Float) {
            if (alpha <= 0f) return
            val a = (alpha * 255).toInt()
            tp.textSize = bh * 0.3f; tp.color = if (th.clock == 1) dim(acc, 0.6f) else sec; tp.alpha = a
            text(cv, up(qt.symbol), cx, cy - bh * 0.2f + dy)
            val pr = if (qt.price.isNaN()) "–" else Logic.fmtPrice(qt.price); val pc = if (qt.price.isNaN()) "" else "  " + Logic.fmtPct(qt.pct)
            tp.textSize = bh * 0.32f; val w1 = tp.measureText(pr); val w2 = tp.measureText(pc)
            tp.textAlign = Paint.Align.LEFT; val x0 = cx - (w1 + w2) / 2; val fm = tp.fontMetrics; val base = cy + bh * 0.22f + dy - (fm.ascent + fm.descent) / 2
            tp.color = dim(fg, 0.9f); tp.alpha = a; cv.drawText(pr, x0, base, tp)
            tp.color = if (qt.pct >= 0) green else red; tp.alpha = a; cv.drawText(pc, x0 + w1, base, tp)
            tp.textAlign = Paint.Align.CENTER
        }
        cv.save(); cv.clipRect(rect)
        if (q.size > 1 && ph < tr) {
            val f = ease(ph / tr.toFloat()); val prev = (idx - 1 + q.size) % q.size
            one(q[prev], 1 - f, -f * bh * 0.4f); one(q[idx], f, (1 - f) * bh * 0.4f); anim(rect)
        } else {
            one(q[idx], 1f, 0f)
            if (animate && q.size > 1) postInvalidateDelayed(period - ph, rect.left.toInt() - 4, rect.top.toInt() - 4, rect.right.toInt() + 4, rect.bottom.toInt() + 4)
        }
        cv.restore(); return rect
    }

    private fun badge(cv: Canvas, name: String, x: Float, y: Float, sz: Float) {
        lp.reset(); lp.isAntiAlias = true
        val hue = (name.hashCode() and 0x7fffffff) % 360
        lp.color = if (th.mono) Color.rgb(60, 60, 60) else Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.75f))
        cv.drawRoundRect(RectF(x, y, x + sz, y + sz), sz * 0.25f, sz * 0.25f, lp)
        val ts = tp.textSize; val al = tp.textAlign; tp.textSize = sz * 0.55f; tp.textAlign = Paint.Align.CENTER; tp.color = Color.WHITE
        text(cv, name.take(1).uppercase(), x + sz / 2, y + sz / 2); tp.textSize = ts; tp.textAlign = al
    }

    /** vertical notification column: scrolls down one item, pauses 3 s, loops back to top */
    private fun drawNotifs(cv: Canvas, cx: Float, cy: Float, wd: Float, h: Float, s: Float): RectF {
        val p = pos[W.NOTIF]!!
        val cw = min(h * 0.5f * s, wd); val ch = min(h * 0.6f * p.v, h)
        val rect = RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
        val items = NotifListener.items
        tp.typeface = font(); tp.clearShadowLayer(); if (th.mono) tp.letterSpacing = 0.04f
        val rowH = h * 0.13f * min(s, 1.4f).coerceAtLeast(0.6f)
        if (items.isEmpty()) {
            tp.textAlign = Paint.Align.CENTER; tp.textSize = rowH * 0.24f; tp.color = sec
            text(cv, if (NotifListener.granted(context)) "Keine Benachrichtigungen" else "Zugriff fehlt", cx, cy); return rect
        }
        val maxOff = items.size * rowH - ch
        var off = 0f
        if (maxOff > 0f) {
            val steps = kotlin.math.ceil(maxOff / rowH).toInt()        // number of downward steps
            val pause = 3000L; val mv = 600L; val period = pause + mv
            val ms = if (animate) SystemClock.uptimeMillis() else 0L
            val k = ((ms / period) % (steps + 1)).toInt(); val ph = ms % period
            val from = min(k * rowH, maxOff); val to = if (k == steps) 0f else min((k + 1) * rowH, maxOff)
            off = if (ph < pause) from else from + (to - from) * ease((ph - pause) / mv.toFloat())
            if (animate) { if (ph < pause) postInvalidateDelayed(pause - ph, rect.left.toInt() - 4, rect.top.toInt() - 4, rect.right.toInt() + 4, rect.bottom.toInt() + 4) else anim(rect) }
        }
        cv.save(); cv.clipRect(rect)
        val ic = rowH * 0.62f; val pad = rowH * 0.12f
        items.forEachIndexed { i, n ->
            val top = rect.top + i * rowH - off
            if (top > rect.bottom || top + rowH < rect.top) return@forEachIndexed
            val iy = top + (rowH - ic) / 2; val ix = rect.left + pad
            val dr = icons.getOrPut(n.pkg) { try { context.packageManager.getApplicationIcon(n.pkg) } catch (_: Exception) { null } }
            if (dr != null && n.pkg != context.packageName) { dr.setBounds(ix.toInt(), iy.toInt(), (ix + ic).toInt(), (iy + ic).toInt()); dr.alpha = if (th.red) 140 else 235; dr.draw(cv) }
            else badge(cv, n.app, ix, iy, ic)
            val tx = ix + ic + pad * 1.5f; val avail = rect.right - tx - pad
            tp.textAlign = Paint.Align.LEFT
            tp.textSize = rowH * 0.22f; tp.color = if (th.clock == 1) dim(acc, 0.6f) else sec
            cv.drawText(fit(up(n.app), avail), tx, top + rowH * 0.42f, tp)
            tp.textSize = rowH * 0.25f; tp.color = dim(fg, 0.9f)
            cv.drawText(fit(n.title, avail), tx, top + rowH * 0.76f, tp)
            if (i < items.size - 1) { lp.reset(); lp.color = Color.argb(30, 255, 255, 255); cv.drawRect(tx, top + rowH - 1, rect.right - pad, top + rowH, lp) }
        }
        cv.restore()
        if (!th.cards && maxOff > 0f) {   // fade top/bottom edges
            lp.reset(); lp.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); val f = rowH * 0.35f
            lp.shader = LinearGradient(0f, rect.top, 0f, rect.top + f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cv.drawRect(rect.left, rect.top, rect.right, rect.top + f, lp)
            lp.shader = LinearGradient(0f, rect.bottom - f, 0f, rect.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            cv.drawRect(rect.left, rect.bottom - f, rect.right, rect.bottom, lp)
        }
        return rect
    }

    private fun fit(t: String, w: Float): String {
        if (tp.measureText(t) <= w) return t
        var n = t.length; while (n > 1 && tp.measureText(t.take(n) + "…") > w) n--
        return t.take(n) + "…"
    }

    private fun drawWeather(cv: Canvas, cx: Float, cy: Float, ts: Float): RectF {
        val w = weather
        tp.typeface = font(); tp.textAlign = Paint.Align.CENTER
        if (w == null) { tp.textSize = ts; tp.color = sec; return text(cv, up(cfg.city.substringBefore(" (")) + " · –°", cx, cy) }
        fun deg(d: Double) = "${d.roundToInt()}°"
        tp.textSize = ts * 1.9f; tp.color = fg; tp.alpha = 220
        val top = text(cv, Weather.icon(w.code) + " " + deg(w.temp), cx, cy - ts * 1.3f)
        tp.textSize = ts * 0.85f; tp.color = sec
        val mid = text(cv, up("H ${deg(w.max)}  T ${deg(w.min)}"), cx, cy + ts * 0.3f)
        val hrs = w.hours.joinToString("   ") { "${it.first}h ${Weather.icon(it.third)} ${deg(it.second)}" }
        val bot = if (hrs.isNotEmpty()) text(cv, hrs, cx, cy + ts * 1.5f) else mid
        return RectF(minOf(top.left, mid.left, bot.left), top.top, maxOf(top.right, mid.right, bot.right), bot.bottom)
    }

    private fun ellip(s: String, n: Int) = if (s.length > n) s.take(n - 1) + "…" else s

    // ---- editor ----
    private var lastX = 0f; private var lastY = 0f
    private val sg = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean {
            if (selected == W.NOTIF && det.previousSpanX > 0 && det.previousSpanY > 0) {
                val w = W.NOTIF; val p = pos[w]!!
                pos[w] = Logic.clampPos(p.copy(s = p.s * det.currentSpanX / det.previousSpanX, v = p.v * det.currentSpanY / det.previousSpanY)); invalidate()
            } else resize(det.scaleFactor)
            return true }
    })
    fun resize(f: Float) { val w = selected ?: return; val p = pos[w]!!; pos[w] = Logic.clampPos(p.copy(s = p.s * f)); cfg.setPos(w, pos[w]!!); invalidate() }

    val guideX = ArrayList<Float>(); val guideY = ArrayList<Float>()
    private var raw: Pos? = null
    private var lastSnap = ""
    fun boundsOf(w: String) = bounds[w]?.let { RectF(it) }

    /** Word/PowerPoint-style smart guides: align centre/edges with screen centre or other widgets, magnetic within 8 dp */
    fun snapPos(w: String, rp: Pos, cur: Pos): Pos {
        guideX.clear(); guideY.clear()
        val b = bounds[w] ?: return rp
        val wd = width.toFloat(); val h = height.toFloat()
        val cx = rp.x * wd; val cy = rp.y * h
        val an = anchor[w] ?: PointF(cur.x * wd, cur.y * h); val ox = b.centerX() - an.x; val oy = b.centerY() - an.y   // offset of drawn box vs anchor
        val hw = b.width() / 2; val hh = b.height() / 2
        val tx = arrayListOf(wd / 2); val ty = arrayListOf(h / 2)
        for (o in W.ALL) { if (o == w || !cfg.on(o)) continue; val r = bounds[o] ?: continue
            tx += listOf(r.left, r.centerX(), r.right); ty += listOf(r.top, r.centerY(), r.bottom) }
        val thr = 8 * dens
        fun best(mine: List<Float>, ts: List<Float>): Pair<Float, Float>? {
            var bd = thr + 1; var res: Pair<Float, Float>? = null
            for (m in mine) for (t in ts) { val d = abs(t - m); if (d < bd) { bd = d; res = (t - m) to t } }
            return if (bd <= thr) res else null
        }
        val bcx = cx + ox; val bcy = cy + oy
        val sx = best(listOf(bcx - hw, bcx, bcx + hw), tx); val sy = best(listOf(bcy - hh, bcy, bcy + hh), ty)
        var nx = cx; var ny = cy
        if (sx != null) { if (cfg.snap) nx += sx.first; val fx = if (cfg.snap) nx + ox else bcx
            // all lines that coincide after snapping
            tx.filter { t -> listOf(fx - hw, fx, fx + hw).any { abs(it - t) < 1f } }.distinct().let { guideX.addAll(it) }
            if (guideX.isEmpty()) guideX += sx.second }
        if (sy != null) { if (cfg.snap) ny += sy.first; val fy = if (cfg.snap) ny + oy else bcy
            ty.filter { t -> listOf(fy - hh, fy, fy + hh).any { abs(it - t) < 1f } }.distinct().let { guideY.addAll(it) }
            if (guideY.isEmpty()) guideY += sy.second }
        val key = "${sx?.second}|${sy?.second}"
        if (cfg.snap && key != lastSnap && (sx != null || sy != null)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        lastSnap = key
        return Logic.clampPos(rp.copy(x = nx / wd, y = ny / h))
    }

    fun resizeV(f: Float) { val w = selected ?: return; val p = pos[w]!!; pos[w] = Logic.clampPos(p.copy(v = p.v * f)); cfg.setPos(w, pos[w]!!); invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!edit) return super.onTouchEvent(e)
        sg.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selected = W.ALL.reversed().firstOrNull { cfg.on(it) && bounds[it]?.let { r -> RectF(r).apply { inset(-30f, -30f) }.contains(e.x, e.y) } == true }
                onSelect?.invoke(selected); lastX = e.x; lastY = e.y; raw = selected?.let { pos[it] }; invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (!sg.isInProgress && e.pointerCount == 1) {
                val w = selected ?: return true; val cur = pos[w]!!; val r0 = raw ?: cur
                val rp = Logic.clampPos(r0.copy(x = r0.x + (e.x - lastX) / width, y = r0.y + (e.y - lastY) / height)); raw = rp
                pos[w] = snapPos(w, rp, cur)
                lastX = e.x; lastY = e.y; invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> { lastX = e.getX(0); lastY = e.getY(0) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { selected?.let { cfg.setPos(it, pos[it]!!) }; guideX.clear(); guideY.clear(); raw = null; invalidate() }
        }
        return true
    }
}
