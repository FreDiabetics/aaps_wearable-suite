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
    fun `unknown legacy marketplace package still supplies the reusable slot`() {
        val slots =
            listOf(
                ManagedWatchFaceSlot("legacy-slot", "app.aapswear.watchfacepush.old", false),
            )

        assertEquals(
            "legacy-slot",
            selectManagedWatchFaceSlot(slots, "app.aapswear.watchfacepush.analog")?.slotId,
        )
    }

    @Test
    fun `successful updates do not imply that the one-shot activation was consumed`() {
        assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))

        context.getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_applied_at", 1L)
            .commit()

        assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))
    }

    @Test
    fun `default watchface is bundled for the system picker`() {
        assertTrue(context.getString(R.string.default_wf_token).isNotBlank())

        context.assets.open("default_watchface.apk").use { apk ->
            assertEquals('P'.code, apk.read())
            assertEquals('K'.code, apk.read())
        }
    }
}
