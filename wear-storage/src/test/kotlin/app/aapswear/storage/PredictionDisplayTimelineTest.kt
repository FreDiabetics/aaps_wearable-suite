package app.aapswear.storage

import app.aapswear.model.GlucosePrediction
import app.aapswear.model.GlucoseSample
import app.aapswear.model.PredictionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionDisplayTimelineTest {
    @Test
    fun `all prediction kinds begin at current time divider`() {
        val now = 1_000_000L
        val predictions =
            listOf(
                prediction(PredictionKind.IOB, now - 10 * 60_000L),
                prediction(PredictionKind.UAM, now + 5 * 60_000L),
            )

        val anchored = PredictionDisplayTimeline.anchor(predictions, now)

        anchored.forEach { series ->
            assertEquals(now + PredictionDisplayTimeline.LEAD_IN_MS, series.samples.first().measuredAtEpochMs)
            assertEquals(now, series.samples.first().measuredAtEpochMs)
            assertEquals(5 * 60_000L, series.samples[1].measuredAtEpochMs - series.samples[0].measuredAtEpochMs)
        }
    }

    @Test
    fun `cached prediction duration remains visible as current time advances`() {
        val receivedAt = 1_000_000L
        val later = receivedAt + 40 * 60_000L
        val predictions = listOf(prediction(PredictionKind.IOB, receivedAt))

        assertEquals(
            5 * 60_000L + PredictionDisplayTimeline.LEAD_IN_MS,
            PredictionDisplayTimeline.futureWindowMs(predictions, later),
        )
    }

    private fun prediction(kind: PredictionKind, first: Long) =
        GlucosePrediction(
            kind,
            listOf(
                GlucoseSample(120.0, first),
                GlucoseSample(125.0, first + 5 * 60_000L),
            ),
        )
}
