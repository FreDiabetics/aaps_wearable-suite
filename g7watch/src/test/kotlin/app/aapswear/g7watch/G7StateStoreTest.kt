package app.aapswear.g7watch

import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CollectorOwner
import app.aapswear.g7.G7PersistedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7StateStoreTest {
    @Test fun `collector state survives process recreation`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        G7SensorStateStore(context).save(G7PersistedState(collectorEnabled = true, collectorOwner = CollectorOwner.WATCH))
        assertEquals(CollectorOwner.WATCH, G7SensorStateStore(context).read().collectorOwner)
    }

    @Test fun `scanner accepts only current G7 advertising families`() {
        assertTrue(isG7AdvertisedName("DXCM12"))
        assertTrue(isG7AdvertisedName("DX01AB"))
        assertTrue(isG7AdvertisedName("DX02CD"))
        assertFalse(isG7AdvertisedName("DXCM-OLD-SENSOR-NAME-TOO-LONG"))
        assertFalse(isG7AdvertisedName("Dexcom"))
    }
}
