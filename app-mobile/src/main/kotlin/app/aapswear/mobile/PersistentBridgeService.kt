package app.aapswear.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder

class PersistentBridgeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var uiPreferences: SharedPreferences
    private lateinit var diagnostics: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        uiPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        diagnostics = getSharedPreferences(DIAGNOSTICS_NAME, MODE_PRIVATE)
        uiPreferences.registerOnSharedPreferenceChangeListener(this)
        diagnostics.registerOnSharedPreferenceChangeListener(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE_LIVE) {
            uiPreferences.edit().putBoolean(PREFERENCE_LIVE_NOTIFICATION, false).apply()
        }
        promoteToForeground(buildNotification())
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREFERENCE_LIVE_NOTIFICATION || sharedPreferences === diagnostics) {
            (getSystemService(NotificationManager::class.java)).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        uiPreferences.unregisterOnSharedPreferenceChangeListener(this)
        diagnostics.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val liveRequested = uiPreferences.getBoolean(PREFERENCE_LIVE_NOTIFICATION, false)
        val liveCapable = liveRequested && Build.VERSION.SDK_INT >= 36
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outlined)
            .setColor(getColor(R.color.app_accent))
            .setContentTitle(if (liveCapable) "Sugarlicious Live-Status" else "Sugarlicious ist aktiv")
            .setContentText(if (liveCapable) liveStatusText() else "AndroidAPS-Empfang und Watch-Verbindung bleiben bereit")
            .setSubText("Read-only Â· ausschlieÃŸlich lokal")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (liveCapable) {
            val disableLive = PendingIntent.getService(
                this,
                1,
                Intent(this, PersistentBridgeService::class.java).setAction(ACTION_DISABLE_LIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val disableAction = Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_notification),
                "Live beenden",
                disableLive,
            ).build()
            builder
                .setStyle(Notification.BigTextStyle().bigText(liveStatusText()))
                .addAction(disableAction)
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        return builder.build()
    }

    private fun liveStatusText(): String {
        val aaps = if (diagnostics.getString("sourceVersion", null).isNullOrBlank()) {
            "Warte auf AndroidAPS"
        } else {
            "AndroidAPS erkannt"
        }
        val watches = diagnostics.getInt("reachableWatches", 0)
        val watch = when (watches) {
            0 -> "keine Watch erreichbar"
            1 -> "Watch verbunden"
            else -> "$watches Watches verbunden"
        }
        return "$aaps Â· $watch"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hintergrundverbindung",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "HÃ¤lt den lokalen AndroidAPS- und Wear-Datenempfang sichtbar aktiv"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val PREFERENCE_LIVE_NOTIFICATION = "liveNotification"
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val CHANNEL_ID = "sugarlicious_background"
        const val NOTIFICATION_ID = 4101
        private const val PREFERENCES_NAME = "dashboard_ui"
        private const val DIAGNOSTICS_NAME = "diagnostics"
        private const val ACTION_REFRESH = "app.aapswear.action.REFRESH_PERSISTENT_NOTIFICATION"
        private const val ACTION_DISABLE_LIVE = "app.aapswear.action.DISABLE_LIVE_NOTIFICATION"

        fun start(context: Context): Boolean = startWithAction(context, null)

        fun refresh(context: Context): Boolean = startWithAction(context, ACTION_REFRESH)

        private fun startWithAction(context: Context, action: String?): Boolean = try {
            val intent = Intent(context, PersistentBridgeService::class.java).apply { this.action = action }
            context.startForegroundService(intent)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}
