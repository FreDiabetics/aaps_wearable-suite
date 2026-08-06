package app.aapswear.wear

import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearActivityTest {
    @Test fun `tile dashboard starts safely without stored health data`() {
        val controller = Robolectric.buildActivity(WearActivity::class.java).setup()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val activity = controller.get()

        assertEquals("—", activity.findViewById<TextView>(R.id.wear_glucose).text.toString())
        assertEquals("○ Keine Daten", activity.findViewById<TextView>(R.id.wear_status).text.toString())
        assertTrue(activity.findViewById<TextView>(R.id.wear_source).text.contains("AndroidAPS"))
        assertEquals("Sugarlicious", activity.getString(R.string.app_name))
        controller.pause().stop().destroy()
    }
}
