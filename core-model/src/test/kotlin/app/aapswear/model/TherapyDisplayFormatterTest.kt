package app.aapswear.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TherapyDisplayFormatterTest {
    @Test
    fun `formats mgdl and mmol without locale-dependent separators`() {
        assertEquals("123", TherapyDisplayFormatter.glucose(glucose(123.4, GlucoseUnit.MG_DL)))
        assertEquals("6.9", TherapyDisplayFormatter.glucose(glucose(124.2, GlucoseUnit.MMOL_L)))
        assertEquals("+5", TherapyDisplayFormatter.signedDelta(5.0, GlucoseUnit.MG_DL))
        assertEquals("-0.3", TherapyDisplayFormatter.signedDelta(-5.4, GlucoseUnit.MMOL_L))
    }

    @Test
    fun `maps all trends and suppresses unknown trend`() {
        val expected = listOf("⇊", "↓", "↘", "→", "↗", "↑", "⇈", "")
        assertEquals(expected, Trend.entries.map(TherapyDisplayFormatter::trendArrow))
    }

    @Test
    fun `formats missing values and future timestamps safely`() {
        assertEquals("—", TherapyDisplayFormatter.units(null, "U", 2))
        assertEquals("—", TherapyDisplayFormatter.percent(null))
        assertEquals("0m", TherapyDisplayFormatter.ageMinutes(2_000L, 1_000L))
        assertEquals("—", TherapyDisplayFormatter.target(null, GlucoseUnit.MG_DL))
    }

    @Test
    fun `formats target bounds in selected unit`() {
        val target = TargetState(lowMgDl = 72.0, highMgDl = 180.0)
        assertEquals("72–180", TherapyDisplayFormatter.target(target, GlucoseUnit.MG_DL))
        assertEquals("4.0–10.0", TherapyDisplayFormatter.target(target, GlucoseUnit.MMOL_L))
    }

    private fun glucose(valueMgDl: Double, unit: GlucoseUnit) = GlucoseState(
        valueMgDl = valueMgDl,
        displayUnit = unit,
        trend = Trend.FLAT,
        measuredAtEpochMs = 1L,
        deltaMgDl = null,
        averageDeltaMgDl = null,
    )
}

