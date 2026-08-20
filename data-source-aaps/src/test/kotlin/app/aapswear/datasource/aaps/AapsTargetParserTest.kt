package app.aapswear.datasource.aaps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AapsTargetParserTest {
    @Test
    fun `parses effective targetBG from APS payload`() {
        assertEquals(
            100.0,
            AapsTargetParser.parse("{\"targetBG\":100,\"predBGs\":{\"IOB\":[120,115]}}"),
            0.0,
        )
    }

    @Test
    fun `parses temp target value without inventing expiry`() {
        assertEquals(
            140.0,
            AapsTargetParser.parse("{\"targetBG\":140,\"reason\":\"active temp target\"}"),
            0.0,
        )
    }

    @Test
    fun `rejects missing malformed or implausible targets`() {
        assertNull(AapsTargetParser.parse(null))
        assertNull(AapsTargetParser.parse("not-json"))
        assertNull(AapsTargetParser.parse("{\"targetBG\":5}"))
    }
}
