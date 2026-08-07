package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutMetricsTest {
    @Test
    fun `large phone profile keeps overview dense enough for one screen`() {
        val metrics = DashboardLayoutMetrics.forScreenHeight(960)

        // Approximate fixed chrome/overhead used by the current View overview:
        // top summary + graph card labels/padding + details + connection card.
        val estimatedOverviewContentDp =
            metrics.summaryTileHeight +
                (metrics.glucoseChartHeight + 62) +
                (metrics.metabolicChartHeight + 38) +
                (metrics.statTileHeight + 6) +
                70 +
                20

        // 960 dp window - 64 dp top bar - 78 dp bottom navigation/margin.
        assertTrue(estimatedOverviewContentDp <= 818)
        assertEquals(94, metrics.summaryTileHeight)
    }

    @Test
    fun `shorter displays receive progressively smaller graph budgets`() {
        val tall = DashboardLayoutMetrics.forScreenHeight(960)
        val compact = DashboardLayoutMetrics.forScreenHeight(820)
        val short = DashboardLayoutMetrics.forScreenHeight(740)

        assertTrue(tall.glucoseChartHeight > compact.glucoseChartHeight)
        assertTrue(compact.glucoseChartHeight > short.glucoseChartHeight)
        assertTrue(tall.metabolicChartHeight > compact.metabolicChartHeight)
        assertTrue(compact.metabolicChartHeight > short.metabolicChartHeight)
    }
}
