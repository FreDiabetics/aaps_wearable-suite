package app.aapswear.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearGlucoseChartTest {
    @Test
    fun `prediction horizon extends graph without removing cgm history`() {
        val current = 1_786_889_891_000L
        val predictionEnd = current + 120L * 60_000L + 15_144L

        val window =
            wearChartTimeWindow(
                currentMeasurement = current,
                predictionEnd = predictionEnd,
                durationHours = 2,
                showPredictions = true,
            )

        assertEquals(current - 2L * 60L * 60_000L, window.first)
        assertEquals(predictionEnd, window.last)
        assertTrue(current in window)
    }

    @Test
    fun `disabled predictions keep current measurement at graph end`() {
        val current = 1_786_889_891_000L

        val window =
            wearChartTimeWindow(
                currentMeasurement = current,
                predictionEnd = current + 3L * 60L * 60_000L,
                durationHours = 2,
                showPredictions = false,
            )

        assertEquals(current - 2L * 60L * 60_000L, window.first)
        assertEquals(current, window.last)
    }
}
