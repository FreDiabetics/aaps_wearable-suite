package app.aapswear.g7

import kotlin.test.Test
import kotlin.test.assertEquals

class G7ReconnectSchedulerTest {
    @Test fun `GATT 133 before first reading retries shortly without exponential backoff`() {
        val now = 1_000_000L
        val plan = G7ReconnectScheduler.afterGatt133(now, null)

        assertEquals(now + 15_000L, plan.nextReconnectEpochMs)
        assertEquals(15_000L, plan.delayMs)
        assertEquals(0, plan.retryCount)
    }

    @Test fun `GATT 133 after a reading advances to the next five minute connection window`() {
        val reading = 1_000_000L
        val now = 1_280_000L
        val plan = G7ReconnectScheduler.afterGatt133(now, reading)

        assertEquals(1_570_000L, plan.nextReconnectEpochMs)
        assertEquals(290_000L, plan.delayMs)
        assertEquals(0, plan.retryCount)
    }

    @Test fun `GATT 133 never inherits generic retry count`() {
        val plan = G7ReconnectScheduler.afterGatt133(10_000_000L, 9_000_000L)

        assertEquals(0, plan.retryCount)
        assert(plan.delayMs <= G7ReconnectScheduler.EXPECTED_READING_INTERVAL_MS)
    }
}
