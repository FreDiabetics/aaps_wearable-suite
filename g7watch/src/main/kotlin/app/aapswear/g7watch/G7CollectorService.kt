package app.aapswear.g7watch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SessionState

class G7CollectorService : Service() {
    private lateinit var store: G7SensorStateStore

    override fun onCreate() {
        super.onCreate()
        store = G7SensorStateStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "G7 Collector", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                store.save(G7SessionManager(store.read()).stop())
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startForegroundCollector()
        }

        val persisted = store.read()
        if (!persisted.collectorEnabled || persisted.sensor == null) {
            val error = G7CollectorError(
                code = "SETUP_REQUIRED",
                recoverable = false,
                occurredAtEpochMs = System.currentTimeMillis(),
                safeMessage = "Sensor muss zuerst eingerichtet werden",
            )
            store.save(persisted.copy(sessionState = G7SessionState.USER_INTERVENTION_REQUIRED, lastError = error))
            stopSelf()
            return START_NOT_STICKY
        }

        // TODO(G7-AUTH): connect only after the validated handshake implementation exists.
        // The service deliberately does not claim a connection or schedule fake readings.
        val manager = G7SessionManager(persisted)
        val next = manager.failure(
            G7CollectorError("G7_PROTOCOL_NOT_IMPLEMENTED", false, System.currentTimeMillis(), "G7 Authentifizierung ist noch nicht implementiert"),
        )
        store.save(next)
        scheduleReconnect(next)
        stopSelf()
        return START_STICKY
    }

    private fun startForegroundCollector() {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, G7WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(app.aapswear.g7watch.R.drawable.ic_g7_collector)
            .setContentTitle("Sugarlicious G7")
            .setContentText("Collector-Grundgerüst aktiv")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun scheduleReconnect(state: G7PersistedState) {
        val at = state.nextReconnectEpochMs ?: return
        val pending = PendingIntent.getBroadcast(this, 0, Intent(this, G7ReconnectReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "app.aapswear.g7watch.START"
        const val ACTION_STOP = "app.aapswear.g7watch.STOP"
        private const val CHANNEL = "g7_collector"
        private const val NOTIFICATION_ID = 7001
        fun start(context: Context) = context.startForegroundService(Intent(context, G7CollectorService::class.java).setAction(ACTION_START))
        fun stop(context: Context) = context.startService(Intent(context, G7CollectorService::class.java).setAction(ACTION_STOP))
    }
}
