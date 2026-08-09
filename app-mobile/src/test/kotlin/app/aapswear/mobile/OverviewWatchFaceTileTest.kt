package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewWatchFaceTileTest {
    @Test
    fun `even a long swipe advances exactly one watchface`() {
        assertEquals(201, carouselTargetPage(200, -2_000f, 400))
        assertEquals(199, carouselTargetPage(200, 2_000f, 400))
    }

    @Test
    fun `short movement stays on current watchface and bounds are respected`() {
        assertEquals(200, carouselTargetPage(200, 12f, 400))
        assertEquals(0, carouselTargetPage(0, 500f, 400))
        assertEquals(399, carouselTargetPage(399, -500f, 400))
    }
}
