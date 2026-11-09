package de.bartuan.standbyclock

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings

class StandbyService : Service() {
    companion object {
        const val CH_SVC = "svc"; const val CH_FSI = "fsi"; const val ID_SVC = 1; const val ID_FSI = 2
        const val ACTION_CLOSE = "de.bartuan.standbyclock.CLOSE"
    }
    private var wasWireless = false

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> if (Util.isWireless(c)) launchClock()
                Intent.ACTION_BATTERY_CHANGED, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    val w = if (i.action == Intent.ACTION_BATTERY_CHANGED) Util.isWireless(i) else Util.isWireless(c)
                    if (w && !wasWireless) launchClock()
                    if (!w && wasWireless) closeClock()
                    wasWireless = w
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SVC, "Hintergrunddienst", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_FSI, "Uhr starten", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH_SVC).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Standby Uhr aktiv").setContentText("Startet beim kabellosen Laden")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_SVC, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID_SVC, n)
        wasWireless = Util.isWireless(this)
        registerReceiver(rx, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Util.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    private fun launchClock() {
        if (!Util.enabled(this)) return
        val i = Intent(this, ClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        try {
            @Suppress("DEPRECATION")
            val wl = getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "standby:wake")
            wl.acquire(3000)
        } catch (_: Exception) {}
        if (Settings.canDrawOverlays(this)) {
            try { startActivity(i); return } catch (_: Exception) {}
        }
        val pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CH_FSI).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Standby Uhr").setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(ID_FSI, n)
    }

    private fun closeClock() {
        getSystemService(NotificationManager::class.java).cancel(ID_FSI)
        sendBroadcast(Intent(ACTION_CLOSE).setPackage(packageName))
    }

    override fun onDestroy() { try { unregisterReceiver(rx) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
