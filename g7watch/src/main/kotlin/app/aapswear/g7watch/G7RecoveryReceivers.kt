package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal fun shouldRestoreG7Collector(action: String?, collectorEnabled: Boolean): Boolean =
    collectorEnabled &&
        action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

class G7BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = G7SensorStateStore(context).read()
        if (!shouldRestoreG7Collector(intent.action, state.collectorEnabled)) return

        // Lifecycle recovery must never rewrite the user's persisted enable/disable decision.
        // If Android temporarily refuses the FGS launch, keep collectorEnabled=true so a later
        // reconnect/user-visible start can recover without re-pairing or losing session state.
        runCatching { G7CollectorService.start(context) }
    }
}

class G7ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!G7SensorStateStore(context).read().collectorEnabled) return
        runCatching { G7CollectorService.start(context) }
    }
}
