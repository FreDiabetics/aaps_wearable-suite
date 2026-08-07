package app.aapswear.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.AllProviders
import app.aapswear.model.GlucoseSample
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StateDataLayerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            runCatching { requestLatestState(this@StateDataLayerService) }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            when (event.dataItem.uri.path) {
                WearProtocol.COMPLICATION_PRESET_PATH -> {
                    persistComplicationPreset(event)
                }

                WearProtocol.STATE_PATH -> {
                    persistTherapyState(event)
                }
            }
        }
    }

    private fun persistComplicationPreset(event: DataEvent) {
        val ids = runCatching {
            DataMapItem
                .fromDataItem(event.dataItem)
                .dataMap
                .getIntegerArrayList("ids")
        }.getOrNull()
            .orEmpty()
            .filter { it in 1..27 }
            .distinct()
            .take(MAX_PRESET_ITEMS)

        getSharedPreferences(
            COMPLICATION_SETUP_PREFS,
            Context.MODE_PRIVATE,
        )
            .edit()
            .putString(COMPLICATION_PRESET_KEY, ids.joinToString(","))
            .apply()
    }

    private fun persistTherapyState(event: DataEvent) {
        val incoming = runCatching {
            WearProtocol.decode(event.dataItem.data ?: return)
        }.getOrNull() ?: return

        scope.launch {
            val store = TherapyStateStore(this@StateDataLayerService)
            val old = store.state.first()
            val now = System.currentTimeMillis()

            val mergedByTimestamp = linkedMapOf<Long, GlucoseSample>()

            old?.glucoseHistory
                .orEmpty()
                .forEach { mergedByTimestamp[it.measuredAtEpochMs] = it }

            incoming.glucoseHistory
                .forEach { mergedByTimestamp[it.measuredAtEpochMs] = it }

            incoming.glucose?.let {
                mergedByTimestamp[it.measuredAtEpochMs] =
                    GlucoseSample(
                        valueMgDl = it.valueMgDl,
                        measuredAtEpochMs = it.measuredAtEpochMs,
                    )
            }

            val history = mergedByTimestamp
                .values
                .asSequence()
                .filter {
                    it.valueMgDl in 20.0..1000.0 &&
                        now - it.measuredAtEpochMs <= HISTORY_WINDOW_MS &&
                        it.measuredAtEpochMs <= now + FUTURE_TOLERANCE_MS
                }
                .sortedBy { it.measuredAtEpochMs }
                .toList()
                .takeLast(MAX_HISTORY_POINTS)

            store.save(incoming.copy(glucoseHistory = history))
            requestAllComplicationUpdates()
        }
    }

    private fun requestAllComplicationUpdates() {
        AllProviders.classes.forEach { provider ->
            ComplicationDataSourceUpdateRequester
                .create(
                    this,
                    ComponentName(this, provider),
                )
                .requestUpdateAll()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val HISTORY_WINDOW_MS = 24 * 60 * 60_000L
        private const val FUTURE_TOLERANCE_MS = 5 * 60_000L
        private const val MAX_HISTORY_POINTS = 300

        private const val COMPLICATION_SETUP_PREFS = "complication_setup"
        private const val COMPLICATION_PRESET_KEY = "selected_ids"
        private const val MAX_PRESET_ITEMS = 4
    }
}

suspend fun requestLatestState(context: Context): Int {
    val nodes = Wearable.getNodeClient(context).connectedNodes.await()

    nodes.forEach { node ->
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(
                    node.id,
                    WearProtocol.REQUEST_PATH,
                    byteArrayOf(),
                )
                .await()
        }
    }

    return nodes.size
}
