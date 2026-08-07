package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NightscoutClientTest {
    @Test
    fun `parses and sorts valid entries`() {
        val now = 1_800_000L
        val json = """
            [
              {"date":1500000,"sgv":120,"direction":"Flat"},
              {"date":1200000,"sgv":110,"direction":"FortyFiveUp"},
              {"date":1300000,"sgv":5,"direction":"Flat"}
            ]
        """.trimIndent()

        val entries = NightscoutClient().parseEntries(json, now)

        assertEquals(2, entries.size)
        assertEquals(1_200_000L, entries.first().sample.measuredAtEpochMs)
        assertEquals(110.0, entries.first().sample.valueMgDl, 0.0)
        assertEquals("FortyFiveUp", entries.first().direction)
    }
}
