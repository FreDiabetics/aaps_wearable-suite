package app.aapswear.mobile

import java.util.Calendar
import java.util.TimeZone
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
    fun `only the centered carousel face is visible`() {
        assertEquals(1f, carouselPageVisibility(0f))
        assertEquals(1f, carouselPageVisibility(0.5f))
        assertEquals(0f, carouselPageVisibility(0.5001f))
        assertEquals(0f, carouselPageVisibility(1f))
    }

    @Test
    fun `wall clock helper still calculates physical clock angles`() {
        val utc = TimeZone.getTimeZone("UTC")
        val calendar = Calendar.getInstance(utc).apply {
            set(2026, Calendar.AUGUST, 14, 10, 10, 30)
            set(Calendar.MILLISECOND, 0)
        }

        val angles = watchPreviewHandAngles(calendar.timeInMillis, utc)

        assertEquals(305.25f, angles.hour, 0.001f)
        assertEquals(63f, angles.minute, 0.001f)
        assertEquals(180f, angles.second, 0.001f)
    }

    @Test
    fun `mobile watch previews use the fixed requested hand positions`() {
        assertEquals(300f, fixedWatchPreviewHandAngles.hour, 0.001f)
        assertEquals(42f, fixedWatchPreviewHandAngles.minute, 0.001f)
        assertEquals(192f, fixedWatchPreviewHandAngles.second, 0.001f)
    }

    @Test
    fun `watch menu lists the four installable Sugarlicious faces as cards`() {
        assertEquals(sugarliciousWatchFaceNames, sugarliciousWatchFaceCards.map { it.name })
        assertEquals(4, sugarliciousWatchFaceCards.size)
        assertTrue(sugarliciousWatchFaceCards.all { it.slots > 0 && "AOD" in it.features })
    }
}
