package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewInlineHeaderLayoutTest {
    @Test
    fun `overview brand stays inside a safe left inset`() {
        assertTrue(OverviewHeaderLayout.START_PADDING_DP >= 10)
        assertEquals(0, OverviewHeaderLayout.LOGO_X_OFFSET_DP)
        assertTrue(OverviewHeaderLayout.LOGO_SLOT_WIDTH_DP >= 36)
        assertTrue(
            OverviewHeaderLayout.START_PADDING_DP +
                OverviewHeaderLayout.LOGO_X_OFFSET_DP >= 10,
        )
        assertTrue(
            OverviewHeaderLayout.START_PADDING_DP +
                OverviewHeaderLayout.LOGO_SLOT_WIDTH_DP >= 48,
        )
    }
}
