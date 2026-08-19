package app.aapswear.complications

import app.aapswear.model.DataSourceId
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class G6StyleComplicationsTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `current reading renders value trend and unit`() {
        val state = state(measuredAt = now - 2 * 60_000L)
        val header = G6StylePresentationFormatter.header(state, now)
        assertEquals("152 →", header.text)
        assertEquals("mg/dL", header.title)
    }

    @Test
    fun `stale reading is never rendered as current value`() {
        val state = state(measuredAt = now - 16 * 60_000L)
        val header = G6StylePresentationFormatter.header(state, now)
        assertEquals("—", header.text)
        assertEquals("VERALTET", header.title)
    }

    @Test
    fun `no source is explicit in status`() {
        val status = G6StylePresentationFormatter.status(null, now)
        assertEquals("Keine Quelle", status.text)
        assertTrue(status.title.contains("KEINE DATEN"))
    }

    @Test
    fun `graph keeps only valid samples in three hour window`() {
        val state = state(measuredAt = now - 60_000L).copy(
            glucoseHistory = listOf(
                GlucoseSample(110.0, now - G6StylePresentationFormatter.GRAPH_WINDOW_MS - 1L),
                GlucoseSample(120.0, now - 2 * 60 * 60_000L),
                GlucoseSample(130.0, now - 10 * 60_000L),
                GlucoseSample(2_000.0, now - 5 * 60_000L),
            ),
        )
        val samples = G6StylePresentationFormatter.samples(state, now)
        assertTrue(samples.any { it.valueMgDl == 120.0 })
        assertTrue(samples.any { it.valueMgDl == 130.0 })
        assertTrue(samples.none { it.valueMgDl == 110.0 || it.valueMgDl == 2_000.0 })
    }

    private fun state(measuredAt: Long) =
        TherapyDisplayState(
            source = DataSourceId.DEXCOM_G7_WATCH,
            receivedAtEpochMs = now,
            glucose = GlucoseState(
                valueMgDl = 152.0,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.FLAT,
                measuredAtEpochMs = measuredAt,
            ),
        )
}
