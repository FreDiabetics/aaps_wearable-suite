package app.aapswear.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CgmSourceResolutionTest {
    private val now = 1_000_000_000L

    @Test
    fun `fresh Mobile is primary in automatic mode`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 2),
                watch = watch(minutesAgo = 1),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.MOBILE_PRIMARY, result.state)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, result.canonicalSource)
    }

    @Test
    fun `fifteen minute Mobile timeout switches to already available Watch stream`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 16),
                watch = watch(minutesAgo = 1),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
        assertEquals("mobile_missing_or_timed_out", result.reason)
    }

    @Test
    fun `timeout without a fresh Watch stream becomes no source`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 16),
                watch = watch(minutesAgo = 13),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.NO_SOURCE, result.state)
        assertEquals(CgmCanonicalSource.NONE, result.canonicalSource)
        assertEquals(null, result.reading)
    }

    @Test
    fun `newer Watch value is not overwritten by delayed older Mobile value`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 5, value = 116.0),
                watch = watch(minutesAgo = 0, value = 110.0),
                nowEpochMs = now,
                previous = CgmResolverMemory(state = CgmSourceState.WATCH_DIRECT),
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
        assertEquals(110.0, result.reading?.glucoseMgDl)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
    }

    @Test
    fun `Mobile recovery requires two distinct fresh readings`() {
        val firstMobile = mobile(minutesAgo = 1, value = 111.0)
        val watchReading = watch(minutesAgo = 1, value = 111.0)
        val first =
            CanonicalCgmSourceResolver.resolve(
                mobile = firstMobile,
                watch = watchReading,
                nowEpochMs = now,
                previous = CgmResolverMemory(state = CgmSourceState.WATCH_DIRECT),
            )

        assertEquals(CgmSourceState.MOBILE_RECOVERY, first.state)
        assertEquals(1, first.memory.recoveryReadingCount)

        val duplicate =
            CanonicalCgmSourceResolver.resolve(
                mobile = firstMobile,
                watch = watchReading,
                nowEpochMs = now,
                previous = first.memory,
            )

        assertEquals(CgmSourceState.MOBILE_RECOVERY, duplicate.state)
        assertEquals(1, duplicate.memory.recoveryReadingCount)

        val second =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(secondsAgo = 20, value = 112.0),
                watch = watch(secondsAgo = 25, value = 112.0),
                nowEpochMs = now,
                previous = duplicate.memory,
            )

        assertEquals(CgmSourceState.MOBILE_PRIMARY, second.state)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, second.canonicalSource)
    }

    @Test
    fun `Watch remains canonical when it produces a newer value during Mobile recovery`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 4, value = 108.0),
                watch = watch(minutesAgo = 0, value = 109.0),
                nowEpochMs = now,
                previous =
                    CgmResolverMemory(
                        state = CgmSourceState.MOBILE_RECOVERY,
                        recoveryReadingCount = 1,
                        lastRecoveryMobileTimestampEpochMs = now - 6 * 60_000L,
                    ),
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
    }

    @Test
    fun `same sensor timestamp is deduplicated`() {
        val measured = now - 60_000L
        val mobile =
            CgmSourceCandidate(
                source = CgmCanonicalSource.MOBILE_AAPS,
                glucoseMgDl = 104.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now - 20_000L,
                sensorId = "sensor",
                sessionId = "session",
            )
        val watch =
            CgmSourceCandidate(
                source = CgmCanonicalSource.WATCH_G7_DIRECT,
                glucoseMgDl = 104.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now - 30_000L,
                sensorId = "sensor",
                sessionId = "session",
            )

        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile,
                watch = watch,
                nowEpochMs = now,
            )

        assertTrue(result.deduplicatedSameMeasurement)
        assertEquals(CgmCanonicalSource.MOBILE_AAPS, result.canonicalSource)
    }

    @Test
    fun `different sensor sessions are never identified as the same measurement only by ids`() {
        val measured = now - 60_000L
        val mobile =
            CgmSourceCandidate(
                source = CgmCanonicalSource.MOBILE_AAPS,
                glucoseMgDl = 100.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now,
                sensorId = "sensor-a",
                sessionId = "session-a",
            )
        val watch =
            CgmSourceCandidate(
                source = CgmCanonicalSource.WATCH_G7_DIRECT,
                glucoseMgDl = 120.0,
                measuredAtEpochMs = measured,
                receivedAtEpochMs = now,
                sensorId = "sensor-b",
                sessionId = "session-b",
            )

        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile,
                watch = watch,
                nowEpochMs = now,
            )

        assertFalse(result.deduplicatedSameMeasurement)
    }

    @Test
    fun `phone can disappear during recovery without source flapping`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = null,
                watch = watch(minutesAgo = 1),
                nowEpochMs = now,
                previous =
                    CgmResolverMemory(
                        state = CgmSourceState.MOBILE_RECOVERY,
                        recoveryReadingCount = 1,
                        lastRecoveryMobileTimestampEpochMs = now - 60_000L,
                    ),
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
        assertEquals(CgmCanonicalSource.WATCH_G7_DIRECT, result.canonicalSource)
    }

    @Test
    fun `phone reachability is not an input to source selection`() {
        val result =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobile(minutesAgo = 18),
                watch = watch(minutesAgo = 2),
                nowEpochMs = now,
            )

        assertEquals(CgmSourceState.WATCH_DIRECT, result.state)
    }

    private fun mobile(
        minutesAgo: Long = 0,
        secondsAgo: Long? = null,
        value: Double = 112.0,
    ): CgmSourceCandidate =
        candidate(
            CgmCanonicalSource.MOBILE_AAPS,
            secondsAgo?.times(1_000L) ?: minutesAgo * 60_000L,
            value,
        )

    private fun watch(
        minutesAgo: Long = 0,
        secondsAgo: Long? = null,
        value: Double = 112.0,
    ): CgmSourceCandidate =
        candidate(
            CgmCanonicalSource.WATCH_G7_DIRECT,
            secondsAgo?.times(1_000L) ?: minutesAgo * 60_000L,
            value,
        )

    private fun candidate(
        source: CgmCanonicalSource,
        ageMs: Long,
        value: Double,
    ): CgmSourceCandidate =
        CgmSourceCandidate(
            source = source,
            glucoseMgDl = value,
            measuredAtEpochMs = now - ageMs,
            receivedAtEpochMs = now - ageMs + 5_000L,
        )
}
