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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7ConnectionState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SessionState
import app.aapswear.g7.toCgm
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.CancellationException
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
    private var cycleWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        store = G7SensorStateStore(this)
        credentials = G7CredentialStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "G7 Direct to Watch", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            collectionJob?.cancel()
            store.save(G7SessionManager(store.read()).stop())
            cancelScheduledReconnect(this)
            finishService(startId)
            return START_NOT_STICKY
        }

        acquireCycleWakeLock()
        startForegroundCollector("Collector startet")
        scope.launch { applicationContext.recordG7Diagnostic("G7-COLLECT-100", "Collector cycle started") }
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
                G7CollectorError("G7-SETUP-001", false, System.currentTimeMillis(), "Sensor muss zuerst eingerichtet und der Collector gestartet werden"),
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
                    scope.launch {
                        applicationContext.recordG7Diagnostic(
                            protocolState.diagnosticCode(),
                            protocolState.label(),
                            metadata = mapOf("protocolState" to protocolState.name),
                        )
                    }
                },
                onSharedKey = credentials::saveSharedKey,
            )
            result.sharedKey?.let { key -> result.sensor.deviceAddress?.let { credentials.saveSharedKey(it, key) } }
            val database = G7ReadingDatabase(this)
            val reading = result.reading.toCgm(database.getLatest())
            database.insertOrIgnore(reading)
            val documentedSensor = result.sensor.copy(
                sensorStartEpochMs = result.reading.sensorStartEpochMs,
                sensorEndEpochMs = result.reading.sensorEndEpochMs,
                graceEndEpochMs = result.reading.graceEndEpochMs,
            )
            val manager = G7SessionManager(store.read().copy(sensor = documentedSensor))
            manager.authenticationSucceeded()
            val next = manager.readingReceived(reading).copy(
                sensor = documentedSensor.copy(state = result.reading.sensorState),
                connectionState = G7ConnectionState.DISCONNECTED,
                protocolState = G7ProtocolState.WAITING_FOR_NEXT_READING,
                lastSuccessfulConnectionEpochMs = System.currentTimeMillis(),
            )
            store.save(next)
            applicationContext.recordG7Diagnostic(
                "G7-DATA-200",
                "Validated G7 reading stored",
                metadata = mapOf(
                    "sequence" to reading.sequenceNumber,
                    "sensorState" to result.reading.sensorState,
                    "sensorClockSeconds" to reading.rawSourceTimestamp,
                    "displayOnly" to reading.displayOnly,
                ),
            )
            scheduleReconnect(next)
            updateForeground("${reading.glucoseMgDl.toInt()} mg/dL empfangen")
        } catch (error: G7BleException) {
            fail(store.read(), G7CollectorError(error.errorCode, error.recoverable, System.currentTimeMillis(), error.message))
        } catch (_: TimeoutCancellationException) {
            fail(store.read(), G7CollectorError("G7-BLE-111", true, System.currentTimeMillis(), "Zeitüberschreitung bei der Sensorverbindung"))
        } catch (_: CancellationException) {
            // Explicit stop/source switch: teardown is not a collector failure.
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
        scope.launch {
            applicationContext.recordG7Diagnostic(
                error.code,
                error.safeMessage,
                if (error.recoverable) DiagnosticSeverity.WARNING else DiagnosticSeverity.ERROR,
                mapOf("recoverable" to error.recoverable, "retryCount" to next.retryCount),
            )
        }
    }

    private fun startForegroundCollector(message: String) {
        val notification = notification(message)
        if (Build.VERSION.SDK_INT >= 34) {
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
            .setSmallIcon(R.drawable.ic_g7_notification)
            .setColor(0xFF6DE892.toInt())
            .setContentTitle("G7 Direct to Watch")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun scheduleReconnect(state: G7PersistedState) {
        if (!state.collectorEnabled) return
        val requestedAt = state.nextReconnectEpochMs ?: return
        val triggerAt = maxOf(requestedAt, System.currentTimeMillis() + MIN_RECONNECT_LEAD_MS)
        val pending = reconnectPendingIntent(this)
        val alarmManager = getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val exactScheduled = if (exactAllowed) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                true
            }.getOrElse {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                false
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            false
        }
        scope.launch {
            applicationContext.recordG7Diagnostic(
                if (exactScheduled) "G7-SCHED-200" else "G7-SCHED-201",
                if (exactScheduled) "Exact G7 reconnect scheduled" else "Exact alarm access unavailable; G7 reconnect timing may be delayed",
                if (exactScheduled) DiagnosticSeverity.INFO else DiagnosticSeverity.WARNING,
                mapOf(
                    "requestedAtEpochMs" to requestedAt,
                    "triggerAtEpochMs" to triggerAt,
                    "exact" to exactScheduled,
                ),
            )
        }
    }

    private fun acquireCycleWakeLock() {
        if (cycleWakeLock?.isHeld == true) return
        cycleWakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:G7Collection")
                .apply {
                    setReferenceCounted(false)
                    acquire(COLLECTION_WAKE_LOCK_TIMEOUT_MS)
                }
    }

    private fun releaseCycleWakeLock() {
        cycleWakeLock?.let { wakeLock ->
            if (wakeLock.isHeld) wakeLock.release()
        }
        cycleWakeLock = null
    }

    private fun finishService(startId: Int) {
        releaseCycleWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onDestroy() {
        releaseCycleWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "app.aapswear.g7watch.START"
        const val ACTION_STOP = "app.aapswear.g7watch.STOP"
        private const val CHANNEL = "g7_collector"
        private const val NOTIFICATION_ID = 7001
        private const val COLLECTION_WAKE_LOCK_TIMEOUT_MS = 35L * 60L * 1000L
        private const val MIN_RECONNECT_LEAD_MS = 1_000L

        /**
         * Explicit collector activation. Merely having credentials or a prepared sensor never starts
         * BLE scanning; this method is called only from a user start or a G7 data-source transition.
         */
        fun start(context: Context) {
            val app = context.applicationContext
            val stateStore = G7SensorStateStore(app)
            val current = stateStore.read()
            if (current.sensor == null || G7CredentialStore(app).read() == null) return
            if (!current.collectorEnabled) {
                stateStore.save(G7SessionManager(current).startCollector())
            }
            app.startForegroundService(
                Intent(app, G7CollectorService::class.java).setAction(ACTION_START),
            )
        }

        /** Stops collection immediately while preserving the configured sensor and credentials. */
        fun stop(context: Context) {
            val app = context.applicationContext
            val stateStore = G7SensorStateStore(app)
            stateStore.save(G7SessionManager(stateStore.read()).stop())
            cancelScheduledReconnect(app)
            app.stopService(Intent(app, G7CollectorService::class.java))
            app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        private fun reconnectPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, G7ReconnectReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun cancelScheduledReconnect(context: Context) {
            val pending = reconnectPendingIntent(context)
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
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

private fun G7ProtocolState.diagnosticCode(): String = when (this) {
    G7ProtocolState.SCANNING,
    G7ProtocolState.CONNECTING,
    G7ProtocolState.DISCOVERING,
    G7ProtocolState.DISCOVERING_SERVICES,
    G7ProtocolState.ENABLING_NOTIFICATIONS,
    -> "G7-BLE-110"
    G7ProtocolState.AUTHENTICATION_START,
    G7ProtocolState.AUTHENTICATING,
    G7ProtocolState.BONDING,
    G7ProtocolState.AUTHENTICATED,
    -> "G7-AUTH-110"
    G7ProtocolState.REQUESTING_GLUCOSE,
    G7ProtocolState.RECEIVING_GLUCOSE,
    G7ProtocolState.WAITING_FOR_NEXT_READING,
    -> "G7-DATA-110"
    else -> "G7-STATE-100"
}
