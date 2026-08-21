package app.aapswear.model

import kotlin.math.abs

/** Shared validation, ordering and identity-safe deduplication for canonical CGM history. */
object CanonicalCgmHistory {
    const val DEFAULT_WINDOW_MS = 24L * 60L * 60_000L
    const val DEFAULT_FUTURE_TOLERANCE_MS = 5L * 60_000L
    const val DEFAULT_MAX_POINTS = 300
    private const val SAME_MEASUREMENT_TOLERANCE_MS = 90_000L

    fun merge(
        samples: List<GlucoseSample>,
        nowEpochMs: Long,
        preferredSource: DataSourceId? = null,
        windowMs: Long = DEFAULT_WINDOW_MS,
        futureToleranceMs: Long = DEFAULT_FUTURE_TOLERANCE_MS,
        maxPoints: Int = DEFAULT_MAX_POINTS,
    ): List<GlucoseSample> {
        require(windowMs > 0L)
        require(futureToleranceMs >= 0L)
        require(maxPoints > 0)
        val result = mutableListOf<GlucoseSample>()

        samples
            .asSequence()
            .filter {
                it.quality == CgmQuality.VALID &&
                    it.valueMgDl.isFinite() &&
                    it.valueMgDl in 20.0..1_000.0 &&
                    nowEpochMs - it.measuredAtEpochMs <= windowMs &&
                    it.measuredAtEpochMs <= nowEpochMs + futureToleranceMs &&
                    (it.receivedAtEpochMs == null ||
                        (it.receivedAtEpochMs >= it.measuredAtEpochMs - futureToleranceMs &&
                            it.receivedAtEpochMs <= nowEpochMs + futureToleranceMs))
            }
            .sortedBy(GlucoseSample::measuredAtEpochMs)
            .forEach { candidate ->
                val duplicateIndex = result.indexOfFirst { existing -> existing.sameMeasurement(candidate) }
                if (duplicateIndex < 0) {
                    result += candidate
                } else {
                    result[duplicateIndex] = prefer(result[duplicateIndex], candidate, preferredSource)
                }
            }

        return result.sortedBy(GlucoseSample::measuredAtEpochMs).takeLast(maxPoints)
    }

    private fun prefer(
        existing: GlucoseSample,
        candidate: GlucoseSample,
        preferredSource: DataSourceId?,
    ): GlucoseSample = when {
        existing.source == preferredSource && candidate.source != preferredSource -> existing
        candidate.source == preferredSource && existing.source != preferredSource -> candidate
        (candidate.receivedAtEpochMs ?: candidate.measuredAtEpochMs) >=
            (existing.receivedAtEpochMs ?: existing.measuredAtEpochMs) -> candidate
        else -> existing
    }

    private fun GlucoseSample.sameMeasurement(other: GlucoseSample): Boolean {
        if (
            sensorId != null && sessionId != null && sequenceNumber != null &&
            other.sensorId != null && other.sessionId != null && other.sequenceNumber != null
        ) {
            return sensorId == other.sensorId &&
                sessionId == other.sessionId &&
                sequenceNumber == other.sequenceNumber
        }
        if (sensorId != null && other.sensorId != null && sensorId != other.sensorId) return false
        if (sessionId != null && other.sessionId != null && sessionId != other.sessionId) return false

        val timeDifference = abs(measuredAtEpochMs - other.measuredAtEpochMs)
        if (timeDifference == 0L && source == other.source) return true
        return timeDifference <= SAME_MEASUREMENT_TOLERANCE_MS &&
            abs(valueMgDl - other.valueMgDl) <= 5.0
    }
}
