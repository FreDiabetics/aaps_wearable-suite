package app.aapswear.mobile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aapswear.datasource.aaps.AapsPayloadAdapter
import app.aapswear.datasource.aaps.AapsCapabilityDetector
import app.aapswear.model.TherapyDisplayState
import app.aapswear.protocol.WearProtocol
import app.aapswear.storage.TherapyStateStore
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class AapsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AapsPayloadAdapter.ACTION) return
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val parsedState = intent.extras?.let { AapsPayloadAdapter.parse(it, now) }
                if (parsedState == null) {
                    app.diagnostics().edit()
                        .putLong("invalidReceived", now)
                        .putString("lastSyncStatus", "invalid_payload")
                        .apply()
                    return@launch
                }
                val installation = AapsCapabilityDetector.detectInstallation(app)
                val state = parsedState.copy(sourceVersion = installation?.versionName)
                val store = TherapyStateStore(app)
                val displayState = DisplayHistoryAccumulator.merge(store.state.first(), state, now)

                // Persistence is deliberately completed before Data Layer I/O. A phone
                // without a paired watch must never lose a valid AAPS status broadcast.
                store.save(displayState)
                app.diagnostics().edit()
                    .putLong("received", now)
                    .putLong("measurement", displayState.glucose?.measuredAtEpochMs ?: 0L)
                    .putString("contract", displayState.sourceContract)
                    .putString("sourceVersion", displayState.sourceVersion)
                    .putString("sourcePackage", installation?.packageName)
                    .putLong("sourceVersionCode", installation?.versionCode ?: 0L)
                    .putString("lastSyncStatus", "pending")
                    .apply()

                runCatching {
                    withTimeout(4_000L) { publishState(app, displayState) }
                }.onSuccess {
                    app.diagnostics().edit()
                        .putLong("lastSyncAt", System.currentTimeMillis())
                        .putString("lastSyncStatus", "ok")
                        .remove("lastSyncError")
                        .apply()
                }.onFailure { error ->
                    app.diagnostics().edit()
                        .putString("lastSyncStatus", "unavailable")
                        .putString("lastSyncError", error.javaClass.simpleName)
                        .apply()
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
    val nodes = Wearable.getCapabilityClient(context)
        .getCapability(
            WearProtocol.CAPABILITY,
            com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE,
        )
        .await()
        .nodes
    context.diagnostics().edit().putInt("reachableWatches", nodes.size).apply()
}

private fun Context.diagnostics() = getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
