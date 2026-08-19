package app.aapswear.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.model.DataSourceId
import app.aapswear.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SugarliciousWatchFaceSelectionStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `sixth Sugarlicious face persists without legacy clamp`() {
        SugarliciousWatchFaceSelectionStore.write(context, SUGARLICIOUS_G6_STYLE_FACE_INDEX)
        assertEquals(SUGARLICIOUS_G6_STYLE_FACE_INDEX, SugarliciousWatchFaceSelectionStore.read(context))
    }

    @Test
    fun `g6 style becomes relevant for explicit collector source`() {
        val preferences = DashboardUiPreferences(dataSource = DataSourcePreference.DEXCOM_G7_WATCH)
        assertTrue(SugarliciousWatchFaceSelectionStore.isG6StyleRelevant(context, null, preferences))
    }

    @Test
    fun `g6 style becomes relevant for canonical watch direct state`() {
        val state = TherapyDisplayState(source = DataSourceId.DEXCOM_G7_WATCH, receivedAtEpochMs = 1L)
        assertTrue(
            SugarliciousWatchFaceSelectionStore.isG6StyleRelevant(
                context,
                state,
                DashboardUiPreferences(),
            ),
        )
    }

    @Test
    fun `g6 style is not marked relevant without setup or collector state`() {
        assertFalse(
            SugarliciousWatchFaceSelectionStore.isG6StyleRelevant(
                context,
                null,
                DashboardUiPreferences(),
            ),
        )
    }
}
