package app.aapswear.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import app.aapswear.datasource.xdrip.XdripContract
import app.aapswear.datasource.xdrip.XdripPayloadAdapter
import app.aapswear.model.DataSourceId
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class DataSourcePreference { AUTOMATIC, ANDROID_APS, XDRIP_PLUS }

class XdripStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != XdripContract.ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val values = mapOf(
                    XdripContract.EXTRA_BG to intent.getDoubleExtra(XdripContract.EXTRA_BG, Double.NaN),
                    XdripContract.EXTRA_SLOPE to intent.getDoubleExtra(XdripContract.EXTRA_SLOPE, Double.NaN),
                    XdripContract.EXTRA_SLOPE_NAME to intent.getStringExtra(XdripContract.EXTRA_SLOPE_NAME),
                    XdripContract.EXTRA_TIME to intent.getLongExtra(XdripContract.EXTRA_TIME, 0L),
                    XdripContract.EXTRA_UNITS to intent.getStringExtra(XdripContract.EXTRA_UNITS),
                    XdripContract.EXTRA_SOURCE to intent.getStringExtra(XdripContract.EXTRA_SOURCE),
                    XdripContract.EXTRA_VERSION to intent.getStringExtra(XdripContract.EXTRA_VERSION),
                )
                val parsed = XdripPayloadAdapter().parse(values, now)
                    ?: return@launch
                val prefs = app.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
                val preference = runCatching {
                    DataSourcePreference.valueOf(prefs.getString("dataSource", "AUTOMATIC")!!)
                }.getOrDefault(DataSourcePreference.AUTOMATIC)
                if (preference == DataSourcePreference.ANDROID_APS) return@launch

                val store = TherapyStateStore(app)
                val previous = store.state.first()
                val aapsIsCurrent = previous?.source == DataSourceId.ANDROID_APS &&
                    FreshnessPolicy.classify(previous.glucose?.measuredAtEpochMs, now) != Freshness.STALE &&
                    FreshnessPolicy.classify(previous.glucose?.measuredAtEpochMs, now) != Freshness.NO_DATA
                if (preference == DataSourcePreference.AUTOMATIC && aapsIsCurrent) return@launch

                val preserved = parsed.copy(
                    insulin = previous?.insulin,
                    carbs = previous?.carbs,
                    basal = previous?.basal,
                    target = previous?.target,
                    loop = previous?.loop,
                    pump = previous?.pump,
                    device = previous?.device,
                    profile = previous?.profile,
                    therapyHistory = previous?.therapyHistory.orEmpty(),
                    capabilities = parsed.capabilities + previous?.capabilities.orEmpty(),
                )
                val state = DisplayHistoryAccumulator.merge(previous, preserved, now)
                if (previous?.copy(receivedAtEpochMs = state.receivedAtEpochMs) == state) {
                    app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit {
                        putLong("received", now)
                        putString("lastSyncStatus", "unchanged")
                    }
                    return@launch
                }
                store.save(state)
                runCatching { HealthConnectIntegration.exportCgmReading(app, state) }
                SugarliciousWidgets.update(app)
                publishState(app, state)
                app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit {
                    putLong("received", now)
                    putLong("measurement", state.glucose?.measuredAtEpochMs ?: 0L)
                    putString("contract", state.sourceContract)
                    putString("sourceVersion", state.sourceVersion)
                    putString("sourcePackage", "com.eveningoutpost.dexdrip")
                    putString("lastSyncStatus", "ok")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
