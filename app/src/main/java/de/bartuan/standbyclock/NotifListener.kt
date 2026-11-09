package de.bartuan.standbyclock

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class NItem(val key: String, val pkg: String, val app: String, val title: String, val time: Long)

class NotifListener : NotificationListenerService() {
    companion object {
        @Volatile var items: List<NItem> = emptyList()
        var onChange: (() -> Unit)? = null
        fun granted(c: Context): Boolean {
            val f = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            return f.contains(ComponentName(c, NotifListener::class.java).flattenToString())
        }
    }
    override fun onListenerConnected() = rebuild()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = rebuild()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rebuild()

    private fun rebuild() {
        val list = try { activeNotifications?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
        items = list.filter { s ->
            val n = s.notification
            !s.isOngoing && s.packageName != packageName && s.packageName != "android" &&
                s.packageName != "com.android.systemui" && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                n.category != Notification.CATEGORY_SERVICE && n.category != Notification.CATEGORY_TRANSPORT
        }.sortedByDescending { it.postTime }.mapNotNull { s ->
            val ex = s.notification.extras
            val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return@mapNotNull null
            val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(s.packageName, 0)).toString() } catch (_: Exception) { s.packageName }
            NItem(s.key, s.packageName, app, title, s.postTime)
        }.take(8)
        onChange?.invoke()
    }
}
