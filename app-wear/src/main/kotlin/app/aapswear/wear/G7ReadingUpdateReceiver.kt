package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.ComplicationUpdatePlanner
import app.aapswear.complications.G7LocalReadingResolver
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Refreshes local CGM consumers immediately after the standalone G7 app stores a reading. */
class G7ReadingUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_G7_READING_UPDATED) return

        ComplicationUpdatePlanner.allManagedProviders.forEach { provider ->
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, provider))
                .requestUpdateAll()
        }
        requestSugarliciousTileUpdates(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // TherapyStateStore deliberately remains the phone-fed source store. Do not replace
                // it with a direct G7 reading: the canonical source resolver needs both independent
                // inputs in order to apply timeout, ordering and Mobile-recovery hysteresis.
                val phoneState = TherapyStateStore(context).state.first()
                val source = WearDisplayPreferences.read(context).dataSource
                val resolved =
                    G7LocalReadingResolver.resolve(
                        context = context,
                        fallback = phoneState,
                        dataSource = source,
                    )
                val sourceState = G7LocalReadingResolver.sourceState(resolved)
                publishG7AlertMode(context, source, resolved)
                context.recordWatchDiagnostic(
                    module = "SOURCE",
                    code = "SRC-RESOLVE-200",
                    message = "Canonical CGM source resolved after direct G7 reading",
                    metadata =
                        mapOf(
                            "state" to sourceState?.name,
                            "canonicalSource" to resolved?.source?.name,
                        ),
                )

                runCatching { G7BackfillSync.sendPending(context) }
                    .onSuccess { dispatch ->
                        context.recordWatchDiagnostic(
                            module = "G7-SYNC",
                            code = if (dispatch == null) "G7-SYNC-204" else "G7-SYNC-100",
                            message = if (dispatch == null) "No pending G7 history" else "G7 history batch sent to Mobile",
                            metadata = mapOf(
                                "batchId" to dispatch?.batchId,
                                "readingCount" to dispatch?.readingIds?.size,
                            ),
                        )
                    }
                    .onFailure { error ->
                        context.recordWatchDiagnostic(
                            module = "G7-SYNC",
                            code = "G7-SYNC-503",
                            message = "G7 history remains pending until Mobile reconnects",
                            metadata = mapOf("error" to error.javaClass.simpleName),
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION_G7_READING_UPDATED = "app.aapswear.g7watch.READING_UPDATED"
    }
}
