package de.bartuan.standbyclock

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object Util {
    const val PREFS = "standby"
    fun enabled(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = c.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", v).apply()
    fun battery(c: Context): Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    fun isWireless(i: Intent?) = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == BatteryManager.BATTERY_PLUGGED_WIRELESS
    fun isWireless(c: Context) = isWireless(battery(c))
    fun percent(i: Intent?): Int {
        if (i == null) return -1
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0) -1 else l * 100 / s
    }
    fun startService(c: Context) {
        if (enabled(c)) c.startForegroundService(Intent(c, StandbyService::class.java))
    }
}
