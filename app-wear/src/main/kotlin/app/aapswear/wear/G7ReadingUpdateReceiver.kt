package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.ComplicationUpdatePlanner
import app.aapswear.complications.G7LocalReadingResolver
import app.aapswear.model.DiagnosticSeverity
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun g7ReadingUpdateApplicationContext(context: Context): Context = context.applicationContext

/** Refreshes local CGM consumers immediately after the standalone G7 app stores a reading. */
class G7ReadingUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_G7_READING_UPDATED) return

        // Android wraps manifest receivers in a restricted Context that must not bind services.
        // Tile refresh can bind to System UI, so all asynchronous receiver work uses the
        // unrestricted process-wide application Context instead.
        val appContext = g7ReadingUpdateApplicationContext(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    ComplicationUpdatePlanner.allManagedProviders.forEach { provider ->
                        ComplicationDataSourceUpdateRequester
                            .create(appContext, ComponentName(appContext, provider))
                            .requestUpdateAll()
                    }
                }.onFailure { error ->
                    appContext.recordWatchDiagnostic(
                        module = "UI",
                        code = "COMP-REFRESH-503",
                        message = "Complication refresh after direct G7 reading failed",
                        severity = DiagnosticSeverity.WARNING,
                        metadata = mapOf("error" to error.javaClass.simpleName),
                    )
                }
                runCatching { requestSugarliciousTileUpdates(appContext) }
                    .onFailure { error ->
                        appContext.recordWatchDiagnostic(
                            module = "UI",
                            code = "TILE-REFRESH-503",
                            message = "Tile refresh after direct G7 reading failed",
                            severity = DiagnosticSeverity.WARNING,
                            metadata = mapOf("error" to error.javaClass.simpleName),
                        )
                    }

                // TherapyStateStore deliberately remains the phone-fed source store. Do not replace
                // it with a direct G7 reading: the canonical source resolver needs both independent
                // inputs in order to apply timeout, ordering and Mobile-recovery hysteresis.
                val phoneState = TherapyStateStore(appContext).state.first()
                val source = WearDisplayPreferences.read(appContext).dataSource
                val resolved =
                    G7LocalReadingResolver.resolve(
                        context = appContext,
                        fallback = phoneState,
                        dataSource = source,
                    )
                val sourceState = G7LocalReadingResolver.sourceState(resolved)
                publishG7AlertMode(appContext, source, resolved)
                appContext.recordWatchDiagnostic(
                    module = "SOURCE",
                    code = "SRC-RESOLVE-200",
                    message = "Canonical CGM source resolved after direct G7 reading",
                    metadata =
                        mapOf(
                            "state" to sourceState?.name,
                            "canonicalSource" to resolved?.source?.name,
                        ),
                )

                runCatching { G7BackfillSync.sendPending(appContext) }
                    .onSuccess { dispatch ->
                        appContext.recordWatchDiagnostic(
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
                        appContext.recordWatchDiagnostic(
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
