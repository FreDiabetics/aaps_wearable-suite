package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import app.aapswear.protocol.WatchGraphColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class G7CollectorTilePresentationTest {
    private val now = 2_000_000L
    private val colors = WatchGraphColors(
        graphBackground = 0xFF111111.toInt(),
        rangeLow = 0xFFAA0000.toInt(),
        rangeHigh = 0xFFCCAA00.toInt(),
        cgmLow = 0xFFFF0000.toInt(),
    )

    @Test
    fun `no data and stale states are explicit`() {
        assertEquals("NO_DATA", g7TilePresentation(null, colors, now).meta)
        val stale = g7TilePresentation(reading(120.0, now - G7_SIGNAL_LOSS_AFTER_MS), colors, now)
        assertEquals("STALE", stale.meta)
        assertEquals("—", stale.value)
        val delayedBeyondDisplayLimit = g7TilePresentation(reading(120.0, now - 13 * 60_000L), colors, now)
        assertEquals("STALE", delayedBeyondDisplayLimit.meta)
        val invalid = g7TilePresentation(reading(120.0, status = CgmReadingStatus.INVALID), colors, now)
        assertEquals("NO_DATA", invalid.meta)
        val sensorError = g7TilePresentation(reading(0.0, status = CgmReadingStatus.SENSOR_ERROR), colors, now)
        assertEquals("SENSORFEHLER", sensorError.meta)
    }

    @Test
    fun `extremes use words and full tile alarm colors without changing the reading`() {
        val low = g7TilePresentation(reading(40.0), colors, now)
        assertTrue(low.value.startsWith("NIEDRIG"))
        assertEquals(colors.rangeLow, low.background)

        val high = g7TilePresentation(reading(400.0), colors, now)
        assertTrue(high.value.startsWith("HOCH"))
        assertEquals(colors.rangeHigh, high.background)
        assertEquals(0xFF181818.toInt(), high.foreground)
    }

    @Test
    fun `normal tile shows trend delta unit and age`() {
        val presentation = g7TilePresentation(
            reading(123.0, now - 2 * 60_000L, delta = 5.0, trend = Trend.FORTY_FIVE_UP),
            colors,
            now,
        )
        assertEquals("123  ↗", presentation.value)
        assertTrue(presentation.meta.contains("+5"))
        assertTrue(presentation.meta.contains("mg/dL"))
        assertEquals("vor 2 min", presentation.age)
    }

    private fun reading(
        value: Double,
        timestamp: Long = now,
        delta: Double? = null,
        trend: Trend = Trend.FLAT,
        status: CgmReadingStatus = CgmReadingStatus.VALID,
    ) = CgmReading(
        id = "reading-$value-$timestamp-$status",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = value,
        timestampEpochMs = timestamp,
        receivedAtEpochMs = timestamp,
        deltaMgDl = delta,
        trend = trend,
        status = status,
    )
}
