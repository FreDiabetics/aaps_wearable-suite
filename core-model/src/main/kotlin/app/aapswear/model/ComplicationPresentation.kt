package app.aapswear.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Canonical complication presentation shared by Wear providers and phone previews.
 *
 * Short-text complications deliberately keep the main value, optional secondary value and trend
 * separate. Wear OS can then decide how to lay out text and icon without us concatenating a long
 * string that gets clipped in small slots.
 */
data class ComplicationPresentation(
    val text: String,
    val title: String? = null,
    val trend: Trend? = null,
    val contentDescription: String,
)

data class TrendVisualSpec(
    val rotationDegrees: Float,
    val arrowCount: Int = 1,
)

object TrendVisuals {
    fun spec(trend: Trend): TrendVisualSpec? = when (trend) {
        Trend.DOUBLE_UP -> TrendVisualSpec(-90f, 2)
        Trend.SINGLE_UP -> TrendVisualSpec(-90f)
        Trend.FORTY_FIVE_UP -> TrendVisualSpec(-45f)
        Trend.FLAT -> TrendVisualSpec(0f)
        Trend.FORTY_FIVE_DOWN -> TrendVisualSpec(45f)
        Trend.SINGLE_DOWN -> TrendVisualSpec(90f)
        Trend.DOUBLE_DOWN -> TrendVisualSpec(90f, 2)
        Trend.UNKNOWN -> null
    }
}

object SugarliciousComplicationIds {
    const val GLUCOSE = 1
    const val GLUCOSE_TREND = 2
    const val TIME_DELTA = 3
    const val GLUCOSE_TREND_DELTA = 4
    const val GLUCOSE_AGE = 5
    const val GRAPH = 9
    const val IOB = 11
    const val COB = 14
    const val BASAL = 16
    const val LOOP = 19
    const val RESERVOIR = 22
    const val GLUCOSE_PLUS_DELTA = 29
    const val SENSOR_AGE = 30
    const val TIR = 31
    const val GLUCOSE_TREND_DELTA_AGE = 32
    const val GLUCOSE_TREND_AGE = 33
    const val IOB_COB_BASAL = 34
    const val TREND_ONLY = 35
    const val DELTA_ONLY = 36

    val ordered = listOf(
        GLUCOSE,
        TREND_ONLY,
        DELTA_ONLY,
        GLUCOSE_AGE,
        BASAL,
        IOB,
        COB,
        GLUCOSE_TREND,
        GLUCOSE_PLUS_DELTA,
        TIME_DELTA,
        GLUCOSE_TREND_AGE,
        GLUCOSE_TREND_DELTA,
        GLUCOSE_TREND_DELTA_AGE,
        IOB_COB_BASAL,
        LOOP,
        RESERVOIR,
        SENSOR_AGE,
        TIR,
        GRAPH,
    )
}

