package app.aapswear.mobile

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `live preference requests promoted status with current glucose delta and graph`() {
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
            glucose = GlucoseState(
                valueMgDl = 123.0,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.FLAT,
                measuredAtEpochMs = now,
                deltaMgDl = 5.0,
            ),
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

        assertEquals("123", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.extras.getBoolean(PersistentBridgeService.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertTrue(content.contains("+5"))
        assertFalse(content.contains("mg/dL"))
        assertNull(notification.getLargeIcon())
        assertNotNull(notification.contentView)
        assertNotNull(notification.bigContentView)
        assertNull(notification.extras.getParcelable(Notification.EXTRA_PICTURE))

        val graphPreferences =
            context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        val highEdgeColor = Color.rgb(198, 36, 91)
        val targetBandColor = Color.rgb(0, 184, 126)
        val lowEdgeColor = Color.rgb(24, 156, 214)
        val graphColors = graphPreferences.edit()
        listOf("dark", "light").forEach { mode ->
            graphColors.putInt(
                "notification.color.$mode.${SugarliciousColorRole.RANGE_HIGH.preferenceKey}",
                highEdgeColor,
            )
            graphColors.putInt(
                "notification.color.$mode.${SugarliciousColorRole.TARGET_BAND.preferenceKey}",
                targetBandColor,
            )
            graphColors.putInt(
                "notification.color.$mode.${SugarliciousColorRole.RANGE_LOW.preferenceKey}",
                lowEdgeColor,
            )
        }
        graphColors.commit()

        val collapsedGraph = NotificationGraphRenderer.renderCollapsed(
            context,
            therapyState,
            graphPreferences,
        )
        val expandedGraph = NotificationGraphRenderer.renderExpanded(
            context,
            therapyState,
            graphPreferences,
        )

        assertEquals(NotificationGraphRenderer.COLLAPSED_WIDTH, collapsedGraph.width)
        assertEquals(NotificationGraphRenderer.COLLAPSED_HEIGHT, collapsedGraph.height)
        assertEquals(NotificationGraphRenderer.EXPANDED_WIDTH, expandedGraph.width)
        assertEquals(NotificationGraphRenderer.EXPANDED_HEIGHT, expandedGraph.height)
        listOf(collapsedGraph, expandedGraph).forEach { graph ->
            assertEquals(0, Color.alpha(graph.getPixel(0, 0)))
            assertEquals(0, Color.alpha(graph.getPixel(graph.width - 1, 0)))
            assertEquals(0, Color.alpha(graph.getPixel(0, graph.height - 1)))
            assertEquals(0, Color.alpha(graph.getPixel(graph.width - 1, graph.height - 1)))
            assertEquals(highEdgeColor, graph.getPixel(graph.width / 2, 0))
            assertEquals(lowEdgeColor, graph.getPixel(graph.width / 2, graph.height - 1))
        }
        val targetTop = (expandedGraph.height * 0.1875f).toInt() + 2
        assertEquals(targetBandColor, expandedGraph.getPixel(0, targetTop))
        assertEquals(targetBandColor, expandedGraph.getPixel(expandedGraph.width - 1, targetTop))
        assertEquals(null, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        assertEquals(1, notification.actions.size)
        controller.destroy()
    }

    @Test
    @Config(sdk = [35])
    fun `notification graph accepts only one two or three hours`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)

        preferences.edit().clear().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 1).commit()
        assertEquals(1, NotificationGraphRenderer.notificationGraphHours(preferences))

        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 2).commit()
        assertEquals(2, NotificationGraphRenderer.notificationGraphHours(preferences))

        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 3).commit()
        assertEquals(3, NotificationGraphRenderer.notificationGraphHours(preferences))

        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 6).commit()
        assertEquals(3, NotificationGraphRenderer.notificationGraphHours(preferences))
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
