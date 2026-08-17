package app.aapswear.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.aapswear.complications.R as ComplicationR
import app.aapswear.complications.G7LocalReadingResolver
import app.aapswear.model.BasalState
import app.aapswear.model.DataSourceId
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.TrendVisuals
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
import java.util.Locale
import kotlin.math.roundToInt

class WearActivity : Activity() {
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main,
        )

    private var refreshJob: Job? = null
    private var latest: TherapyDisplayState? = null
    private var connectedNodes = 0
    private var lastRenderedState: TherapyDisplayState? = null
    private var lastRenderedPreferences: WearDisplayPreferences? = null
    private var lastRenderedConnectedNodes: Int? = null
    private var hasRendered = false

    private lateinit var glucose: TextView
    private lateinit var trendContainer: LinearLayout
    private lateinit var trendArrow1: ImageView
    private lateinit var trendArrow2: ImageView
    private lateinit var delta: TextView
    private lateinit var age: TextView
    private lateinit var source: TextView
    private lateinit var connection: TextView
    private lateinit var syncHint: TextView
    private lateinit var iob: TextView
    private lateinit var cob: TextView
    private lateinit var basal: TextView
    private lateinit var iobIcon: ImageView
    private lateinit var cobIcon: ImageView
    private lateinit var basalIcon: ImageView
    private lateinit var therapyRow: LinearLayout
    private lateinit var chart: WearGlucoseChart
    private lateinit var watchFacePushStatus: TextView

    private val displayPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread { render() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)
        bindViews()

        findViewById<View>(R.id.wear_connection_card)
            .setOnClickListener { requestPhoneRefresh() }
        findViewById<View>(R.id.wear_settings_action)
            .setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        WearSettingsActivity::class.java,
                    ),
                )
            }

        scope.launch {
            TherapyStateStore(this@WearActivity)
                .state
                .collectLatest {
                    latest = it
                    render()
                }
        }

        requestWatchFacePermissionOnFirstLaunch()
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

        refreshJob =
            scope.launch {
                while (true) {
                    render(refreshClock = true)
                    delay(30_000L)
                }
            }
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null

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
        glucose = findViewById(R.id.wear_glucose)
        trendContainer = findViewById(R.id.wear_trend_container)
        trendArrow1 = findViewById(R.id.wear_trend_arrow_1)
        trendArrow2 = findViewById(R.id.wear_trend_arrow_2)
        delta = findViewById(R.id.wear_delta)
        age = findViewById(R.id.wear_age)
        source = findViewById(R.id.wear_source)
        connection = findViewById(R.id.wear_connection)
        syncHint = findViewById(R.id.wear_sync_hint)
        iob = findViewById(R.id.wear_iob)
        cob = findViewById(R.id.wear_cob)
        basal = findViewById(R.id.wear_basal)
        iobIcon = findViewById(R.id.wear_iob_icon)
        cobIcon = findViewById(R.id.wear_cob_icon)
        basalIcon = findViewById(R.id.wear_basal_icon)
        therapyRow = findViewById(R.id.wear_therapy_row)
        chart = findViewById(R.id.wear_glucose_chart)
        watchFacePushStatus = findViewById(R.id.wear_watchface_push_status)
    }

    private fun requestPhoneRefresh(initial: Boolean = false) {
        if (!::connection.isInitialized) return

        if (!initial) {
            syncHint.text = "Werte werden synchronisiert"
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

    private fun render(refreshClock: Boolean = false) {
        if (!::glucose.isInitialized) return

        val now = System.currentTimeMillis()
        val preferences = WearDisplayPreferences.read(this)
        val state = G7LocalReadingResolver.resolve(this, latest, now, preferences.dataSource)
        val previousState = lastRenderedState
        val glucoseState = state?.glucose
        val previousPreferences = lastRenderedPreferences
        val firstRender = !hasRendered

        if (firstRender || previousPreferences?.uiColors != preferences.uiColors) {
            applyUiColors(preferences)
        }

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

        val targetLow = state?.target?.lowMgDl ?: 80.0
        val targetHigh = state?.target?.highMgDl ?: 160.0
        val resolvedUnit =
            resolveUnit(
                glucoseState?.displayUnit,
                preferences.glucoseUnit,
            )

        val glucoseSectionChanged =
            firstRender ||
                refreshClock ||
                previousState?.glucose != glucoseState ||
                previousState?.target != state?.target ||
                previousPreferences?.glucoseUnit != preferences.glucoseUnit ||
                previousPreferences.uiColors != preferences.uiColors

        if (glucoseSectionChanged) {
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
                when {
                    !canShowValue -> preferences.uiColors.textPrimary
                    glucoseState.valueMgDl < targetLow -> preferences.uiColors.glucoseLow
                    glucoseState.valueMgDl > targetHigh -> preferences.uiColors.glucoseHigh
                    else -> preferences.uiColors.glucoseInRange
                },
            )

            renderTrend(
                if (canShowValue) glucoseState.trend else null,
                preferences.uiColors.textPrimary,
            )

            delta.text =
                if (canShowValue) {
                    formatDelta(
                        glucoseState.deltaMgDl,
                        resolvedUnit,
                    )
                } else {
                    "—"
                }
            age.text = ageMinutes(glucoseState?.measuredAtEpochMs, now)
        }

        chart.bind(
            newState = state,
            graphHours = preferences.graphHours,
            showPredictions = preferences.showPredictions,
            colors = preferences.graphColors,
            style = preferences.graphStyle,
        )
        if (refreshClock) chart.invalidate()

        if (
            firstRender ||
            previousPreferences?.showTherapyStats != preferences.showTherapyStats ||
            previousState?.insulin != state?.insulin ||
            previousState?.carbs != state?.carbs ||
            previousState?.basal != state?.basal
        ) {
            therapyRow.visibility =
                if (preferences.showTherapyStats) View.VISIBLE else View.GONE
            findViewById<View>(R.id.wear_basal_card).visibility =
                if (preferences.showTherapyStats) View.VISIBLE else View.GONE

            iob.text = formatNumber(state?.insulin?.totalIob, 2, " U")
            cob.text = formatNumber(state?.carbs?.cobGrams, 0, " g")
            basal.text =
                formatNumber(
                    basalDisplayUnitsPerHour(state?.basal),
                    2,
                    " U/h",
                )
            basalIcon.setImageResource(basalIconResource(state?.basal))
        }

        if (
            firstRender ||
            previousState?.source != state?.source ||
            previousState?.sourceVersion != state?.sourceVersion
        ) {
            source.text =
                when (state?.source) {
                    DataSourceId.DEXCOM_G7_WATCH -> "Dexcom G7 Watch"
                    DataSourceId.ANDROID_APS -> "AndroidAPS"
                    DataSourceId.NIGHTSCOUT -> "Nightscout"
                    DataSourceId.XDRIP_PLUS ->
                        state.sourceVersion
                            ?.let { "xDrip+ $it" }
                            ?: "xDrip+"
                    DataSourceId.OTHER -> "Other"
                    null -> "Datenquelle nicht verfügbar"
                }
        }

        if (
            firstRender ||
            lastRenderedConnectedNodes != connectedNodes ||
            previousPreferences?.uiColors != preferences.uiColors
        ) {
            connection.text =
                if (connectedNodes > 0) {
                    "● Telefon verbunden"
                } else {
                    "○ Telefon nicht erreichbar"
                }
            connection.setTextColor(
                if (connectedNodes > 0) {
                    preferences.uiColors.accent
                } else {
                    preferences.uiColors.textSecondary
                },
            )
        }

        renderWatchFacePushStatus()
        lastRenderedState = state
        lastRenderedPreferences = preferences
        lastRenderedConnectedNodes = connectedNodes
        hasRendered = true
    }

    private fun renderTrend(
        trend: app.aapswear.model.Trend?,
        color: Int,
    ) {
        val spec = trend?.let(TrendVisuals::spec)
        if (spec == null) {
            trendContainer.visibility = View.GONE
            return
        }

        trendContainer.visibility = View.VISIBLE
        val tint = ColorStateList.valueOf(color)
        trendArrow1.imageTintList = tint
        trendArrow2.imageTintList = tint
        trendArrow1.rotation = spec.rotationDegrees
        trendArrow2.rotation = spec.rotationDegrees
        trendArrow1.visibility = View.VISIBLE
        trendArrow2.visibility =
            if (spec.arrowCount == 2) View.VISIBLE else View.GONE
    }

    private fun applyUiColors(preferences: WearDisplayPreferences) {
        val ui = preferences.uiColors
        findViewById<View>(R.id.wear_root).setBackgroundColor(ui.background)

        listOf(
            R.id.wear_glucose_card,
            R.id.wear_basal_card,
            R.id.wear_iob_card,
            R.id.wear_cob_card,
            R.id.wear_connection_card,
            R.id.wear_settings_action,
        ).forEach { id ->
            findViewById<View>(id).background =
                roundedBackground(
                    ui.tileBackground,
                    ui.tileBorder,
                )
        }

        // The chart paints its own background, target range, grid and outer contour.
        findViewById<View>(R.id.wear_graph_card).background = null

        listOf(
            R.id.wear_glucose,
            R.id.wear_basal,
            R.id.wear_iob,
            R.id.wear_cob,
            R.id.wear_source,
            R.id.wear_settings_label,
        ).forEach { id ->
            findViewById<TextView>(id).setTextColor(ui.textPrimary)
        }
        listOf(
            R.id.wear_delta,
            R.id.wear_age,
            R.id.wear_sync_hint,
            R.id.wear_watchface_push_status,
            R.id.wear_footer_text,
        ).forEach { id ->
            findViewById<TextView>(id).setTextColor(ui.textSecondary)
        }

        iobIcon.imageTintList = ColorStateList.valueOf(ui.iob)
        cobIcon.imageTintList = ColorStateList.valueOf(ui.cob)
        basalIcon.imageTintList = ColorStateList.valueOf(ui.basal)

        val primaryTint = ColorStateList.valueOf(ui.textPrimary)
        findViewById<ImageView>(R.id.wear_settings_icon).imageTintList = primaryTint
        findViewById<ImageView>(R.id.wear_footer_icon).imageTintList =
            ColorStateList.valueOf(ui.accent)
    }

    private fun roundedBackground(
        fill: Int,
        border: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * resources.displayMetrics.density
            setColor(fill)
            setStroke(
                (1f * resources.displayMetrics.density).roundToInt().coerceAtLeast(1),
                border,
            )
        }

    private fun requestWatchFacePermissionOnFirstLaunch() {
        val onboarding =
            getSharedPreferences(
                ONBOARDING_PREFS,
                Context.MODE_PRIVATE,
            )
        if (onboarding.getBoolean(KEY_WFP_PERMISSION_REQUESTED, false)) return

        onboarding
            .edit()
            .putBoolean(KEY_WFP_PERMISSION_REQUESTED, true)
            .apply()

        if (
            SugarliciousWatchFacePush.isSupported() &&
            !SugarliciousWatchFacePush.hasActivationPermission(this)
        ) {
            requestPermissions(
                arrayOf(
                    SugarliciousWatchFacePush.ACTIVE_PERMISSION,
                ),
                WATCH_FACE_PERMISSION_REQUEST,
            )
        }
    }

    private fun renderWatchFacePushStatus() {
        if (!::watchFacePushStatus.isInitialized) return

        watchFacePushStatus.text =
            when {
                !SugarliciousWatchFacePush.isSupported() ->
                    "Watchface-Direktwechsel: Wear OS 6+ erforderlich"
                SugarliciousWatchFacePush.hasActivationPermission(this) ->
                    "Watchface-Direktwechsel freigegeben"
                else ->
                    "Watchface-Direktwechsel nicht freigegeben"
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
        if (requestCode == WATCH_FACE_PERMISSION_REQUEST) {
            renderWatchFacePushStatus()
        }
    }

    private fun resolveUnit(
        stateUnit: GlucoseUnit?,
        preference: WatchGlucoseUnit,
    ): GlucoseUnit =
        when (preference) {
            WatchGlucoseUnit.AAPS -> stateUnit ?: GlucoseUnit.MG_DL
            WatchGlucoseUnit.MG_DL -> GlucoseUnit.MG_DL
            WatchGlucoseUnit.MMOL_L -> GlucoseUnit.MMOL_L
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
            valueMgDl.roundToInt().toString()
        }

    private fun formatDelta(
        valueMgDl: Double?,
        unit: GlucoseUnit,
    ): String {
        valueMgDl ?: return "—"
        val value =
            if (unit == GlucoseUnit.MMOL_L) valueMgDl / 18.0 else valueMgDl
        val prefix = if (value >= 0.0) "+" else ""
        return prefix +
            if (unit == GlucoseUnit.MMOL_L) {
                String.format(Locale.getDefault(), "%.1f", value)
            } else {
                value.roundToInt().toString()
            }
    }

    private fun ageMinutes(
        timestamp: Long?,
        now: Long,
    ): String =
        timestamp
            ?.let {
                "${((now - it).coerceAtLeast(0L) / 60_000L)} min"
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
        private const val WATCH_FACE_PERMISSION_REQUEST = 701
        private const val ONBOARDING_PREFS = "wear_onboarding"
        private const val KEY_WFP_PERMISSION_REQUESTED = "watchface_permission_requested"
    }
}

internal fun basalDisplayUnitsPerHour(basal: BasalState?): Double? =
    basal?.currentUnitsPerHour

internal fun basalIconResource(basal: BasalState?): Int {
    val absolute = basal?.tempAbsoluteUnitsPerHour
    val base = basal?.currentUnitsPerHour
    val percent = basal?.tempPercent
    return when {
        absolute != null && base != null && absolute > base + BASAL_COMPARE_EPSILON ->
            ComplicationR.drawable.ic_complication_basal_more
        absolute != null && base != null && absolute < base - BASAL_COMPARE_EPSILON ->
            ComplicationR.drawable.ic_complication_basal_less
        percent != null && percent > 100 ->
            ComplicationR.drawable.ic_complication_basal_more
        percent != null && percent < 100 ->
            ComplicationR.drawable.ic_complication_basal_less
        else -> ComplicationR.drawable.ic_complication_basal
    }
}

private const val BASAL_COMPARE_EPSILON = 0.001
