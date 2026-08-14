package app.aapswear.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverviewFitScrollViewTest {

    @Test fun `fit is enabled only for visible overview with every tile and graph enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        preferences.edit()
            .clear()
            .putBoolean("showDetails", true)
            .putBoolean("showCgmGraph", true)
            .putBoolean("showMetabolicGraph", true)
            .commit()

        assertTrue(overviewFitRequested(preferences, overviewVisible = true))
        assertFalse(overviewFitRequested(preferences, overviewVisible = false))

        preferences.edit().putBoolean("showMetabolicGraph", false).commit()
        assertFalse(overviewFitRequested(preferences, overviewVisible = true))

        preferences.edit()
            .putBoolean("showMetabolicGraph", true)
            .putBoolean("showCgmGraph", false)
            .commit()
        assertFalse(overviewFitRequested(preferences, overviewVisible = true))

        preferences.edit()
            .putBoolean("showCgmGraph", true)
            .putBoolean("showDetails", false)
            .commit()
        assertFalse(overviewFitRequested(preferences, overviewVisible = true))
    }
}