object ComplicationPresentationFormatter {
    fun format(
        id: Int,
        state: TherapyDisplayState?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ComplicationPresentation {
        val glucose = state?.glucose
        val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, nowEpochMs)
        val displayable = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
        val liveGlucose = glucose.takeIf { displayable }
        val glucoseText = liveGlucose?.let(TherapyDisplayFormatter::glucose) ?: DASH
        val delta = liveGlucose?.let { TherapyDisplayFormatter.signedDelta(it.deltaMgDl, it.displayUnit) }.orEmpty()
        val age = TherapyDisplayFormatter.ageMinutes(glucose?.measuredAtEpochMs, nowEpochMs)
        val trend = liveGlucose?.trend?.takeUnless { it == Trend.UNKNOWN }

        return when (id) {
            SugarliciousComplicationIds.GLUCOSE ->
                p(glucoseText, desc = "Glukose $glucoseText")

            SugarliciousComplicationIds.TREND_ONLY ->
                p(
                    text = trend?.let(TherapyDisplayFormatter::trendArrow).orEmpty().ifBlank { DASH },
                    trend = trend,
                    desc = trend?.let { "Glukosetrend ${TherapyDisplayFormatter.trendArrow(it)}" } ?: "Kein Glukosetrend",
                )

            SugarliciousComplicationIds.DELTA_ONLY ->
                p(delta.ifBlank { DASH }, desc = "Delta ${delta.ifBlank { DASH }}")

            SugarliciousComplicationIds.GLUCOSE_AGE ->
                p(age, desc = "Glukosewert vor $age")

            SugarliciousComplicationIds.BASAL -> {
                val basal = TherapyDisplayFormatter.units(state?.basal?.currentUnitsPerHour, "U/h", 2)
                p(basal, desc = "Basal $basal")
            }

            SugarliciousComplicationIds.IOB -> {
                val iob = TherapyDisplayFormatter.units(state?.insulin?.totalIob, "U", 2)
                p(iob, desc = "IOB $iob")
            }

            SugarliciousComplicationIds.COB -> {
                val cob = TherapyDisplayFormatter.units(state?.carbs?.cobGrams, "g", 0)
                p(cob, desc = "COB $cob")
            }

            SugarliciousComplicationIds.GLUCOSE_TREND ->
                p(glucoseText, trend = trend, desc = "Glukose $glucoseText mit Trend")

            SugarliciousComplicationIds.GLUCOSE_PLUS_DELTA ->
                p(glucoseText, delta.ifBlank { DASH }, desc = "Glukose $glucoseText, Delta ${delta.ifBlank { DASH }}")

            SugarliciousComplicationIds.TIME_DELTA ->
                p(delta.ifBlank { DASH }, age, desc = "Delta ${delta.ifBlank { DASH }}, Wert vor $age")

            SugarliciousComplicationIds.GLUCOSE_TREND_AGE ->
                p(glucoseText, age, trend, "Glukose $glucoseText mit Trend, Wert vor $age")

            SugarliciousComplicationIds.GLUCOSE_TREND_DELTA ->
                p(glucoseText, delta.ifBlank { DASH }, trend, "Glukose $glucoseText mit Trend, Delta ${delta.ifBlank { DASH }}")

            SugarliciousComplicationIds.GLUCOSE_TREND_DELTA_AGE -> {
                val secondary = listOf(delta.ifBlank { DASH }, age).joinToString(" · ")
                p(glucoseText, secondary, trend, "Glukose $glucoseText mit Trend, Delta ${delta.ifBlank { DASH }}, Wert vor $age")
            }

            SugarliciousComplicationIds.IOB_COB_BASAL -> {
                val basal = TherapyDisplayFormatter.units(state?.basal?.currentUnitsPerHour, "U/h", 2)
                val iob = TherapyDisplayFormatter.units(state?.insulin?.totalIob, "U", 1)
                val cob = TherapyDisplayFormatter.units(state?.carbs?.cobGrams, "g", 0)
                p("$iob · $cob", basal, desc = "Basal $basal, IOB $iob, COB $cob")
            }

            SugarliciousComplicationIds.LOOP -> {
                val loop = when (state?.loop?.status?.lowercase()) {
                    "enacted", "closed", "loop", "on", "enabled" -> "ON"
                    "suggested" -> "AUTO"
                    null -> DASH
                    else -> state.loop.status?.take(8) ?: DASH
                }
                p(loop, desc = "AndroidAPS Loop $loop")
            }

            SugarliciousComplicationIds.RESERVOIR -> {
                val reservoir = TherapyDisplayFormatter.units(state?.pump?.reservoirUnits, "U", 0)
                p(reservoir, state?.pump?.status?.takeIf { it.isNotBlank() }, desc = "Reservoir $reservoir")
            }

            SugarliciousComplicationIds.SENSOR_AGE ->
                p(DASH, desc = "Sensoralter nicht verfügbar")

            SugarliciousComplicationIds.TIR -> {
                val tir = tirPercent(state, nowEpochMs)
                val value = tir?.let { "$it%" } ?: DASH
                p(value, "70–180", desc = "TIR $value")
            }

            else -> p(DASH, desc = "Keine Daten")
        }
    }

    fun tirPercent(state: TherapyDisplayState?, nowEpochMs: Long): Int? {
        val cutoff = nowEpochMs - 24L * 60L * 60_000L
        val samples = state?.glucoseHistory.orEmpty().filter {
            it.measuredAtEpochMs in cutoff..(nowEpochMs + FreshnessPolicy.FUTURE_TOLERANCE_MS) &&
                it.valueMgDl in 20.0..1000.0
        }
        if (samples.isEmpty()) return null
        return (samples.count { it.valueMgDl in 70.0..180.0 } * 100.0 / samples.size).roundToInt()
    }

    private fun p(
        text: String,
        title: String? = null,
        trend: Trend? = null,
        desc: String,
    ) = ComplicationPresentation(text = text, title = title, trend = trend, contentDescription = desc)

    private const val DASH = "—"
}
