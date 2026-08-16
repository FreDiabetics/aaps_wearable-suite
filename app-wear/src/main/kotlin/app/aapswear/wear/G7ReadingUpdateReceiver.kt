package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.AllProviders
import app.aapswear.complications.G7LocalReadingResolver
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Refreshes every local CGM consumer immediately after the standalone G7 app stores a reading. */
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
                val store = TherapyStateStore(context)
                val phoneState = store.state.first()
                val source = WearDisplayPreferences.read(context).dataSource
                val local = G7LocalReadingResolver.resolve(context, phoneState, dataSource = source)
                    ?.takeIf { it.source == app.aapswear.model.DataSourceId.DEXCOM_G7_WATCH }
                    ?: return@launch
                store.save(local)
                Wearable.getNodeClient(context).connectedNodes.await().forEach { node ->
                    runCatching {
                        Wearable.getMessageClient(context)
                            .sendMessage(node.id, WearProtocol.G7_READING_PATH, WearProtocol.encode(local))
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
