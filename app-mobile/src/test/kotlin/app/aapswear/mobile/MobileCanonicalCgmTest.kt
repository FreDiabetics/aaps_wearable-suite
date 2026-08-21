package app.aapswear.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.CgmQuality
import app.aapswear.model.DataSourceId
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileCanonicalCgmTest {
    @Test
    fun `Watch backfill survives recreation deduplicates and drives canonical recovery`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("mobile_canonical_cgm_resolver", Context.MODE_PRIVATE).edit().clear().commit()
        val now = System.currentTimeMillis()
        val prefix = "backfill-${now}-"
        val first = reading(prefix + "first", "sensor-a", "session-a", 1L, 111.0, now - 20 * 60_000L)
        val sameIdentity = first.copy(id = prefix + "same-sequence", glucoseMgDl = 112.0)
        val outOfOrder = reading(prefix + "out-of-order", "sensor-a", "session-a", 3L, 115.0, now - 15 * 60_000L)
        val second = reading(prefix + "second", "sensor-a", "session-a", 2L, 114.0, now - 10 * 60_000L)
        val otherSession = reading(prefix + "other-session", "sensor-a", "session-b", 1L, 119.0, now - 5 * 60_000L)
        val latest = reading(prefix + "latest", "sensor-b", "session-c", 7L, 121.0, now - 60_000L)
        val invalidStatus = reading(prefix + "invalid", "sensor-a", "session-a", 4L, 118.0, now).copy(status = CgmReadingStatus.INVALID)
        val wrongSource = reading(prefix + "phone", "sensor-a", "session-a", 5L, 120.0, now).copy(source = DataSourceId.ANDROID_APS)
        val future = reading(prefix + "future", "sensor-a", "session-a", 6L, 120.0, now + 10 * 60_000L)

        val accepted = MobileG7BackfillStore(context).merge(
            listOf(first, first, sameIdentity, second, outOfOrder, otherSession, latest, invalidStatus, wrongSource, future),
            now,
        )

        assertTrue(first.id in accepted)
        assertTrue(sameIdentity.id in accepted)
        assertFalse(invalidStatus.id in accepted)
        assertFalse(wrongSource.id in accepted)
        assertFalse(future.id in accepted)

        val restored = MobileG7BackfillStore(context).snapshot().filter { it.id.startsWith(prefix) }
        assertEquals(5, restored.size)
        assertEquals(restored.sortedBy(CgmReading::timestampEpochMs), restored)
        assertEquals(1, restored.count { it.sensorId == "sensor-a" && it.sessionId == "session-a" && it.sequenceNumber == 1L })
        assertEquals(2, restored.count { it.sequenceNumber == 1L })

        val stalePhone = phoneState(
            now = now,
            measuredAt = now - 16 * 60_000L,
            history = listOf(first.toPhoneSample()),
        )
        val watchDirect = MobileCanonicalCgmResolver.resolve(context, stalePhone, now)!!
        assertEquals(DataSourceId.DEXCOM_G7_WATCH, watchDirect.source)
        assertEquals("sensor-b", watchDirect.glucose?.sensorId)
        assertEquals("session-c", watchDirect.glucose?.sessionId)
        assertEquals(7L, watchDirect.glucose?.sequenceNumber)
        assertEquals(1, watchDirect.glucoseHistory.count { it.sensorId == first.sensorId && it.sessionId == first.sessionId && it.sequenceNumber == first.sequenceNumber })
        assertEquals(DataSourceId.ANDROID_APS, watchDirect.glucoseHistory.single { it.sensorId == first.sensorId && it.sessionId == first.sessionId && it.sequenceNumber == first.sequenceNumber }.source)

        val firstRecovery = MobileCanonicalCgmResolver.resolve(
            context,
            phoneState(now, now - 30_000L),
            now,
        )!!
        assertEquals(DataSourceId.DEXCOM_G7_WATCH, firstRecovery.source)
        val recovered = MobileCanonicalCgmResolver.resolve(
            context,
            phoneState(now, now - 10_000L),
            now,
        )!!
        assertEquals(DataSourceId.ANDROID_APS, recovered.source)

        val invalidPhone =
            phoneState(now, now).copy(
                glucose = phoneState(now, now).glucose?.copy(quality = CgmQuality.SENSOR_ERROR),
            )
        val invalidPhoneResult = MobileCanonicalCgmResolver.resolve(context, invalidPhone, now)!!
        assertEquals(DataSourceId.DEXCOM_G7_WATCH, invalidPhoneResult.source)
    }

    private fun phoneState(
        now: Long,
        measuredAt: Long,
        history: List<GlucoseSample> = emptyList(),
    ) = TherapyDisplayState(
        source = DataSourceId.ANDROID_APS,
        receivedAtEpochMs = now,
        glucose = GlucoseState(
            valueMgDl = 123.0,
            displayUnit = GlucoseUnit.MG_DL,
            measuredAtEpochMs = measuredAt,
            source = DataSourceId.ANDROID_APS,
            receivedAtEpochMs = now,
        ),
        glucoseHistory = history,
    )

    private fun reading(
        id: String,
        sensor: String,
        session: String,
        sequence: Long,
        value: Double,
        timestamp: Long,
    ) = CgmReading(
        id = id,
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = sensor,
        sessionId = session,
        glucoseMgDl = value,
        timestampEpochMs = timestamp,
        receivedAtEpochMs = timestamp + 1_000L,
        status = CgmReadingStatus.VALID,
        sequenceNumber = sequence,
    )

    private fun CgmReading.toPhoneSample() = GlucoseSample(
        valueMgDl = glucoseMgDl,
        measuredAtEpochMs = timestampEpochMs,
        source = DataSourceId.ANDROID_APS,
        sensorId = sensorId,
        sessionId = sessionId,
        sequenceNumber = sequenceNumber,
        receivedAtEpochMs = receivedAtEpochMs + 1L,
    )
}
