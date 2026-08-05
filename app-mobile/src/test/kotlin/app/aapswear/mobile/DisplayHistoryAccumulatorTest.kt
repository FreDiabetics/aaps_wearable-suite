package app.aapswear.mobile

import app.aapswear.model.CarbState
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.InsulinState
import app.aapswear.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayHistoryAccumulatorTest {
    @Test fun `deduplicates and bounds display history`() {
        val now = 2 * DisplayHistoryAccumulator.WINDOW_MS
        fun state(at: Long, glucose: Double) = TherapyDisplayState(
            receivedAtEpochMs = at,
            glucose = GlucoseState(glucose, GlucoseUnit.MG_DL, measuredAtEpochMs = at),
            insulin = InsulinState(totalIob = glucose / 100),
            carbs = CarbState(cobGrams = glucose / 10),
        )
        val old = state(now - DisplayHistoryAccumulator.WINDOW_MS - 1, 90.0)
        val first = DisplayHistoryAccumulator.merge(null, old, now)
        assertEquals(0, first.glucoseHistory.size)

        val second = DisplayHistoryAccumulator.merge(first, state(now, 120.0), now)
        val replaced = DisplayHistoryAccumulator.merge(second, state(now, 125.0), now)
        assertEquals(listOf(125.0), replaced.glucoseHistory.map { it.valueMgDl })
        assertEquals(1.25, replaced.therapyHistory.single().totalIob!!, 0.001)
    }
}
