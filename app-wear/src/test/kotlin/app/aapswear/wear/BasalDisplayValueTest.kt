package app.aapswear.wear

import app.aapswear.model.BasalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasalDisplayValueTest {
    @Test
    fun `temp basal does not override the canonical basal display value`() {
        val basal =
            BasalState(
                currentUnitsPerHour = 0.39,
                tempAbsoluteUnitsPerHour = 0.0,
                tempPercent = 0,
            )

        assertEquals(0.39, basalDisplayUnitsPerHour(basal)!!, 0.0001)
    }

    @Test
    fun `missing basal remains unavailable`() {
        assertNull(basalDisplayUnitsPerHour(null))
    }
}
