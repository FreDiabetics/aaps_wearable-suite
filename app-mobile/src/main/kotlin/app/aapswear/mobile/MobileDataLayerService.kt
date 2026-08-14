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
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColorStore
import app.aapswear.storage.TherapyStateStore
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
        when (event.path) {
            WearProtocol.REQUEST_PATH -> {
                scope.launch {
                    TherapyStateStore(this@MobileDataLayerService)
                        .state
                        .first()
                        ?.let { publishState(this@MobileDataLayerService, it) }

                    publishWatchConfig(this@MobileDataLayerService)
                }
            }

            WearProtocol.WATCH_CONFIG_REQUEST_PATH -> {
                scope.launch {
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
                    .onSuccess { WatchRuntimeStatusStore.save(applicationContext, it) }
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
        showTherapyStats = preferences.getBoolean("showDetails", true),
        graphColors = WatchGraphColors(
            graphBackground = palette.argb(SugarliciousColorRole.GRAPH_BACKGROUND),
            rangeLow = palette.argb(SugarliciousColorRole.RANGE_LOW),
            rangeInRange = palette.argb(SugarliciousColorRole.RANGE_IN_RANGE),
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
    Wearable.getNodeClient(context).connectedNodes.await().forEach { node ->
        Wearable.getMessageClient(context)
            .sendMessage(node.id, WearProtocol.WATCH_RUNTIME_REQUEST_PATH, byteArrayOf())
            .await()
    }
}
