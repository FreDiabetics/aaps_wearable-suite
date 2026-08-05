package app.aapswear.mobile

import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

    @Test
    fun `diagnostics update while activity stays visible`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val text = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            .getChildAt(0) as TextView

        assertTrue(text.text.contains("Letzter gültiger Empfang: —"))
        assertFalse(text.text.contains("AAPS_EXTENDED_STATUS_V1"))

        context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong("received", 1_000L)
            .putString("contract", "AAPS_EXTENDED_STATUS_V1")
            .putString("lastSyncStatus", "ok")
            .commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(text.text.contains("AAPS_EXTENDED_STATUS_V1"))
        assertTrue(text.text.contains("Synchronisation: übertragen"))

        controller.pause().stop().destroy()
    }
}
