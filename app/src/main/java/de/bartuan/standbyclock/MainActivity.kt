package de.bartuan.standbyclock

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var cfg: Cfg
    private lateinit var pv: ClockView
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        cfg = Cfg(this)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 64, 48, 96) }
        fun head(t: String) = l.addView(TextView(this).apply { textSize = 19f; text = t; setPadding(0, dp(20), 0, dp(6)) })
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        fun sw(t: String, v: Boolean, f: (Boolean) -> Unit) = l.addView(Switch(this).apply { text = t; isChecked = v; setPadding(0, dp(6), 0, dp(6)); setOnCheckedChangeListener { _, on -> f(on) } })

        l.addView(TextView(this).apply { textSize = 24f; text = "Standby Uhr" })
        l.addView(TextView(this).apply { textSize = 15f; setPadding(0, 16, 0, 24)
            text = "Zeigt beim kabellosen Laden eine große Uhr im Querformat, sobald du den Bildschirm ausschaltest. " +
                "Antippen schließt die Uhr. Bitte einmal alle Berechtigungen unten erteilen." })
        status = TextView(this).apply { setPadding(0, 0, 0, 24) }; l.addView(status)
        btn("1. Benachrichtigungen erlauben") { if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        btn("2. Über anderen Apps anzeigen") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        btn("3. Vollbild-Benachrichtigungen (Android 14+)") {
            if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "Auf deiner Android-Version nicht nötig", Toast.LENGTH_SHORT).show()
        }
        btn("4. Akku-Optimierung ausschalten") {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        btn("5. Benachrichtigungszugriff (für Widget)") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        sw("Standby Uhr aktiv", Util.enabled(this)) { on -> Util.setEnabled(this, on)
            if (on) Util.startService(this) else stopService(Intent(this, StandbyService::class.java)); refresh() }

        head("Vorschau")
        pv = ClockView(this)
        l.addView(pv, LinearLayout.LayoutParams(-1, dp(180)))
        btn("Vollbild-Vorschau") { startActivity(Intent(this, ClockActivity::class.java).putExtra("preview", true)) }
        btn("Layout bearbeiten") { startActivity(Intent(this, EditorActivity::class.java)) }

        head("Helligkeit")
        val bl = TextView(this); l.addView(bl)
        val sb = SeekBar(this).apply { max = 99; progress = cfg.brightness - 1 }
        fun lbl() { bl.text = if (cfg.autoBright) "Automatisch (Lichtsensor)" else "Helligkeit: ${cfg.brightness} %" }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { if (u) { cfg.brightness = p + 1; lbl(); setWinBright(Logic.percentToBrightness(p + 1)) } }
            override fun onStartTrackingTouch(s: SeekBar) { setWinBright(Logic.percentToBrightness(cfg.brightness)) }
            override fun onStopTrackingTouch(s: SeekBar) { setWinBright(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
        })
        l.addView(sb)
        sw("Automatisch (dunkler Raum → sehr dunkel)", cfg.autoBright) { cfg.autoBright = it; sb.isEnabled = !it; lbl() }
        sb.isEnabled = !cfg.autoBright; lbl()

        head("Design")
        val themeRow = LinearLayout(this)
        val thumbs = ArrayList<android.view.View>()
        fun mark() { thumbs.forEachIndexed { i, v -> v.alpha = if (i == cfg.design) 1f else 0.55f
            (v as LinearLayout).getChildAt(1).let { (it as TextView).setTextColor(if (i == cfg.design) Color.WHITE else Color.GRAY) } } }
        Themes.ALL.forEachIndexed { i, t ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
            val cv = ClockView(this, themeOverride = i, animate = false)
            cv.isClickable = false
            box.addView(cv, LinearLayout.LayoutParams(dp(160), dp(76)))
            box.addView(TextView(this).apply { text = t.name; textSize = 12f; gravity = android.view.Gravity.CENTER })
            box.setOnClickListener { cfg.design = i; mark(); pv.reload() }
            thumbs.add(box); themeRow.addView(box)
        }
        l.addView(HorizontalScrollView(this).apply { addView(themeRow) }); mark()
        l.addView(TextView(this).apply { textSize = 12f; setTextColor(Color.GRAY); text = "Basis · Apple-Stil (StandBy …) · Nothing-Stil (Nothing …)" })
        sw("Reiner schwarzer Hintergrund (OLED)", cfg.pureBlack) { cfg.pureBlack = it; pv.invalidate() }
        l.addView(TextView(this).apply { text = "Akzentfarbe (bei Nothing/Nacht fest)" })
        val row = LinearLayout(this)
        W.COLORS.forEach { c -> row.addView(android.view.View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(2), Color.DKGRAY) }
            setOnClickListener { cfg.accent = c; pv.invalidate(); thumbs.forEach { (it as LinearLayout).getChildAt(0).invalidate() } } }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) }) }
        l.addView(HorizontalScrollView(this).apply { addView(row) })

        head("Widgets")
        sw("Uhr (immer an)", true) {}.also { (l.getChildAt(l.childCount - 1)).isEnabled = false }
        listOf(W.DATE, W.BATTERY, W.STOCKS, W.WEATHER, W.NOTIF).forEach { w -> sw(W.NAMES[w]!!, cfg.on(w)) { cfg.setOn(w, it); pv.invalidate() } }
        sw("Ladeleistung anzeigen (Watt beim Laden)", cfg.showWatts) { cfg.showWatts = it; pv.invalidate() }
        l.addView(TextView(this).apply { text = "Aktien-Symbole (Yahoo, mit Komma getrennt, max. 8)" ; setPadding(0, dp(10), 0, 0) })
        l.addView(EditText(this).apply { setText(cfg.tickers); isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { cfg.tickers = s.toString(); cfg.quotesTime = 0; pv.quotes = emptyList(); pv.invalidate() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            }) })
        l.addView(TextView(this).apply { textSize = 12f; setTextColor(Color.GRAY)
            text = "Kurse werden nur bei laufender Uhr alle 15 Min. aktualisiert (Yahoo Finance, ggf. verzögert)." })

        head("Wetter")
        val cityLbl = TextView(this).apply { text = "Ort: ${cfg.city}" }; l.addView(cityLbl)
        val cityEt = EditText(this).apply { hint = "Stadt eingeben, z. B. Berlin"; isSingleLine = true }; l.addView(cityEt)
        btn("Ort suchen & speichern") {
            val q = cityEt.text.toString().trim(); if (q.isEmpty()) return@btn
            Thread { val r = Weather.geocode(q); val wj = r?.let { Weather.fetch(it.second, it.third) }
                runOnUiThread {
                    if (r == null) Toast.makeText(this, "Ort nicht gefunden", Toast.LENGTH_SHORT).show()
                    else { cfg.city = r.first; cfg.lat = r.second; cfg.lon = r.third; cityLbl.text = "Ort: ${r.first}"
                        if (wj != null) { cfg.weather = wj; cfg.weatherTime = System.currentTimeMillis() } else cfg.weatherTime = 0
                        pv.reload() }
                } }.start()
        }
        l.addView(TextView(this).apply { textSize = 12f; setTextColor(Color.GRAY); text = "Open-Meteo, ohne GPS. Aktualisiert alle 30 Min. bei laufender Uhr." })

        setContentView(ScrollView(this).apply { addView(l) })
        Util.startService(this)
    }
    private fun setWinBright(v: Float) { window.attributes = window.attributes.apply { screenBrightness = v } }
    override fun onResume() { super.onResume(); refresh(); pv.quotes = Logic.decodeQuotes(cfg.quotes); pv.battery = Util.percent(Util.battery(this)); pv.reload() }
    private fun refresh() {
        fun ok(b: Boolean) = if (b) "✅" else "❌"
        val notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val fsi = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val bat = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        status.text = "${ok(notif)} Benachrichtigungen\n${ok(Settings.canDrawOverlays(this))} Über anderen Apps\n" +
            "${ok(fsi)} Vollbild-Benachrichtigungen\n${ok(bat)} Akku-Optimierung aus\n" +
            "${ok(NotifListener.granted(this))} Benachrichtigungszugriff (optional)\n" +
            "Kabellos am Laden: ${if (Util.isWireless(this)) "ja" else "nein"}"
    }
}
