package app.aapswear.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.aapswear.datasource.aaps.AapsCapabilityDetector
import app.aapswear.datasource.aaps.AapsPayloadAdapter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.model.DataSourceId
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class AapsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AapsPayloadAdapter.ACTION) return
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val sourcePreference = runCatching {
                    DataSourcePreference.valueOf(
                        app.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
                            .getString("dataSource", "AUTOMATIC")!!,
                    )
                }.getOrDefault(DataSourcePreference.AUTOMATIC)
                if (sourcePreference in setOf(DataSourcePreference.XDRIP_PLUS, DataSourcePreference.DEXCOM_G7_WATCH)) return@launch
                val parsedState = intent.extras?.let { AapsPayloadAdapter.parse(it, now) }
                if (parsedState == null) {
                    app.diagnostics().edit {
                        putLong("invalidReceived", now)
                        putString("lastSyncStatus", "invalid_payload")
                    }
                    return@launch
                }
                val installation = AapsCapabilityDetector.detectInstallation(app)
                val state = parsedState.copy(sourceVersion = installation?.versionName)
                val store = TherapyStateStore(app)
                val previous = store.state.first()
                val g7IsCurrent = previous?.source == DataSourceId.DEXCOM_G7_WATCH &&
                    FreshnessPolicy.classify(previous.glucose?.measuredAtEpochMs, now) in
                    setOf(Freshness.CURRENT, Freshness.DELAYED)
                if (sourcePreference == DataSourcePreference.AUTOMATIC && g7IsCurrent) return@launch

                var displayState = DisplayHistoryAccumulator.merge(previous, state, now)

                val glucose = displayState.glucose
                if (glucose != null && glucose.trend == Trend.UNKNOWN) {
                    val resolved = TrendArrowResolver.resolve(
                        glucose.trend,
                        displayState.glucoseHistory,
                        glucose.measuredAtEpochMs,
                    )
                    displayState = displayState.copy(glucose = glucose.copy(trend = resolved))
                }

                if (previous?.copy(receivedAtEpochMs = displayState.receivedAtEpochMs) == displayState) {
                    app.diagnostics().edit {
                        putLong("received", now)
                        putString("lastSyncStatus", "unchanged")
                    }
                    return@launch
                }

                // Persistence is deliberately completed before Data Layer I/O. A phone
                // without a paired watch must never lose a valid AAPS status broadcast.
                store.save(displayState)
                runCatching { HealthConnectIntegration.exportCgmReading(app, displayState) }
                SugarliciousWidgets.update(app)
                app.diagnostics().edit {
                    putLong("received", now)
                    putLong("measurement", displayState.glucose?.measuredAtEpochMs ?: 0L)
                    putString("contract", displayState.sourceContract)
                    putString("sourceVersion", displayState.sourceVersion)
                    putString("sourcePackage", installation?.packageName)
                    putLong("sourceVersionCode", installation?.versionCode ?: 0L)
                    putString("lastSyncStatus", "pending")
                }

                runCatching {
                    withTimeout(4.seconds) { publishState(app, displayState) }
                }.onSuccess {
                    app.diagnostics().edit {
                        putLong("lastSyncAt", System.currentTimeMillis())
                        putString("lastSyncStatus", "ok")
                        remove("lastSyncError")
                    }
                }.onFailure { error ->
                    app.diagnostics().edit {
                        putString("lastSyncStatus", "unavailable")
                        putString("lastSyncError", error.javaClass.simpleName)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

suspend fun publishState(context: Context, state: TherapyDisplayState) {
    val request = PutDataRequest.create(WearProtocol.STATE_PATH)
        .setData(WearProtocol.encode(state))
        .setUrgent()
    Wearable.getDataClient(context).putDataItem(request).await()
    refreshReachableWatchNodeIds(context)
}

private fun Context.diagnostics() = getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
