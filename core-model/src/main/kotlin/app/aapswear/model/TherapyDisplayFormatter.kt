package app.aapswear.model

import java.util.Locale
import kotlin.math.roundToInt

/** Pure, deterministic display formatting shared by complications, Tiles, widgets and tests. */
object TherapyDisplayFormatter {
    fun glucose(glucose: GlucoseState): String =
        if (glucose.displayUnit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f", glucose.valueMgDl / 18.0)
        } else {
            glucose.valueMgDl.roundToInt().toString()
        }

    fun signedDelta(valueMgDl: Double?, unit: GlucoseUnit): String = valueMgDl?.let {
        val displayValue = if (unit == GlucoseUnit.MMOL_L) it / 18.0 else it
        val prefix = if (displayValue >= 0.0) "+" else ""
        prefix + if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.US, "%.1f", displayValue)
        } else {
            displayValue.roundToInt().toString()
        }
    } ?: ""

    fun trendArrow(trend: Trend): String = when (trend) {
        Trend.DOUBLE_DOWN -> "⇊"
        Trend.SINGLE_DOWN -> "↓"
        Trend.FORTY_FIVE_DOWN -> "↘"
        Trend.FLAT -> "→"
        Trend.FORTY_FIVE_UP -> "↗"
        Trend.SINGLE_UP -> "↑"
        Trend.DOUBLE_UP -> "⇈"
        Trend.UNKNOWN -> ""
    }

    fun units(value: Double?, suffix: String, digits: Int): String =
        value?.let { String.format(Locale.US, "%.${digits}f%s", it, suffix) } ?: "—"

    fun percent(value: Int?): String = value?.let { "$it%" } ?: "—"

    fun ageMinutes(timestampEpochMs: Long?, nowEpochMs: Long): String =
        ageMinutesValue(timestampEpochMs, nowEpochMs)?.let { "${it}m" } ?: "—"

    fun ageMinutesValue(timestampEpochMs: Long?, nowEpochMs: Long): Long? =
        timestampEpochMs?.let { ((nowEpochMs - it).coerceAtLeast(0L) / 60_000L) }

    fun freshness(state: TherapyDisplayState?, nowEpochMs: Long): Freshness =
        FreshnessPolicy.classify(state?.glucose?.measuredAtEpochMs, nowEpochMs)

    fun freshnessLabel(freshness: Freshness): String = when (freshness) {
        Freshness.CURRENT -> "AKTUELL"
        Freshness.DELAYED -> "VERZÖGERT"
        Freshness.STALE -> "VERALTET"
        Freshness.NO_DATA -> "KEINE DATEN"
    }

    fun isGlucoseDisplayable(state: TherapyDisplayState?, nowEpochMs: Long): Boolean =
        when (freshness(state, nowEpochMs)) {
            Freshness.CURRENT, Freshness.DELAYED -> true
            Freshness.STALE, Freshness.NO_DATA -> false
        }

    fun sourceName(source: DataSourceId?): String = when (source) {
        DataSourceId.DEXCOM_G7_WATCH -> "Watch Direct"
        DataSourceId.ANDROID_APS -> "AndroidAPS"
        DataSourceId.NIGHTSCOUT -> "Nightscout"
        DataSourceId.XDRIP_PLUS -> "xDrip+"
        DataSourceId.OTHER -> "Andere Quelle"
        null -> ""
    }

    fun target(target: TargetState?, unit: GlucoseUnit): String {
        if (target?.lowMgDl == null && target?.highMgDl == null) return "—"
        return listOfNotNull(target.lowMgDl, target.highMgDl).joinToString("–") {
            if (unit == GlucoseUnit.MMOL_L) String.format(Locale.US, "%.1f", it / 18.0)
            else it.roundToInt().toString()
        }
    }
}