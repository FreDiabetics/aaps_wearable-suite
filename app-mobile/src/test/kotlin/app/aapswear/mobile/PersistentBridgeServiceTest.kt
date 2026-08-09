package app.aapswear.mobile

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        assertEquals("—", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.getBoolean(PersistentBridgeService.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(PersistentBridgeService.CHANNEL_ID).importance)
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 2))
        controller.destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `live preference requests promoted status with current glucose and graph`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE).edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
            .commit()
        context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE).edit()
            .putString("sourceVersion", "4.0.0-dev")
            .putInt("reachableWatches", 1)
            .commit()
        val now = System.currentTimeMillis()
        val therapyState = TherapyDisplayState(
            receivedAtEpochMs = now,
            glucose = GlucoseState(123.0, GlucoseUnit.MG_DL, Trend.FLAT, now),
            glucoseHistory = listOf(
                app.aapswear.model.GlucoseSample(115.0, now - 10 * 60_000L),
                app.aapswear.model.GlucoseSample(120.0, now - 5 * 60_000L),
            ),
        )
        runBlocking { TherapyStateStore(context).save(therapyState) }

        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val manager = controller.get().getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID)
        val content = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals("123 →", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.extras.getBoolean(PersistentBridgeService.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(content.contains("mg/dL"))
        assertNotNull(notification.getLargeIcon())
        val picture = notification.extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        assertNotNull(picture)
        val rendered = requireNotNull(picture)
        assertTrue(rendered.width > 0)
        assertTrue("height=${rendered.height}", rendered.height >= 280)
        assertEquals(
            NotificationGraphRenderer.HEIGHT.toDouble() / NotificationGraphRenderer.WIDTH,
            rendered.height.toDouble() / rendered.width,
            0.02,
        )
        val sourceGraph = NotificationGraphRenderer.render(
            context,
            therapyState,
            context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE),
        )
        assertEquals(0, Color.alpha(sourceGraph.getPixel(0, 0)))
        assertTrue(Color.alpha(sourceGraph.getPixel(sourceGraph.width / 2, sourceGraph.height / 2)) > 0)
        assertEquals(null, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
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
