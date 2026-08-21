package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CollectorDiagnosticResult
import app.aapswear.g7.CollectorDiagnosticStage
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.g7.G7Sensor
import app.aapswear.model.DataSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7CollectorDiagnosticsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("g7_collector_attempts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `attempt ids survive recreation and only the latest fifty attempts remain`() {
        repeat(55) { index ->
            val store = G7CollectorDiagnosticStore(context)
            val attempt = store.begin(manual = index % 2 == 0, restart = index % 3 == 0, nowEpochMs = index.toLong())
            store.record(
                attempt.attemptId,
                CollectorDiagnosticStage.COMPLETE,
                CollectorDiagnosticResult.SUCCESS,
                "Erfolgreich",
                nowEpochMs = index.toLong() + 1L,
            )
        }

        val restored = G7CollectorDiagnosticStore(context).snapshot()
        assertEquals(50, restored.size)
        assertEquals(55L, restored.first().attemptId)
        assertEquals(6L, restored.last().attemptId)
        assertTrue(restored.all { it.completedAtEpochMs != null })
    }

    @Test
    fun `event history is bounded and secrets and Bluetooth addresses are redacted`() {
        val store = G7CollectorDiagnosticStore(context)
        val attempt = store.begin(manual = true, restart = false, nowEpochMs = 0L)
        repeat(105) { index ->
            store.record(
                attempt.attemptId,
                CollectorDiagnosticStage.SCANNING,
                message = "event=$index sharedKey=DEADBEEF pairingCode=1234 address=AA:BB:CC:DD:EE:FF\n",
                nowEpochMs = index.toLong() + 1L,
            )
        }

        val events = store.snapshot().single().events
        assertEquals(100, events.size)
        val text = events.joinToString(" ", transform = { it.message })
        assertFalse(text.contains("DEADBEEF"))
        assertFalse(text.contains("1234"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(text.contains("[REDACTED]"))
        assertTrue(text.contains("••:••:••:••:EE:FF"))
    }

    @Test
    fun `reading summary counts missing five minute windows in chronological order`() {
        val readings = listOf(reading(20L), reading(0L), reading(5L))
        val summary = summarizeG7Readings(readings, startOfDayEpochMs = 1L)

        assertEquals(3, summary.count)
        assertEquals(2, summary.todayCount)
        assertEquals(0L, summary.oldestEpochMs)
        assertEquals(20L * 60_000L, summary.latestEpochMs)
        assertEquals(2, summary.missedExpectedWindows)
    }

    @Test
    fun `reading summary does not count a sensor session change as a gap`() {
        val readings =
            listOf(
                reading(0L),
                reading(30L).copy(sensorId = "next-sensor", sessionId = "next-session"),
            )

        assertEquals(0, summarizeG7Readings(readings, startOfDayEpochMs = 0L).missedExpectedWindows)
    }

    @Test
    fun `reading summary reports only plausible valid glucose successes`() {
        val readings =
            listOf(
                reading(0L),
                reading(5L).copy(status = CgmReadingStatus.SENSOR_ERROR, glucoseMgDl = 0.0),
                reading(10L).copy(glucoseMgDl = Double.NaN),
                reading(15L).copy(glucoseMgDl = 1_001.0),
            )

        val summary = summarizeG7Readings(readings, startOfDayEpochMs = 0L)

        assertEquals(1, summary.count)
        assertEquals(0L, summary.latestEpochMs)
        assertEquals(0, summary.missedExpectedWindows)
    }

    @Test
    fun `collector graph never mixes readings from another sensor session`() {
        val active = reading(5L)
        val sameSensorOtherSession = reading(10L).copy(sessionId = "other-session")
        val otherSensor = reading(15L).copy(sensorId = "other-sensor")

        val filtered =
            currentG7SessionReadings(
                listOf(active, sameSensorOtherSession, otherSensor),
                G7Sensor("sensor", "session"),
            )

        assertEquals(listOf(active), filtered)
    }

    private fun reading(minutes: Long) = CgmReading(
        id = "reading-$minutes",
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = "sensor",
        sessionId = "session",
        glucoseMgDl = 120.0,
        timestampEpochMs = minutes * 60_000L,
        receivedAtEpochMs = minutes * 60_000L,
        sequenceNumber = minutes,
    )
}
