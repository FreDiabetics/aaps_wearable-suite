package app.aapswear.g7watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7SessionState
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object G7AlertPolicyStore {
    private const val PREFS = "g7_alert_policy"
    private const val KEY_WATCH_ONLY = "watch_only"

    fun isWatchOnly(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WATCH_ONLY, false)

    fun setWatchOnly(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WATCH_ONLY, enabled)
            .apply()
    }
}

internal const val G7_SIGNAL_LOSS_AFTER_MS = 11L * 60_000L

internal fun isG7SignalLoss(
    lastReadingEpochMs: Long?,
    nowEpochMs: Long,
): Boolean =
    lastReadingEpochMs != null &&
        nowEpochMs - lastReadingEpochMs >= G7_SIGNAL_LOSS_AFTER_MS

internal fun shouldPostImmediateCollectorAlert(
    watchOnly: Boolean,
    error: G7CollectorError,
    sessionState: G7SessionState,
): Boolean {
    if (!watchOnly) return false
    if (!error.recoverable) return true
    return sessionState == G7SessionState.USER_INTERVENTION_REQUIRED ||
        sessionState == G7SessionState.REQUIRES_REBOND ||
        sessionState == G7SessionState.REQUIRES_FULL_HANDSHAKE
}

internal object G7SignalLossMonitor {
    private const val REQUEST_CODE = 7011
    private const val MIN_TRIGGER_LEAD_MS = 1_000L

    fun scheduleFromState(context: Context, state: G7PersistedState) {
        if (!state.collectorEnabled) {
            cancel(context)
            return
        }
        val lastReading = state.lastReading?.timestampEpochMs ?: return
        schedule(context, lastReading + G7_SIGNAL_LOSS_AFTER_MS)
    }

    fun cancel(context: Context) {
        val pending = pendingIntent(context)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun schedule(context: Context, requestedAtEpochMs: Long) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val triggerAt = maxOf(requestedAtEpochMs, System.currentTimeMillis() + MIN_TRIGGER_LEAD_MS)
        val pending = pendingIntent(app)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }.getOrElse {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, G7SignalLossReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class G7SignalLossReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = G7SensorStateStore(context).read()
        if (!state.collectorEnabled) return

        val lastReadingAt = state.lastReading?.timestampEpochMs
        val now = System.currentTimeMillis()
        if (!isG7SignalLoss(lastReadingAt, now)) {
            G7SignalLossMonitor.scheduleFromState(context, state)
            return
        }

        if (!G7AlertPolicyStore.isWatchOnly(context)) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    context.applicationContext.recordG7Diagnostic(
                        code = "G7-SIGNAL-LOSS-SUPPRESSED",
                        message = "Direct G7 signal loss suppressed because Watch Collector Only is not selected",
                        severity = DiagnosticSeverity.INFO,
                        metadata = mapOf("lastReadingEpochMs" to lastReadingAt),
                    )
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val error = G7CollectorError(
            code = "G7-SIGNAL-LOSS",
            recoverable = true,
            occurredAtEpochMs = now,
            safeMessage = "Seit mindestens 11 Minuten kein aktueller G7-Wert. Bluetooth, Sensorreichweite und Sensorstatus prüfen; Bond, Shared Key und Sensorcode nicht löschen.",
        )
        G7ErrorNotifier.show(context, error)
    }
}
