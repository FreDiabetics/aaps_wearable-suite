package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `only the immediate carousel neighbours remain visible when settled`() {
        assertEquals(1f, carouselPageVisibility(0f))
        assertEquals(1f, carouselPageVisibility(1f))
        assertEquals(0f, carouselPageVisibility(2f))
        assertEquals(0.5f, carouselPageVisibility(1.125f))
    }

    @Test
    fun `watch menu lists the four installable Sugarlicious faces as cards`() {
        assertEquals(sugarliciousWatchFaceNames, sugarliciousWatchFaceCards.map { it.name })
        assertEquals(4, sugarliciousWatchFaceCards.size)
        assertTrue(sugarliciousWatchFaceCards.all { it.slots > 0 && "AOD" in it.features })
    }
}
