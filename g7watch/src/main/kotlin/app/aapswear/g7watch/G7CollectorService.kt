package app.aapswear.g7watch

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SessionState
import app.aapswear.g7.toCgm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class G7CollectorService : Service() {
    private lateinit var store: G7SensorStateStore
    private lateinit var credentials: G7CredentialStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        store = G7SensorStateStore(this)
        credentials = G7CredentialStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "G7 Collector", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            collectionJob?.cancel()
            store.save(G7SessionManager(store.read()).stop())
            finishService(startId)
            return START_NOT_STICKY
        }

        startForegroundCollector("Collector startet")
        if (collectionJob?.isActive == true) return START_STICKY
        collectionJob = scope.launch { collectOnce(startId) }
        return START_STICKY
    }

    private suspend fun collectOnce(startId: Int) {
        val persisted = store.read()
        val configuredSensor = persisted.sensor
        if (!persisted.collectorEnabled || configuredSensor == null) {
            fail(
                persisted,
                G7CollectorError("G7-SETUP-001", false, System.currentTimeMillis(), "Sensor muss zuerst eingerichtet werden"),
            )
            finishService(startId)
            return
        }
        val storedCredentials = credentials.read()
        if (storedCredentials == null) {
            fail(
                persisted,
                G7CollectorError("G7-SETUP-002", false, System.currentTimeMillis(), "Sensorcode fehlt oder ist nicht mehr lesbar"),
            )
            finishService(startId)
            return
        }

        try {
            val collector = AndroidG7Collector(this)
            val result = collector.collect(
                initialSensor = configuredSensor,
                credentials = storedCredentials,
                onState = { protocolState ->
                    val current = store.read()
                    val next = current.copy(
                        protocolState = protocolState,
                        connectionState = protocolState.toConnectionState(),
                        sessionState = protocolState.toSessionState(),
                        lastError = null,
                    )
                    store.save(next)
                    updateForeground(protocolState.label())
                },
                onSharedKey = credentials::saveSharedKey,
            )
            result.sharedKey?.let { key -> result.sensor.deviceAddress?.let { credentials.saveSharedKey(it, key) } }
            val database = G7ReadingDatabase(this)
            val reading = result.reading.toCgm(database.getLatest())
            database.insertOrIgnore(reading)
            val manager = G7SessionManager(store.read().copy(sensor = result.sensor))
            manager.authenticationSucceeded()
            val next = manager.readingReceived(reading).copy(
                sensor = result.sensor.copy(state = result.reading.sensorState),
                connectionState = G7ConnectionState.DISCONNECTED,
                protocolState = G7ProtocolState.WAITING_FOR_NEXT_READING,
                lastSuccessfulConnectionEpochMs = System.currentTimeMillis(),
            )
            store.save(next)
            scheduleReconnect(next)
            updateForeground("${reading.glucoseMgDl.toInt()} mg/dL empfangen")
        } catch (error: G7BleException) {
            fail(store.read(), G7CollectorError(error.errorCode, error.recoverable, System.currentTimeMillis(), error.message))
        } catch (_: TimeoutCancellationException) {
            fail(store.read(), G7CollectorError("G7-BLE-111", true, System.currentTimeMillis(), "Zeitüberschreitung bei der Sensorverbindung"))
        } catch (_: SecurityException) {
            fail(store.read(), G7CollectorError("G7-PERM-401", false, System.currentTimeMillis(), "Bluetooth-Berechtigung fehlt"))
        } catch (_: Throwable) {
            fail(store.read(), G7CollectorError("G7-INT-500", true, System.currentTimeMillis(), "Unerwarteter Collector-Fehler"))
        } finally {
            finishService(startId)
        }
    }

    private fun fail(state: G7PersistedState, error: G7CollectorError) {
        val next = G7SessionManager(state).failure(error).copy(
            connectionState = G7ConnectionState.DISCONNECTED,
            protocolState = G7ProtocolState.ERROR,
        )
        store.save(next)
        scheduleReconnect(next)
        updateForeground("${error.code}: ${error.safeMessage}")
    }

    private fun startForegroundCollector(message: String) {
        val notification = notification(message)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForeground(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }

    private fun notification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, G7WatchActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_g7_collector)
            .setContentTitle("Sugarlicious G7")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun scheduleReconnect(state: G7PersistedState) {
        val at = state.nextReconnectEpochMs ?: return
        val pending = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, G7ReconnectReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun finishService(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "app.aapswear.g7watch.START"
        const val ACTION_STOP = "app.aapswear.g7watch.STOP"
        private const val CHANNEL = "g7_collector"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) = context.startForegroundService(
            Intent(context, G7CollectorService::class.java).setAction(ACTION_START),
        )

        fun stop(context: Context) = context.startService(
            Intent(context, G7CollectorService::class.java).setAction(ACTION_STOP),
        )
    }
}

private fun G7ProtocolState.toConnectionState(): G7ConnectionState = when (this) {
    G7ProtocolState.SCANNING -> G7ConnectionState.SCANNING
    G7ProtocolState.CONNECTING -> G7ConnectionState.CONNECTING
    G7ProtocolState.DISCOVERING,
    G7ProtocolState.DISCOVERING_SERVICES,
    G7ProtocolState.ENABLING_NOTIFICATIONS,
    -> G7ConnectionState.DISCOVERING
    G7ProtocolState.AUTHENTICATION_START,
    G7ProtocolState.AUTHENTICATING,
    G7ProtocolState.AUTHENTICATED,
    G7ProtocolState.BONDING,
    G7ProtocolState.REQUESTING_GLUCOSE,
    G7ProtocolState.RECEIVING_GLUCOSE,
    -> G7ConnectionState.CONNECTED
    else -> G7ConnectionState.DISCONNECTED
}

private fun G7ProtocolState.toSessionState(): G7SessionState = when (this) {
    G7ProtocolState.AUTHENTICATED,
    G7ProtocolState.REQUESTING_GLUCOSE,
    G7ProtocolState.RECEIVING_GLUCOSE,
    -> G7SessionState.ACTIVE
    G7ProtocolState.AUTHENTICATION_START,
    G7ProtocolState.AUTHENTICATING,
    G7ProtocolState.BONDING,
    -> G7SessionState.AUTHENTICATING
    G7ProtocolState.WAITING_FOR_NEXT_READING -> G7SessionState.WAITING_FOR_NEXT_READING
    G7ProtocolState.ERROR -> G7SessionState.RECOVERING
    else -> G7SessionState.INITIAL_SETUP
}

private fun G7ProtocolState.label(): String = when (this) {
    G7ProtocolState.SCANNING -> "Sensor wird gesucht"
    G7ProtocolState.CONNECTING -> "Sensor wird verbunden"
    G7ProtocolState.DISCOVERING_SERVICES -> "G7-Dienste werden geprüft"
    G7ProtocolState.ENABLING_NOTIFICATIONS -> "G7-Datenkanäle werden geöffnet"
    G7ProtocolState.AUTHENTICATION_START,
    G7ProtocolState.AUTHENTICATING,
    -> "Sensor wird authentifiziert"
    G7ProtocolState.BONDING -> "Sensor wird gekoppelt"
    G7ProtocolState.AUTHENTICATED -> "Sensor ist authentifiziert"
    G7ProtocolState.REQUESTING_GLUCOSE -> "Glukosewert wird angefordert"
    G7ProtocolState.RECEIVING_GLUCOSE -> "Glukosewert wird geprüft"
    else -> name.replace('_', ' ')
}
