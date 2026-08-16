package app.aapswear.g7watch

import android.bluetooth.BluetoothGatt
import app.aapswear.g7.G7Sensor
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

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

    @Test fun `successful stale write callback is ignored instead of rejected`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")
        val stale = UUID.fromString("f8083534-849e-531c-c594-30f1f86a4ea5")

        assertEquals(
            G7WriteCallbackDisposition.STALE_SUCCESS,
            classifyG7WriteCallback(expected, stale, BluetoothGatt.GATT_SUCCESS),
        )
    }

    @Test fun `successful callback for expected characteristic completes write`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")

        assertEquals(
            G7WriteCallbackDisposition.EXPECTED_SUCCESS,
            classifyG7WriteCallback(expected, expected, BluetoothGatt.GATT_SUCCESS),
        )
    }

    @Test fun `failed callback for expected characteristic is rejected`() {
        val expected = UUID.fromString("f8083535-849e-531c-c594-30f1f86a4ea5")

        assertEquals(
            G7WriteCallbackDisposition.EXPECTED_FAILURE,
            classifyG7WriteCallback(expected, expected, BluetoothGatt.GATT_FAILURE),
        )
    }
}
