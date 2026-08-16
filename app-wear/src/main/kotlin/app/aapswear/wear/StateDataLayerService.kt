package app.aapswear.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.ActiveComplicationRegistry
import app.aapswear.complications.AllProviders
import app.aapswear.complications.ComplicationUpdatePlanner
import app.aapswear.model.SugarliciousComplicationIds
import app.aapswear.model.DiagnosticSeverity
import app.aapswear.model.GlucoseSample
import app.aapswear.protocol.WatchRuntimeStatus
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.PersistentPredictionCache
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
                .onSuccess { applicationContext.recordWatchDiagnostic("SYNC", "SYNC-PHONE-100", "Requested latest state from phone") }
                .onFailure { error ->
                    applicationContext.recordWatchDiagnostic(
                        "SYNC",
                        "SYNC-PHONE-503",
                        "Could not request latest state from phone",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
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
            WearProtocol.G7_SETUP_PATH -> configureG7Collector(event)
            WearProtocol.DIAGNOSTICS_REQUEST_PATH ->
                scope.launch {
                    runCatching { sendWatchDiagnostics(applicationContext, event.sourceNodeId) }
                        .onSuccess { applicationContext.recordWatchDiagnostic("DIAGNOSTICS", "DIAG-SYNC-200", "Diagnostics sent to phone") }
                        .onFailure { error ->
                            applicationContext.recordWatchDiagnostic(
                                "DIAGNOSTICS",
                                "DIAG-SYNC-503",
                                "Diagnostics could not be sent to phone",
                                DiagnosticSeverity.WARNING,
                                mapOf("error" to error.javaClass.simpleName),
                            )
                        }
                }
        }
    }

    private fun configureG7Collector(event: MessageEvent) {
        val command = runCatching { WearProtocol.decodeG7Setup(event.data) }.getOrNull()
        if (command == null) {
            scope.launch { applicationContext.recordWatchDiagnostic("G7", "G7-SETUP-401", "Invalid G7 setup command", DiagnosticSeverity.WARNING) }
            return
        }
        val intent = Intent("app.aapswear.g7watch.CONFIGURE")
            .setComponent(
                ComponentName(
                    "app.aapswear.g7watch",
                    "app.aapswear.g7watch.G7SetupReceiver",
                ),
            )
            .putExtra("pairing_code", command.pairingCode)
            .putExtra("sensor_serial", command.sensorSerial)
            .putExtra("gtin", command.gtin)
        sendBroadcast(intent, "app.aapswear.g7watch.permission.CONFIGURE_G7")
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "G7",
                "G7-SETUP-200",
                "G7 setup forwarded to collector",
                metadata = mapOf("serialAvailable" to !command.sensorSerial.isNullOrBlank(), "gtinAvailable" to !command.gtin.isNullOrBlank()),
            )
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
                val activated = status.equals("Watchface aktiv", ignoreCase = true)

                applicationContext.recordWatchDiagnostic(
                    "WATCHFACE",
                    if (activated) "WATCHFACE-APPLY-200" else "WATCHFACE-APPLY-409",
                    status,
                    if (activated) DiagnosticSeverity.INFO else DiagnosticSeverity.WARNING,
                    mapOf("index" to index),
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
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "COMPLICATION",
                "COMP-CONFIG-200",
                "Complication preset saved",
                metadata = mapOf("count" to ids.size, "graphHours" to graphHours),
            )
        }
    }

    private fun persistWatchConfig(event: DataEvent) {
        val config =
            runCatching {
                WearProtocol.decodeConfig(
                    event.dataItem.data ?: return,
                )
            }.getOrNull()
        if (config == null) {
            scope.launch { applicationContext.recordWatchDiagnostic("CONFIG", "CONFIG-401", "Invalid Watch configuration", DiagnosticSeverity.WARNING) }
            return
        }

        WearDisplayPreferences.save(
            this,
            config,
        )
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "CONFIG",
                "CONFIG-200",
                "Watch configuration saved",
                metadata = mapOf("graphHours" to config.graphHours, "showPredictions" to config.showPredictions, "dataSource" to config.dataSource),
            )
        }
    }

    private fun persistTherapyState(event: DataEvent) {
        val incoming =
            runCatching {
                WearProtocol.decode(
                    event.dataItem.data ?: return,
                )
            }.getOrNull()
        if (incoming == null) {
            scope.launch { applicationContext.recordWatchDiagnostic("SOURCE", "SRC-PHONE-401", "Invalid phone state payload", DiagnosticSeverity.WARNING) }
            return
        }

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
                PersistentPredictionCache.merge(
                    previous = old,
                    incoming = incoming.copy(glucoseHistory = history),
                    nowEpochMs = now,
                )
            applicationContext.recordWatchDiagnostic(
                "PREDICTION",
                if (incoming.glucosePredictions.isEmpty() && merged.glucosePredictions.isNotEmpty()) "PRED-CACHE-203" else "PRED-DATA-200",
                if (incoming.glucosePredictions.isEmpty() && merged.glucosePredictions.isNotEmpty()) "Cached predictions retained on Watch" else "Phone state merged on Watch",
                metadata = mapOf(
                    "incomingPredictions" to incoming.glucosePredictions.size,
                    "displayPredictions" to merged.glucosePredictions.size,
                    "historyCount" to history.size,
                ),
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
