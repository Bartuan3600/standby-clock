package de.bartuan.standbyclock

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class EditorActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val v = ClockView(this, edit = true)
        v.quotes = Logic.decodeQuotes(Cfg(this).quotes)
        v.battery = Util.percent(Util.battery(this))
        val root = FrameLayout(this); root.addView(v)
        val hint = TextView(this).apply { setTextColor(Color.GRAY); text = "Widget ziehen zum Verschieben · rote Linien = ausgerichtet (rastet ein) · zwei Finger oder +/− zum Größe ändern (Benachrichtigungen: Breite & Höhe getrennt)"; setPadding(24, 12, 24, 12) }
        root.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 12, 12, 12) }
        fun btn(t: String, f: () -> Unit) = bar.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("−") { v.resize(0.9f) }; btn("+") { v.resize(1.1f) }; btn("Höhe −") { v.resizeV(0.9f) }; btn("Höhe +") { v.resizeV(1.1f) }
        val cfg = Cfg(this)
        bar.addView(CheckBox(this).apply { text = "Einrasten"; setTextColor(Color.LTGRAY); isChecked = cfg.snap; setOnCheckedChangeListener { _, b -> cfg.snap = b } })
        bar.addView(CheckBox(this).apply { text = "Raster"; setTextColor(Color.LTGRAY); isChecked = cfg.grid; setOnCheckedChangeListener { _, b -> cfg.grid = b; v.invalidate() } })
        btn("Zurücksetzen") { v.resetLayout() }; btn("Fertig") { finish() }
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        v.onSelect = { w -> hint.text = if (w == null) "Widget antippen zum Auswählen" else "Ausgewählt: ${W.NAMES[w]}" }
        setContentView(root)
    }
}
