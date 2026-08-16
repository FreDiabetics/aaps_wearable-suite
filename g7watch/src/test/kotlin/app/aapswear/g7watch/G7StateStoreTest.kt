package app.aapswear.g7watch

import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CollectorOwner
import app.aapswear.g7.G7PersistedState
import org.junit.Assert.assertEquals
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
}
