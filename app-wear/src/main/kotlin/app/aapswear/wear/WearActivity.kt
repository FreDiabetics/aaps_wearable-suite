package app.aapswear.wear

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aapswear.model.DataSourceId
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.protocol.WatchGlucoseUnit
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
import kotlin.math.roundToInt

class WearActivity : Activity() {
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main,
        )

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
    private lateinit var syncHint: TextView
    private lateinit var historyInfo: TextView
    private lateinit var iob: TextView
    private lateinit var cob: TextView
    private lateinit var basal: TextView
    private lateinit var therapyRow: LinearLayout
    private lateinit var chart: WearGlucoseChart
    private lateinit var watchFacePushStatus: TextView

    private val displayPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener {
                _,
                _,
            ->
            runOnUiThread(::render)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)
        bindViews()

        findViewById<View>(
            R.id.wear_connection_card,
        ).setOnClickListener {
            requestPhoneRefresh()
        }

        findViewById<View>(
            R.id.wear_watchface_push_card,
        ).setOnClickListener {
            requestWatchFaceActivationPermission()
        }

        scope.launch {
            TherapyStateStore(this@WearActivity)
                .state
                .collectLatest {
                    latest = it
                    render()
                }
        }

        requestPhoneRefresh(initial = true)
        render()
    }

    override fun onStart() {
        super.onStart()

        getSharedPreferences(
            WearDisplayPreferences.PREFS,
            Context.MODE_PRIVATE,
        ).registerOnSharedPreferenceChangeListener(
            displayPreferencesListener,
        )

        clockJob =
            scope.launch {
                while (true) {
                    render()
                    delay(30_000L)
                }
            }
    }

    override fun onStop() {
        clockJob?.cancel()
        clockJob = null

        getSharedPreferences(
            WearDisplayPreferences.PREFS,
            Context.MODE_PRIVATE,
        ).unregisterOnSharedPreferenceChangeListener(
            displayPreferencesListener,
        )

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
        syncHint = findViewById(R.id.wear_sync_hint)
        historyInfo = findViewById(R.id.wear_history_info)
        iob = findViewById(R.id.wear_iob)
        cob = findViewById(R.id.wear_cob)
        basal = findViewById(R.id.wear_basal)
        therapyRow = findViewById(R.id.wear_therapy_row)
        chart = findViewById(R.id.wear_glucose_chart)
        watchFacePushStatus =
            findViewById(
                R.id.wear_watchface_push_status,
            )
    }

    private fun requestPhoneRefresh(
        initial: Boolean = false,
    ) {
        if (!::connection.isInitialized) return

        if (!initial) {
            syncHint.text =
                getString(
                    R.string.syncing_values,
                )
        }

        scope.launch {
            connectedNodes =
                withContext(Dispatchers.IO) {
                    runCatching {
                        requestLatestState(
                            applicationContext,
                        )
                    }.getOrDefault(0)
                }

            syncHint.text =
                if (connectedNodes > 0) {
                    "Tippen zum Aktualisieren"
                } else {
                    "Telefon derzeit nicht erreichbar"
                }

            render()
        }
    }

    private fun render() {
        if (!::clock.isInitialized) return

        val now = System.currentTimeMillis()
        val state = latest
        val glucoseState = state?.glucose
        val preferences =
            WearDisplayPreferences.read(this)

        val freshness =
            FreshnessPolicy.classify(
                glucoseState?.measuredAtEpochMs
                    ?: state?.receivedAtEpochMs,
                now,
            )

        val canShowValue =
            glucoseState != null &&
                freshness in
                setOf(
                    Freshness.CURRENT,
                    Freshness.DELAYED,
                )

        val targetLow =
            state?.target?.lowMgDl ?: 80.0
        val targetHigh =
            state?.target?.highMgDl ?: 160.0

        clock.text =
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault(),
            ).format(Date(now))

        val resolvedUnit =
            resolveUnit(
                glucoseState?.displayUnit,
                preferences.glucoseUnit,
            )

        glucose.text =
            if (canShowValue) {
                formatGlucose(
                    glucoseState.valueMgDl,
                    resolvedUnit,
                )
            } else {
                "—"
            }

        glucose.setTextColor(
            getColor(
                when {
                    !canShowValue ->
                        R.color.wear_text

                    glucoseState.valueMgDl < targetLow ->
                        R.color.wear_red

                    glucoseState.valueMgDl > targetHigh ->
                        R.color.wear_yellow

                    else ->
                        R.color.wear_text
                },
            ),
        )

        unit.text =
            when {
                !canShowValue ->
                    "keine Daten"

                resolvedUnit == GlucoseUnit.MMOL_L ->
                    "mmol/L"

                else ->
                    "mg/dL"
            }

        trend.text =
            if (canShowValue) {
                TherapyDisplayFormatter.trendArrow(
                    glucoseState.trend,
                )
            } else {
                "—"
            }

        delta.text =
            if (canShowValue) {
                formatDelta(
                    glucoseState.deltaMgDl,
                    resolvedUnit,
                )
            } else {
                "—"
            }

        age.text =
            ageMinutes(
                glucoseState?.measuredAtEpochMs,
                now,
            )

        status.text =
            when (freshness) {
                Freshness.CURRENT ->
                    "● LIVE"

                Freshness.DELAYED ->
                    "● VERZÖGERT"

                Freshness.STALE ->
                    "● VERALTET"

                Freshness.NO_DATA ->
                    "○ KEINE DATEN"
            }

        status.setTextColor(
            getColor(
                when (freshness) {
                    Freshness.CURRENT ->
                        R.color.wear_accent

                    Freshness.DELAYED ->
                        R.color.wear_orange

                    Freshness.STALE ->
                        R.color.wear_red

                    Freshness.NO_DATA ->
                        R.color.wear_text_secondary
                },
            ),
        )

        val history =
            state
                ?.glucoseHistory
                .orEmpty()
                .filter {
                    now - it.measuredAtEpochMs <=
                        HISTORY_WINDOW_MS
                }

        historyInfo.text =
            if (history.isEmpty()) {
                "Keine Historie"
            } else {
                "${preferences.graphHours} h · ${history.size} Werte"
            }

        chart.bind(
            newState = state,
            graphHours = preferences.graphHours,
            showPredictions =
                preferences.showPredictions,
            colors = preferences.graphColors,
            style = preferences.graphStyle,
        )

        therapyRow.visibility =
            if (preferences.showTherapyStats) {
                View.VISIBLE
            } else {
                View.GONE
            }

        iob.text =
            formatNumber(
                state?.insulin?.totalIob,
                2,
                " IE",
            )

        cob.text =
            formatNumber(
                state?.carbs?.cobGrams,
                0,
                " g",
            )

        basal.text =
            formatNumber(
                state?.basal?.currentUnitsPerHour,
                2,
                " IE/h",
            )

        source.text =
            when (state?.source) {
                DataSourceId.ANDROID_APS ->
                    "Lokale Loop-Daten"

                DataSourceId.XDRIP_PLUS ->
                    state.sourceVersion
                        ?.let { "xDrip+ $it" }
                        ?: "xDrip+"

                null ->
                    getString(
                        R.string.data_source_unavailable,
                    )
            }

        connection.text =
            if (connectedNodes > 0) {
                "● Telefon verbunden"
            } else {
                "○ Telefon nicht erreichbar"
            }

        connection.setTextColor(
            getColor(
                if (connectedNodes > 0) {
                    R.color.wear_accent
                } else {
                    R.color.wear_text_secondary
                },
            ),
        )

        renderWatchFacePushStatus()
    }

    private fun requestWatchFaceActivationPermission() {
        when {
            !SugarliciousWatchFacePush.isSupported() ->
                watchFacePushStatus.text =
                    "Direktwechsel: Wear OS 6+ erforderlich"

            SugarliciousWatchFacePush
                .hasActivationPermission(this) ->
                watchFacePushStatus.text =
                    "Direktwechsel freigegeben"

            else ->
                requestPermissions(
                    arrayOf(
                        SugarliciousWatchFacePush
                            .ACTIVE_PERMISSION,
                    ),
                    WATCH_FACE_PERMISSION_REQUEST,
                )
        }
    }

    private fun renderWatchFacePushStatus() {
        if (!::watchFacePushStatus.isInitialized) {
            return
        }

        watchFacePushStatus.text =
            when {
                !SugarliciousWatchFacePush.isSupported() ->
                    "Direktwechsel auf diesem Wear OS nicht verfügbar"

                SugarliciousWatchFacePush
                    .hasActivationPermission(this) ->
                    "Direktwechsel freigegeben"

                else ->
                    "Tippen, um Direktwechsel einmalig freizugeben"
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
        )

        if (requestCode ==
            WATCH_FACE_PERMISSION_REQUEST
        ) {
            renderWatchFacePushStatus()
        }
    }

    private fun resolveUnit(
        stateUnit: GlucoseUnit?,
        preference: WatchGlucoseUnit,
    ): GlucoseUnit =
        when (preference) {
            WatchGlucoseUnit.AAPS ->
                stateUnit ?: GlucoseUnit.MG_DL

            WatchGlucoseUnit.MG_DL ->
                GlucoseUnit.MG_DL

            WatchGlucoseUnit.MMOL_L ->
                GlucoseUnit.MMOL_L
        }

    private fun formatGlucose(
        valueMgDl: Double,
        unit: GlucoseUnit,
    ): String =
        if (unit == GlucoseUnit.MMOL_L) {
            String.format(
                Locale.getDefault(),
                "%.1f",
                valueMgDl / 18.0,
            )
        } else {
            valueMgDl
                .roundToInt()
                .toString()
        }

    private fun formatDelta(
        valueMgDl: Double?,
        unit: GlucoseUnit,
    ): String {
        valueMgDl ?: return "—"

        val value =
            if (unit == GlucoseUnit.MMOL_L) {
                valueMgDl / 18.0
            } else {
                valueMgDl
            }

        val prefix =
            if (value >= 0.0) "+" else ""

        return prefix +
            if (unit == GlucoseUnit.MMOL_L) {
                String.format(
                    Locale.getDefault(),
                    "%.1f",
                    value,
                )
            } else {
                value
                    .roundToInt()
                    .toString()
            }
    }

    private fun ageMinutes(
        timestamp: Long?,
        now: Long,
    ): String =
        timestamp
            ?.let {
                "${((now - it).coerceAtLeast(0L) / 60_000L)}m"
            }
            ?: "—"

    private fun formatNumber(
        value: Double?,
        digits: Int,
        suffix: String,
    ): String =
        value
            ?.let {
                String.format(
                    Locale.US,
                    "%.${digits}f%s",
                    it,
                    suffix,
                )
            }
            ?: "—"

    companion object {
        private const val WATCH_FACE_PERMISSION_REQUEST =
            701

        private const val HISTORY_WINDOW_MS =
            24 * 60 * 60_000L
    }
}
