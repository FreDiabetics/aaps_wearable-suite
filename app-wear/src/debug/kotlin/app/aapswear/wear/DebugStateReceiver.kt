package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.AllProviders
import app.aapswear.model.*
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** ADB-only synthetic state injection. This class exists only in debug APKs. */
class DebugStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val value = intent.getDoubleExtra("glucose", 123.0)
                val unit = if (intent.getBooleanExtra("mmol", false)) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL
                val history = listOf(112.0, 116.0, 119.0, 117.0, 121.0, value).mapIndexed { index, sample ->
                    GlucoseSample(sample, now - (5L - index) * 5 * 60_000L)
                }
                val state = TherapyDisplayState(
                    sourceVersion = "AAPS dev test",
                    sourceContract = "AAPS_EXTENDED_STATUS_V1",
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(value, unit, Trend.FORTY_FIVE_UP, now, 4.0, 2.3),
                    glucoseHistory = history,
                    insulin = InsulinState(1.25, 0.8, 0.45),
                    carbs = CarbState(18.0, 0.0),
                    basal = BasalState(0.9, 1.08, 120, now - 10 * 60_000L, 30, now + 20 * 60_000L, "120%"),
                    target = TargetState(70.0, 180.0, false),
                    loop = LoopState("enacted", now - 2 * 60_000L, enactedAtEpochMs = now - 2 * 60_000L),
                    pump = PumpState("OK", 118.0, 76),
                    device = DeviceState(82, 90),
                    profile = ProfileState("Default"),
                    capabilities = DataCapability.entries.toSet(),
                )
                TherapyStateStore(app).save(state)
                AllProviders.classes.forEach { provider ->
                    ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, provider)).requestUpdateAll()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val ACTION = "app.aapswear.DEBUG_INJECT_STATE" }
}
