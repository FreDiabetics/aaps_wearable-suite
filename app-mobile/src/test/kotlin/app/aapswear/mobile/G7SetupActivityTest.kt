package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class G7SetupActivityTest {
    @Test fun `applicator data matrix extracts code serial and gtin`() {
        val parsed = G7ApplicatorBarcodeParser.parse(
            "]d20100386270000000\u001d2409876\u001d21SERIAL-1",
        )
        assertEquals("9876", parsed?.pairingCode)
        assertEquals("SERIAL-1", parsed?.sensorSerial)
        assertEquals("00386270000000", parsed?.gtin)
    }

    @Test fun `non G7 and incomplete barcodes are rejected`() {
        assertNull(G7ApplicatorBarcodeParser.parse("0101234567890123\u001d2401234"))
        assertNull(G7ApplicatorBarcodeParser.parse("24012"))
    }
}
