package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.AllProviders
import app.aapswear.complications.G7LocalReadingResolver
import app.aapswear.model.DataSourceId
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Refreshes local CGM consumers immediately after the standalone G7 app stores a reading. */
class G7ReadingUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_G7_READING_UPDATED) return

        AllProviders.classes.forEach { provider ->
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

                // This path is a convenience signal for the phone while Watch Direct is canonical.
                // Local history itself remains durable in the G7 collector database.
                val local =
                    resolved
                        ?.takeIf { it.source == DataSourceId.DEXCOM_G7_WATCH }
                        ?: return@launch

                Wearable
                    .getNodeClient(context)
                    .connectedNodes
                    .await()
                    .forEach { node ->
                        runCatching {
                            Wearable
                                .getMessageClient(context)
                                .sendMessage(
                                    node.id,
                                    WearProtocol.G7_READING_PATH,
                                    WearProtocol.encode(local),
                                )
                                .await()
                        }
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
