package app.aapswear.mobile

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PersistentBridgeServiceTest {

    @Test
    @Config(sdk = [35])
    fun `normal notification is ongoing private and sticky by default`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID)

        assertNotNull(notification)
        assertEquals("Sugarlicious ist aktiv", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.getBoolean(PersistentBridgeService.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(PersistentBridgeService.CHANNEL_ID).importance)
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 2))
        controller.destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `live preference requests promoted ongoing status without therapy values`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
            .commit()
        context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE).edit()
            .putString("sourceVersion", "4.0.0-dev")
            .putInt("reachableWatches", 1)
            .commit()

        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        val manager = controller.get().getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID)
        val content = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("Sugarlicious Live-Status", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.extras.getBoolean(PersistentBridgeService.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(content.contains("AndroidAPS erkannt"))
        assertTrue(content.contains("Watch verbunden"))
        assertFalse(content.contains("mg/dL"))
        assertEquals(1, notification.actions.size)
        controller.destroy()
    }

    @Test
    @Config(sdk = [35])
    fun `boot receiver requests persistent service restart`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowContext = shadowOf(context)
        shadowContext.clearStartedServices()

        PersistentBridgeBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val started = shadowContext.nextStartedService
        assertEquals(PersistentBridgeService::class.java.name, started.component?.className)
    }
}
