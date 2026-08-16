package app.aapswear.g7watch

import app.aapswear.g7.G7Sensor
import kotlin.test.Test
import kotlin.test.assertEquals

class G7BlePolicyTest {
    @Test fun `initial pairing keeps scanning for up to thirty minutes`() {
        assertEquals(
            G7_INITIAL_PAIRING_SCAN_TIMEOUT_MS,
            g7ScanTimeoutMs(G7Sensor("new-sensor")),
        )
    }

    @Test fun `known sensor reconnect uses shorter targeted scan`() {
        assertEquals(
            G7_RECONNECT_SCAN_TIMEOUT_MS,
            g7ScanTimeoutMs(G7Sensor("known-sensor", deviceAddress = "AA:BB:CC:DD:EE:FF")),
        )
    }
}
