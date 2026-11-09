package de.bartuan.standbyclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { try { Util.startService(c) } catch (_: Exception) {} }
}
