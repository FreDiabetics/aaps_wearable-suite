package app.aapswear.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchFacePushControllerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `active marketplace slot wins over an inactive matching package`() {
        val slots =
            listOf(
                ManagedWatchFaceSlot("inactive-match", "app.aapswear.watchfacepush.orbit", false),
                ManagedWatchFaceSlot("active-slot", "app.aapswear.watchfacepush.rings", true),
            )

        assertEquals(
            "active-slot",
            selectManagedWatchFaceSlot(slots, "app.aapswear.watchfacepush.orbit")?.slotId,
        )
    }

    @Test
    fun `matching package is reused when marketplace has no active face`() {
        val slots =
            listOf(
                ManagedWatchFaceSlot("fallback", "app.aapswear.watchfacepush.rings", false),
                ManagedWatchFaceSlot("match", "app.aapswear.watchfacepush.orbit", false),
            )

        assertEquals(
            "match",
            selectManagedWatchFaceSlot(slots, "app.aapswear.watchfacepush.orbit")?.slotId,
        )
    }

    @Test
    fun `legacy successful application migrates one-shot activation history`() {
        assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))

        context.getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_applied_at", 1L)
            .commit()

        assertTrue(SugarliciousWatchFacePush.directActivationWasAttempted(context))
    }
}
