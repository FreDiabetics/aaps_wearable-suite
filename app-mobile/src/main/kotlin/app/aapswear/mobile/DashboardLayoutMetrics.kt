package app.aapswear.mobile

/**
 * Height profile for the legacy overview while it is being migrated to Compose.
 *
 * The overview deliberately uses logical dp rather than raw display pixels. That
 * keeps the same information density when a Samsung device switches between FHD+
 * and QHD+ render resolution. The large-phone profile is sized so the complete
 * overview fits between the 64 dp top bar and 72 dp bottom navigation without
 * requiring vertical scrolling.
 */
internal data class DashboardLayoutMetrics(
    val summaryTileHeight: Int,
    val glucoseChartHeight: Int,
    val metabolicChartHeight: Int,
    val statTileHeight: Int,
) {
    companion object {
        fun forScreenHeight(screenHeightDp: Int): DashboardLayoutMetrics = when {
            screenHeightDp >= 960 -> DashboardLayoutMetrics(
                summaryTileHeight = 94,
                glucoseChartHeight = 190,
                metabolicChartHeight = 178,
                statTileHeight = 76,
            )
            screenHeightDp >= 880 -> DashboardLayoutMetrics(
                summaryTileHeight = 92,
                glucoseChartHeight = 174,
                metabolicChartHeight = 164,
                statTileHeight = 74,
            )
            screenHeightDp >= 820 -> DashboardLayoutMetrics(
                summaryTileHeight = 88,
                glucoseChartHeight = 142,
                metabolicChartHeight = 138,
                statTileHeight = 72,
            )
            screenHeightDp >= 760 -> DashboardLayoutMetrics(
                summaryTileHeight = 82,
                glucoseChartHeight = 120,
                metabolicChartHeight = 118,
                statTileHeight = 68,
            )
            else -> DashboardLayoutMetrics(
                summaryTileHeight = 78,
                glucoseChartHeight = 108,
                metabolicChartHeight = 106,
                statTileHeight = 64,
            )
        }
    }
}
