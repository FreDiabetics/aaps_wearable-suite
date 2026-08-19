package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun shouldRestoreWearRuntime(action: String?): Boolean =
    action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

class WearRuntimeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldRestoreWearRuntime(intent.action)) return

        runCatching { StateDataLayerService.start(context) }
            .onFailure { error ->
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        context.applicationContext.recordWatchDiagnostic(
                            "RUNTIME",
                            "WATCH-FGS-503",
                            "Wear runtime foreground service could not be restored",
                            DiagnosticSeverity.ERROR,
                            mapOf("error" to error.javaClass.simpleName, "action" to intent.action),
                        )
                    } finally {
                        pending.finish()
                    }
                }
            }
    }
}
