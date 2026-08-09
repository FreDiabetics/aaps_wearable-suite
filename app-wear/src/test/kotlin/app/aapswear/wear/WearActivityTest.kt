package app.aapswear.wear

import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.aapswear.protocol.WatchConfig
import app.aapswear.protocol.WatchGlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearActivityTest {
    @Test
    fun `dashboard exposes chart and bridge status`() {
        val activity =
            Robolectric
                .buildActivity(WearActivity::class.java)
                .create()
                .start()
                .resume()
                .get()

        assertEquals(
            "—",
            activity
                .findViewById<TextView>(
                    R.id.wear_glucose,
                )
                .text
                .toString(),
        )
        assertNotNull(
            activity.findViewById<WearGlucoseChart>(
                R.id.wear_glucose_chart,
            ),
        )
        assertNotNull(
            activity.findViewById<TextView>(
                R.id.wear_connection,
            ),
        )
        assertEquals(
            0,
            activity.resources.getIdentifier(
                "wear_config_info",
                "id",
                activity.packageName,
            ),
        )
    }

    @Test
    fun `watch config persists phone display preferences`() {
        val context =
            ApplicationProvider.getApplicationContext<
                android.content.Context
            >()

        WearDisplayPreferences.save(
            context,
            WatchConfig(
                graphHours = 6,
                showPredictions = false,
                glucoseUnit = WatchGlucoseUnit.MMOL_L,
                showTherapyStats = false,
                sentAtEpochMs = 1234L,
            ),
        )

        val preferences =
            WearDisplayPreferences.read(context)

        assertEquals(6, preferences.graphHours)
        assertEquals(false, preferences.showPredictions)
        assertEquals(
            WatchGlucoseUnit.MMOL_L,
            preferences.glucoseUnit,
        )
        assertEquals(false, preferences.showTherapyStats)
        assertEquals(1234L, preferences.syncedAtEpochMs)
    }
}
