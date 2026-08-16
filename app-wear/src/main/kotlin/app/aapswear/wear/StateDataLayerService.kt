package app.aapswear.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.ActiveComplicationRegistry
import app.aapswear.complications.AllProviders
import app.aapswear.complications.ComplicationUpdatePlanner
import app.aapswear.model.SugarliciousComplicationIds
import app.aapswear.model.GlucoseSample
import app.aapswear.protocol.WatchRuntimeStatus
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class StateDataLayerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            runCatching {
                requestLatestState(this@StateDataLayerService)
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            when (event.dataItem.uri.path) {
                WearProtocol.COMPLICATION_PRESET_PATH ->
                    persistComplicationPreset(event)

                WearProtocol.WATCH_CONFIG_PATH ->
                    persistWatchConfig(event)

                WearProtocol.STATE_PATH ->
                    persistTherapyState(event)
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.WATCH_FACE_APPLY_PATH ->
                applyWatchFace(event)

            WearProtocol.WATCH_RUNTIME_REQUEST_PATH ->
                scope.launch {
                    sendRuntimeStatus(
                        applicationContext,
                        event.sourceNodeId,
                    )
                }
        }
    }

    private fun applyWatchFace(event: MessageEvent) {
        val index =
            event.data
                .decodeToString()
                .toIntOrNull()
                ?.coerceAtLeast(0)
                ?: return

        val appContext = applicationContext
        val sourceNodeId = event.sourceNodeId

        watchFacePushScope.launch {
            watchFacePushMutex.withLock {
                val status =
                    SugarliciousWatchFacePush.apply(
                        appContext,
                        index,
                    )

                runCatching {
                    Wearable
                        .getMessageClient(appContext)
                        .sendMessage(
                            sourceNodeId,
                            WearProtocol.WATCH_FACE_STATUS_PATH,
                            status.encodeToByteArray(),
                        )
                        .await()
                }

                runCatching {
                    sendRuntimeStatus(
                        appContext,
                        sourceNodeId,
                    )
                }
            }
        }
    }

    private fun persistComplicationPreset(event: DataEvent) {
        val dataMap =
            runCatching {
                DataMapItem.fromDataItem(event.dataItem).dataMap
            }.getOrNull() ?: return
        val ids =
            dataMap
                .getIntegerArrayList("ids")
                .orEmpty()
                .filter { it in SugarliciousComplicationIds.all }
                .distinct()
                .take(MAX_PRESET_ITEMS)
        val graphHours =
            dataMap
                .getInt("graphHours", 3)
                .takeIf { it in WearDisplayPreferences.allowedGraphHours }
                ?: 3

        getSharedPreferences(
            COMPLICATION_SETUP_PREFS,
            Context.MODE_PRIVATE,
        )
            .edit()
            .putString(
                COMPLICATION_PRESET_KEY,
                ids.joinToString(","),
            )
            .putInt(
                COMPLICATION_GRAPH_HOURS_KEY,
                graphHours,
            )
            .apply()

        getSharedPreferences(
            WearDisplayPreferences.PREFS,
            Context.MODE_PRIVATE,
        )
            .edit()
            .putInt(
                "complication_graph_hours",
                graphHours,
            )
            .apply()

        requestAllComplicationUpdates()
    }

    private fun persistWatchConfig(event: DataEvent) {
        val config =
            runCatching {
                WearProtocol.decodeConfig(
                    event.dataItem.data ?: return,
                )
            }.getOrNull() ?: return

        WearDisplayPreferences.save(
            this,
            config,
        )
    }

    private fun persistTherapyState(event: DataEvent) {
        val incoming =
            runCatching {
                WearProtocol.decode(
                    event.dataItem.data ?: return,
                )
            }.getOrNull() ?: return

        scope.launch {
            val store =
                TherapyStateStore(
                    this@StateDataLayerService,
                )
            val old = store.state.first()
            val now = System.currentTimeMillis()

            val mergedByTimestamp =
                linkedMapOf<Long, GlucoseSample>()

            old
                ?.glucoseHistory
                .orEmpty()
                .forEach {
                    mergedByTimestamp[it.measuredAtEpochMs] = it
                }

            incoming
                .glucoseHistory
                .forEach {
                    mergedByTimestamp[it.measuredAtEpochMs] = it
                }

            incoming.glucose?.let {
                mergedByTimestamp[it.measuredAtEpochMs] =
                    GlucoseSample(
                        valueMgDl = it.valueMgDl,
                        measuredAtEpochMs = it.measuredAtEpochMs,
                    )
            }

            val history =
                mergedByTimestamp
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

            val merged =
                incoming.copy(
                    glucoseHistory = history,
                )
            val meaningfulState =
                old?.copy(receivedAtEpochMs = merged.receivedAtEpochMs)
            if (meaningfulState == merged) return@launch

            store.save(merged)
            requestComplicationUpdates(
                ComplicationUpdatePlanner.affectedProviders(old, merged),
            )
            requestSugarliciousTileUpdates(this@StateDataLayerService)
        }
    }

    private suspend fun sendRuntimeStatus(
        context: Context,
        nodeId: String,
    ) {
        val status =
            WatchRuntimeStatus(
                activeSugarliciousFaceIndex =
                    SugarliciousWatchFacePush
                        .activeFaceIndex(context),
                activeComplicationIds =
                    ActiveComplicationRegistry
                        .activeCatalogIds(context),
                sentAtEpochMs =
                    System.currentTimeMillis(),
            )

        Wearable
            .getMessageClient(context)
            .sendMessage(
                nodeId,
                WearProtocol.WATCH_RUNTIME_STATUS_PATH,
                WearProtocol.encodeRuntimeStatus(status),
            )
            .await()
    }

    private fun requestAllComplicationUpdates() {
        requestComplicationUpdates(AllProviders.classes)
    }

    private fun requestComplicationUpdates(providers: List<Class<*>>) {
        providers.forEach { provider ->
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
        private val watchFacePushScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO,
            )
        private val watchFacePushMutex = Mutex()

        private const val HISTORY_WINDOW_MS =
            24 * 60 * 60_000L
        private const val FUTURE_TOLERANCE_MS =
            5 * 60_000L
        private const val MAX_HISTORY_POINTS = 300

        private const val COMPLICATION_SETUP_PREFS =
            "complication_setup"
        private const val COMPLICATION_PRESET_KEY =
            "selected_ids"
        private const val COMPLICATION_GRAPH_HOURS_KEY =
            "graph_hours"
        private const val MAX_PRESET_ITEMS = 4
    }
}

suspend fun requestLatestState(
    context: Context,
): Int {
    val nodes =
        Wearable
            .getNodeClient(context)
            .connectedNodes
            .await()

    nodes.forEach { node ->
        runCatching {
            Wearable
                .getMessageClient(context)
                .sendMessage(
                    node.id,
                    WearProtocol.REQUEST_PATH,
                    byteArrayOf(),
                )
                .await()
        }

        runCatching {
            Wearable
                .getMessageClient(context)
                .sendMessage(
                    node.id,
                    WearProtocol.WATCH_CONFIG_REQUEST_PATH,
                    byteArrayOf(),
                )
                .await()
        }
    }

    return nodes.size
}
