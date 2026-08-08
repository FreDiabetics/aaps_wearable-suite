package app.aapswear.mobile

import android.content.Context
import app.aapswear.protocol.WatchConfig
import app.aapswear.protocol.WatchGlucoseUnit
import app.aapswear.protocol.WearProtocol
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

    return WatchConfig(
        graphHours =
            preferences
                .getInt("graphHours", 3)
                .takeIf { it in listOf(3, 6, 12, 24) }
                ?: 3,
        showPredictions = preferences.getBoolean("showPredictions", true),
        glucoseUnit = unit,
        showTherapyStats = preferences.getBoolean("showDetails", true),
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
