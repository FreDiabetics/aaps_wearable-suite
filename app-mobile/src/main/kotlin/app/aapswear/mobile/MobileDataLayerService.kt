package app.aapswear.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.aapswear.protocol.WatchConfig
import app.aapswear.protocol.WatchGlucoseUnit
import app.aapswear.protocol.WearProtocol
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.protocol.WatchGraphStyle
import app.aapswear.protocol.WatchUiColors
import app.aapswear.protocol.WatchDataSource
import app.aapswear.model.DataSourceId
import app.aapswear.model.DiagnosticSeverity
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColorStore
import app.aapswear.storage.TherapyStateStore
import app.aapswear.storage.DiagnosticEventStore
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MobileDataLayerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        recordWatchContact(applicationContext)
        when (event.path) {
            WearProtocol.REQUEST_PATH -> {
                scope.launch {
                    applicationContext.recordMobileDiagnostic("SYNC", "SYNC-WATCH-100", "Watch requested current state")
                    TherapyStateStore(this@MobileDataLayerService)
                        .state
                        .first()
                        ?.let { publishState(this@MobileDataLayerService, it) }

                    publishWatchConfig(this@MobileDataLayerService)
                }
            }

            WearProtocol.WATCH_CONFIG_REQUEST_PATH -> {
                scope.launch {
                    applicationContext.recordMobileDiagnostic("SYNC", "SYNC-CONFIG-101", "Watch requested display configuration")
                    publishWatchConfig(this@MobileDataLayerService)
                }
            }
            WearProtocol.WATCH_FACE_STATUS_PATH -> {
                val message = event.data.decodeToString()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            WearProtocol.WATCH_RUNTIME_STATUS_PATH -> {
                runCatching { WearProtocol.decodeRuntimeStatus(event.data) }
                    .onSuccess {
                        WatchRuntimeStatusStore.save(applicationContext, it)
                        scope.launch {
                            applicationContext.recordMobileDiagnostic(
                                "WATCH",
                                "WATCH-STATUS-200",
                                "Watch runtime status received",
                                metadata = mapOf("complications" to it.activeComplicationIds.size, "watchface" to it.activeSugarliciousFaceIndex),
                            )
                        }
                    }
                    .onFailure {
                        scope.launch {
                            applicationContext.recordMobileDiagnostic("WATCH", "WATCH-STATUS-401", "Invalid Watch runtime status", DiagnosticSeverity.WARNING)
                        }
                    }
            }
            WearProtocol.G7_READING_PATH -> {
                scope.launch {
                    val incoming = runCatching { WearProtocol.decode(event.data) }.getOrNull()
                        ?.takeIf { it.source == DataSourceId.DEXCOM_G7_WATCH }
                        ?: return@launch
                    val preference = runCatching {
                        DataSourcePreference.valueOf(
                            getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
                                .getString("dataSource", DataSourcePreference.AUTOMATIC.name)!!,
                        )
                    }.getOrDefault(DataSourcePreference.AUTOMATIC)
                    if (preference !in setOf(DataSourcePreference.AUTOMATIC, DataSourcePreference.DEXCOM_G7_WATCH)) return@launch
                    val store = TherapyStateStore(this@MobileDataLayerService)
                    val previous = store.state.first()
                    val merged = DisplayHistoryAccumulator.merge(previous, incoming, System.currentTimeMillis())
                    store.save(merged)
                    applicationContext.recordMobileDiagnostic(
                        "G7",
                        "G7-DATA-200",
                        "Direct G7 reading received from Watch",
                        metadata = mapOf("historyCount" to merged.glucoseHistory.size, "predictionCount" to merged.glucosePredictions.size),
                    )
                    runCatching { HealthConnectIntegration.exportCgmReading(this@MobileDataLayerService, merged) }
                    SugarliciousWidgets.update(this@MobileDataLayerService)
                    PersistentBridgeService.refresh(this@MobileDataLayerService)
                }
            }
            WearProtocol.DIAGNOSTICS_BATCH_PATH -> {
                scope.launch {
                    runCatching { WearProtocol.decodeDiagnostics(event.data) }
                        .onSuccess { batch ->
                            DiagnosticEventStore(applicationContext).append(batch.events)
                            applicationContext.recordMobileDiagnostic(
                                "DIAGNOSTICS",
                                "DIAG-SYNC-200",
                                "Watch diagnostics received",
                                metadata = mapOf("eventCount" to batch.events.size),
                            )
                        }
                        .onFailure {
                            applicationContext.recordMobileDiagnostic(
                                "DIAGNOSTICS",
                                "DIAG-SYNC-401",
                                "Watch diagnostics could not be decoded",
                                DiagnosticSeverity.WARNING,
                            )
                        }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal fun readWatchConfig(context: Context): WatchConfig {
    val preferences =
        context.getSharedPreferences(
            "dashboard_ui",
            Context.MODE_PRIVATE,
        )

    val unit =
        runCatching {
            WatchGlucoseUnit.valueOf(
                preferences.getString("unit", WatchGlucoseUnit.AAPS.name)
                    ?: WatchGlucoseUnit.AAPS.name,
            )
        }.getOrDefault(WatchGlucoseUnit.AAPS)

    val palette = SugarliciousColorStore.load(preferences)
    return WatchConfig(
        graphHours =
            preferences
                .getInt("graphHours", 3)
                .takeIf { it in listOf(1, 2, 3, 6, 12, 24) }
                ?: 3,
        showPredictions =
            listOf(
                "cgm.prediction.iob",
                "cgm.prediction.cob",
                "cgm.prediction.uam",
                "cgm.prediction.zeroTemp",
            ).any { preferences.getBoolean(it, false) },
        glucoseUnit = unit,
        dataSource = when (
            runCatching {
                DataSourcePreference.valueOf(
                    preferences.getString("dataSource", DataSourcePreference.AUTOMATIC.name)!!,
                )
            }.getOrDefault(DataSourcePreference.AUTOMATIC)
        ) {
            DataSourcePreference.AUTOMATIC -> WatchDataSource.AUTOMATIC
            DataSourcePreference.DEXCOM_G7_WATCH -> WatchDataSource.DEXCOM_G7_WATCH
            DataSourcePreference.ANDROID_APS,
            DataSourcePreference.XDRIP_PLUS,
            -> WatchDataSource.PHONE
        },
        showTherapyStats = preferences.getBoolean("showDetails", true),
        graphColors = WatchGraphColors(
            graphBackground = palette.argb(SugarliciousColorRole.GRAPH_BACKGROUND),
            rangeLow = palette.argb(SugarliciousColorRole.RANGE_LOW),
            rangeInRange = palette.argb(SugarliciousColorRole.TARGET_BAND),
            rangeHigh = palette.argb(SugarliciousColorRole.RANGE_HIGH),
            cgmLow = palette.argb(SugarliciousColorRole.CGM_DOT_LOW),
            cgmInRange = palette.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE),
            cgmHigh = palette.argb(SugarliciousColorRole.CGM_DOT_HIGH),
            divider = palette.argb(SugarliciousColorRole.GRAPH_DIVIDER),
            outline = palette.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE),
            predictionIob = palette.argb(SugarliciousColorRole.PREDICTION_IOB),
            predictionCob = palette.argb(SugarliciousColorRole.PREDICTION_COB),
            predictionUam = palette.argb(SugarliciousColorRole.PREDICTION_UAM),
            predictionZeroTemp = palette.argb(SugarliciousColorRole.PREDICTION_ZERO_TEMP),
        ),
        graphStyle = WatchGraphStyle(
            cgmDotRadiusDp =
                preferences
                    .getFloat("cgm.dotRadiusDp", 2.4f)
                    .coerceIn(1.5f, 6.0f),
            cgmDotOutlineEnabled =
                preferences.getBoolean(
                    "cgm.dotOutlineEnabled",
                    true,
                ),
            cgmDotOutlineWidthDp =
                preferences
                    .getFloat("cgm.dotOutlineWidthDp", 0.95f)
                    .coerceIn(0.25f, 3.0f),
        ),
        uiColors = WatchUiColors(
            background = palette.argb(SugarliciousColorRole.BACKGROUND),
            tileBackground = palette.argb(SugarliciousColorRole.SURFACE),
            tileBorder = palette.argb(SugarliciousColorRole.BORDER),
            textPrimary = palette.argb(SugarliciousColorRole.TEXT_PRIMARY),
            textSecondary = palette.argb(SugarliciousColorRole.TEXT_SECONDARY),
            accent = palette.argb(SugarliciousColorRole.PRIMARY),
            glucoseLow = palette.argb(SugarliciousColorRole.GLUCOSE_LOW),
            glucoseInRange = palette.argb(SugarliciousColorRole.GLUCOSE_IN_RANGE),
            glucoseHigh = palette.argb(SugarliciousColorRole.GLUCOSE_HIGH),
            iob = palette.argb(SugarliciousColorRole.BLUE),
            cob = palette.argb(SugarliciousColorRole.ORANGE),
            basal = palette.argb(SugarliciousColorRole.SECONDARY),
        ),

        sentAtEpochMs = System.currentTimeMillis(),
    )
}

internal suspend fun publishWatchConfig(context: Context) {
    val request =
        PutDataRequest
            .create(WearProtocol.WATCH_CONFIG_PATH)
            .setData(
                WearProtocol.encodeConfig(
                    readWatchConfig(context),
                ),
            )
            .setUrgent()

    Wearable
        .getDataClient(context)
        .putDataItem(request)
        .await()
}

internal suspend fun requestWatchRuntimeStatus(context: Context) {
    refreshReachableWatchNodeIds(context).forEach { nodeId ->
        Wearable.getMessageClient(context)
            .sendMessage(nodeId, WearProtocol.WATCH_RUNTIME_REQUEST_PATH, byteArrayOf())
            .await()
    }
}
