package de.bartuan.standbyclock

import android.app.Activity
import android.app.NotificationManager
import android.content.*
import android.hardware.*
import android.os.*
import android.view.*
import kotlin.random.Random

class ClockActivity : Activity(), SensorEventListener {
    private lateinit var view: ClockView
    private lateinit var cfg: Cfg
    private val h = Handler(Looper.getMainLooper())
    private var preview = false
    private var sm: SensorManager? = null

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == StandbyService.ACTION_CLOSE) { if (!preview) finish(); return }
            if (!preview && !Util.isWireless(i)) finish() else { view.battery = Util.percent(i); view.invalidate() }
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            view.shiftX = Random.nextInt(-30, 31) * resources.displayMetrics.density / 2
            view.shiftY = Random.nextInt(-20, 21) * resources.displayMetrics.density / 2
            view.battery = Util.percent(Util.battery(this@ClockActivity)); view.invalidate()
            h.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    private val stockTick = object : Runnable {
        override fun run() {
            if (cfg.on(W.STOCKS)) {
                val syms = Logic.parseTickers(cfg.tickers)
                Thread {
                    val q = Stocks.fetch(syms)
                    if (q.isNotEmpty()) { cfg.quotes = Logic.encodeQuotes(q); cfg.quotesTime = System.currentTimeMillis() }
                    h.post { view.quotes = Logic.decodeQuotes(cfg.quotes).filter { it.symbol in syms }; view.invalidate() }
                }.start()
            }
            h.postDelayed(this, 15 * 60_000L)
        }
    }

    private val weatherTick = object : Runnable {
        override fun run() {
            if (cfg.on(W.WEATHER)) { val la = cfg.lat; val lo = cfg.lon
                Thread { Weather.fetch(la, lo)?.let { cfg.weather = it; cfg.weatherTime = System.currentTimeMillis() }
                    h.post { view.weather = Weather.parse(cfg.weather); view.invalidate() } }.start() }
            h.postDelayed(this, 30 * 60_000L)
        }
    }

    private var wSmooth = -1f
    private val wattTick = object : Runnable { override fun run() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val cur = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val mv = Util.battery(this@ClockActivity)?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val w = Logic.watts(cur, mv)
        wSmooth = if (w < 0f) -1f else if (wSmooth < 0f) w else wSmooth * 0.6f + w * 0.4f
        if (view.watts != wSmooth) { view.watts = wSmooth; view.invalidate() }
        h.postDelayed(this, 3000L)
    } }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        preview = intent.getBooleanExtra("preview", false)
        cfg = Cfg(this)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setBright(if (cfg.autoBright) 0.05f else Logic.percentToBrightness(cfg.brightness))
        getSystemService(NotificationManager::class.java).cancel(StandbyService.ID_FSI)
        view = ClockView(this)
        view.quotes = Logic.decodeQuotes(cfg.quotes)
        view.setOnClickListener { finish() }
        setContentView(view)
        hideBars()
    }
    private fun setBright(v: Float) { window.attributes = window.attributes.apply { screenBrightness = v } }

    private fun hideBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onSensorChanged(e: SensorEvent) { setBright(Logic.brightnessForLux(e.values[0])) }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onResume() {
        super.onResume(); hideBars()
        val f = IntentFilter().apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(StandbyService.ACTION_CLOSE) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(rx, f)
        if (!preview && !Util.isWireless(this)) { finish(); return }
        view.reload()
        NotifListener.onChange = { h.post { view.invalidate() } }
        if (cfg.autoBright) { sm = getSystemService(SensorManager::class.java)
            sm?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } }
        h.removeCallbacks(tick); tick.run()
        h.removeCallbacks(wattTick); wattTick.run()
        h.removeCallbacks(stockTick)
        val age = System.currentTimeMillis() - cfg.quotesTime
        h.postDelayed(stockTick, if (age > 15 * 60_000L) 0L else 15 * 60_000L - age)
        val wage = System.currentTimeMillis() - cfg.weatherTime
        h.postDelayed(weatherTick, if (wage > 30 * 60_000L) 0L else 30 * 60_000L - wage)
    }
    override fun onPause() {
        super.onPause(); try { unregisterReceiver(rx) } catch (_: Exception) {}
        sm?.unregisterListener(this); NotifListener.onChange = null
        h.removeCallbacksAndMessages(null)
    }
}
