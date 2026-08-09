package app.aapswear.mobile

import app.aapswear.model.GlucoseSample
import app.aapswear.model.DataSourceId
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.TherapyHistorySample

/** Keeps a bounded graph cache inside the single latest display state. */
internal object DisplayHistoryAccumulator {
    const val WINDOW_MS = 24 * 60 * 60_000L
    const val MAX_POINTS = 300
    const val GAP_THRESHOLD_MS = 7 * 60_000L + 30_000L
    private const val SAME_READING_TOLERANCE_MS = 90_000L

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
                previous?.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, previous.source)) }
                addAll(current.glucoseHistory)
                current.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, current.source)) }
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
                basalUnitsPerHour = current.basal?.tempAbsoluteUnitsPerHour
                    ?: current.basal?.currentUnitsPerHour,
                baseBasalUnitsPerHour = current.basal?.currentUnitsPerHour,
                tempBasalUnitsPerHour = current.basal?.tempAbsoluteUnitsPerHour,
            )
            if (sample.totalIob != null || sample.cobGrams != null || sample.basalUnitsPerHour != null) add(sample)
        }.filter { it.measuredAtEpochMs in earliest..latest }
            .associateBy { it.measuredAtEpochMs }
            .values
            .sortedBy { it.measuredAtEpochMs }
            .takeLast(MAX_POINTS)
            .withEstimatedInsulinActivity()

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
                current.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, current.source)) }
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
        val eligible = values
            .filter {
                it.measuredAtEpochMs in earliest..latest &&
                    it.valueMgDl.isFinite() &&
                    it.valueMgDl in 20.0..1000.0
            }
            .sortedBy { it.measuredAtEpochMs }

        val deduplicated = mutableListOf<GlucoseSample>()
        eligible.forEach { candidate ->
            val duplicateIndex = deduplicated.indexOfLast {
                candidate.measuredAtEpochMs - it.measuredAtEpochMs <= SAME_READING_TOLERANCE_MS
            }
            if (duplicateIndex < 0) {
                deduplicated += candidate
            } else {
                val existing = deduplicated[duplicateIndex]
                val preferred = when {
                    existing.source == candidate.source && candidate.measuredAtEpochMs >= existing.measuredAtEpochMs -> candidate
                    existing.source == DataSourceId.ANDROID_APS -> existing
                    candidate.source == DataSourceId.ANDROID_APS -> candidate
                    candidate.measuredAtEpochMs >= existing.measuredAtEpochMs -> candidate
                    else -> existing
                }
                deduplicated[duplicateIndex] = preferred
            }
        }
        return deduplicated.sortedBy { it.measuredAtEpochMs }.takeLast(MAX_POINTS)
    }

    private fun List<TherapyHistorySample>.withEstimatedInsulinActivity(): List<TherapyHistorySample> =
        mapIndexed { index, sample ->
            if (sample.insulinActivityUnitsPerMinute != null || index == 0) return@mapIndexed sample
            val previous = this[index - 1]
            val minutes = (sample.measuredAtEpochMs - previous.measuredAtEpochMs) / 60_000.0
            val priorIob = previous.totalIob
            val currentIob = sample.totalIob
            val decay = if (priorIob != null && currentIob != null && minutes in 2.0..15.0) {
                (priorIob - currentIob).coerceIn(0.0, 1.5) / minutes
            } else {
                null
            }
            sample.copy(insulinActivityUnitsPerMinute = decay?.takeIf { it > 0.0001 })
        }
}
