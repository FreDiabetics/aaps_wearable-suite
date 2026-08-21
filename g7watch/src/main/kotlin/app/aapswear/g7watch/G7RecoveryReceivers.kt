package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

internal fun shouldRestoreG7Collector(action: String?, collectorEnabled: Boolean): Boolean =
    collectorEnabled &&
        (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED)

/**
 * Very short alarm→FGS CPU handoff. The normal bounded cycle WakeLock in G7CollectorService takes
 * over immediately after the service starts. The timeout guarantees that a failed service launch
 * cannot leave the CPU held indefinitely.
 */
internal object G7WakeHandoff {
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        release()
        wakeLock =
            context.applicationContext
                .getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:G7AlarmHandoff")
                .apply {
                    setReferenceCounted(false)
                    acquire(HANDOFF_TIMEOUT_MS)
                }
    }

    @Synchronized
    fun release() {
        wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wakeLock = null
    }

    private const val HANDOFF_TIMEOUT_MS = 15_000L
}

class G7BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = G7SensorStateStore(context).read()
        if (!shouldRestoreG7Collector(intent.action, state.collectorEnabled)) return

        G7CgmAlarmCoordinator.restore(context)
        // Lifecycle recovery must never rewrite the user's persisted enable/disable decision.
        // If Android temporarily refuses the FGS launch, keep collectorEnabled=true so a later
        // reconnect/user-visible start can recover without re-pairing or losing session state.
        runCatching { G7CollectorService.start(context) }
    }
}

class G7ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!G7SensorStateStore(context).read().collectorEnabled) return
        val now = System.currentTimeMillis()
        G7CollectorDiagnosticStore(context).markScheduledAlarmReceived(now)
        G7WakeHandoff.acquire(context)
        runCatching { G7CollectorService.startScheduledReconnect(context) }
            .onFailure { G7WakeHandoff.release() }
    }
}
