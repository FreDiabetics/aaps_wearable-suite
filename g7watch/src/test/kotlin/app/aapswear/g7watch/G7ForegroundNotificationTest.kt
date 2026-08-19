package app.aapswear.g7watch

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7ForegroundNotificationTest {
    @Test fun `collector foreground notification is ongoing silent and not auto cancel`() {
        val service = Robolectric.buildService(G7CollectorService::class.java).get()
        val notification = service.notification("Dauerbetrieb aktiv")

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertNull(notification.sound)
    }
}
