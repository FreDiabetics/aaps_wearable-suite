package app.aapswear.mobile

import app.aapswear.model.GlucoseSample
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.TherapyHistorySample

/** Keeps a bounded graph cache inside the single latest display state. */
internal object DisplayHistoryAccumulator {
    const val WINDOW_MS = 24 * 60 * 60_000L
    const val MAX_POINTS = 300
    const val GAP_THRESHOLD_MS = 7 * 60_000L + 30_000L

    /** True when two persisted CGM points are farther apart than a normal 5-minute cycle. */
    fun hasGap(history: List<GlucoseSample>): Boolean =
        history.sortedBy { it.measuredAtEpochMs }.zipWithNext().any { (first, second) ->
            second.measuredAtEpochMs - first.measuredAtEpochMs > GAP_THRESHOLD_MS
        }

    fun merge(
        previous: TherapyDisplayState?,
        current: TherapyDisplayState,
        nowEpochMs: Long,
    ): TherapyDisplayState {
        val glucose = mergeGlucose(
            buildList {
                addAll(previous?.glucoseHistory.orEmpty())
                previous?.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)) }
                addAll(current.glucoseHistory)
                current.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)) }
            },
            nowEpochMs,
        )

        val earliest = nowEpochMs - WINDOW_MS
        val latest = nowEpochMs + 5 * 60_000L
        val therapy = buildList {
            addAll(previous?.therapyHistory.orEmpty())
            val timestamp = current.glucose?.measuredAtEpochMs ?: current.receivedAtEpochMs
            val sample = TherapyHistorySample(
                measuredAtEpochMs = timestamp,
                totalIob = current.insulin?.totalIob,
                cobGrams = current.carbs?.cobGrams,
                basalUnitsPerHour = current.basal?.currentUnitsPerHour,
            )
            if (sample.totalIob != null || sample.cobGrams != null || sample.basalUnitsPerHour != null) add(sample)
        }.filter { it.measuredAtEpochMs in earliest..latest }
            .associateBy { it.measuredAtEpochMs }
            .values
            .sortedBy { it.measuredAtEpochMs }
            .takeLast(MAX_POINTS)

        return current.copy(glucoseHistory = glucose, therapyHistory = therapy)
    }

    fun mergeExternalHistory(
        current: TherapyDisplayState,
        external: List<GlucoseSample>,
        nowEpochMs: Long,
    ): TherapyDisplayState = current.copy(
        glucoseHistory = mergeGlucose(
            buildList {
                addAll(current.glucoseHistory)
                current.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)) }
                addAll(external)
            },
            nowEpochMs,
        ),
    )

    private fun mergeGlucose(
        values: List<GlucoseSample>,
        nowEpochMs: Long,
    ): List<GlucoseSample> {
        val earliest = nowEpochMs - WINDOW_MS
        val latest = nowEpochMs + 5 * 60_000L
        return values
            .filter {
                it.measuredAtEpochMs in earliest..latest &&
                    it.valueMgDl.isFinite() &&
                    it.valueMgDl in 20.0..1000.0
            }
            .associateBy { it.measuredAtEpochMs }
            .values
            .sortedBy { it.measuredAtEpochMs }
            .takeLast(MAX_POINTS)
    }
}
