package app.aapswear.wear

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WearActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var clockJob: Job? = null
    private var latest: TherapyDisplayState? = null
    private var connectedNodes = 0

    private lateinit var clock: TextView
    private lateinit var glucose: TextView
    private lateinit var unit: TextView
    private lateinit var trend: TextView
    private lateinit var delta: TextView
    private lateinit var age: TextView
    private lateinit var status: TextView
    private lateinit var source: TextView
    private lateinit var connection: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)
        bindViews()
        scope.launch {
            TherapyStateStore(this@WearActivity).state.collectLatest {
                latest = it
                render()
            }
        }
        scope.launch {
            connectedNodes = withContext(Dispatchers.IO) {
                runCatching { requestLatestState(applicationContext) }.getOrDefault(0)
            }
            render()
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        clockJob = scope.launch {
            while (true) {
                render()
                delay(30_000L)
            }
        }
    }

    override fun onStop() {
        clockJob?.cancel()
        clockJob = null
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        clock = findViewById(R.id.wear_clock)
        glucose = findViewById(R.id.wear_glucose)
        unit = findViewById(R.id.wear_unit)
        trend = findViewById(R.id.wear_trend)
        delta = findViewById(R.id.wear_delta)
        age = findViewById(R.id.wear_age)
        status = findViewById(R.id.wear_status)
        source = findViewById(R.id.wear_source)
        connection = findViewById(R.id.wear_connection)
    }

    private fun render() {
        if (!::clock.isInitialized) return
        val now = System.currentTimeMillis()
        val state = latest
        val glucoseState = state?.glucose
        val freshness = FreshnessPolicy.classify(glucoseState?.measuredAtEpochMs ?: state?.receivedAtEpochMs, now)
        val canShowValue = glucoseState != null && freshness in setOf(Freshness.CURRENT, Freshness.DELAYED)

        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        glucose.text = if (canShowValue) TherapyDisplayFormatter.glucose(glucoseState!!) else "—"
        unit.text = if (canShowValue && glucoseState!!.displayUnit == GlucoseUnit.MMOL_L) "mmol/L" else if (canShowValue) "mg/dL" else "keine Daten"
        trend.text = if (canShowValue) TherapyDisplayFormatter.trendArrow(glucoseState!!.trend).ifBlank { "—" } else "—"
        delta.text = if (canShowValue) TherapyDisplayFormatter.signedDelta(glucoseState!!.deltaMgDl, glucoseState.displayUnit).ifBlank { "—" } else "—"
        age.text = TherapyDisplayFormatter.ageMinutes(glucoseState?.measuredAtEpochMs, now)
        status.text = when (freshness) {
            Freshness.CURRENT -> "● Aktuell"
            Freshness.DELAYED -> "● Verzögert"
            Freshness.STALE -> "● Veraltet"
            Freshness.NO_DATA -> "○ Keine Daten"
        }
        status.setTextColor(getColor(when (freshness) {
            Freshness.CURRENT -> R.color.wear_green
            Freshness.DELAYED -> R.color.wear_orange
            Freshness.STALE -> R.color.wear_red
            Freshness.NO_DATA -> R.color.wear_text_secondary
        }))
        source.text = state?.sourceVersion?.let { "AndroidAPS $it" } ?: "AndroidAPS nicht erkannt"
        connection.text = if (connectedNodes > 0) "● Telefon verbunden" else "○ Telefon nicht erreichbar"
        connection.setTextColor(getColor(if (connectedNodes > 0) R.color.wear_green else R.color.wear_text_secondary))
    }
}
