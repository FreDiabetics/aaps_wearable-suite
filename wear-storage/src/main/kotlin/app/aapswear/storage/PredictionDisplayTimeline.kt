package app.aapswear.storage

import app.aapswear.model.GlucosePrediction

/**
 * Builds a display-only timeline for prediction curves.
 *
 * AAPS prediction packets commonly begin at the timestamp of the last CGM reading. If those
 * timestamps are drawn literally, their first dots overlap the CGM dot or drift left of the
 * current-time divider while a cached packet is retained. Re-anchoring keeps every curve directly
 * to the right of the divider without mutating the persisted source data.
 */
object PredictionDisplayTimeline {
    const val LEAD_IN_MS = 15_000L
    const val MAX_DISPLAY_WINDOW_MS = 2L * 60L * 60_000L

    fun anchor(
        predictions: List<GlucosePrediction>,
        nowEpochMs: Long,
    ): List<GlucosePrediction> =
        predictions.mapNotNull { series ->
            val samples =
                series.samples
                    .filter { it.valueMgDl.isFinite() && it.valueMgDl in 20.0..1000.0 }
                    .distinctBy { it.measuredAtEpochMs }
                    .sortedBy { it.measuredAtEpochMs }
            val firstTimestamp = samples.firstOrNull()?.measuredAtEpochMs ?: return@mapNotNull null
            val offset = nowEpochMs + LEAD_IN_MS - firstTimestamp
            series.copy(
                samples =
                    samples.map { sample ->
                        sample.copy(measuredAtEpochMs = safeAdd(sample.measuredAtEpochMs, offset))
                    },
            )
        }

    fun futureWindowMs(
        predictions: List<GlucosePrediction>,
        nowEpochMs: Long,
    ): Long =
        anchor(predictions, nowEpochMs)
            .flatMap { it.samples }
            .maxOfOrNull { it.measuredAtEpochMs }
            ?.minus(nowEpochMs)
            ?.coerceIn(0L, MAX_DISPLAY_WINDOW_MS)
            ?: 0L

    private fun safeAdd(value: Long, offset: Long): Long =
        runCatching { Math.addExact(value, offset) }.getOrElse {
            if (offset >= 0L) Long.MAX_VALUE else Long.MIN_VALUE
        }
}
