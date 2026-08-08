package app.aapswear.mobile

import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

    @Test fun `app surfaces are neutral gray and system accent follows the icon`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(R.color.app_background, R.color.app_surface, R.color.app_surface_high, R.color.app_surface_raised, R.color.app_surface_selected).forEach { colorId ->
            val color = context.getColor(colorId)
            assertEquals(Color.red(color), Color.green(color))
            assertEquals(Color.green(color), Color.blue(color))
        }
        assertEquals(Color.rgb(109, 232, 146), context.getColor(R.color.app_accent))
    }

    @Test fun `diagnostics update while overview dashboard stays visible`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostics = context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
        diagnostics.edit().clear().commit()
        context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val dashboard = activity.findViewById<ViewGroup>(R.id.dashboard_content)

        assertNotNull(dashboard)
        assertTrue(dashboard.childCount > 0)

        diagnostics.edit()
            .putLong("received", 1_000L)
            .putString("contract", "AAPS_EXTENDED_STATUS_V1")
            .putString("sourceVersion", "4.0.0-dev-b")
            .putInt("reachableWatches", 1)
            .putString("lastSyncStatus", "ok")
            .commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(dashboard.childCount > 0)

        activity.findViewById<View>(R.id.nav_settings).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val settingsText = textOf(activity.findViewById(R.id.dashboard_content))
        assertTrue(settingsText.contains("AAPS_EXTENDED_STATUS_V1"))
        assertTrue(settingsText.contains("4.0.0-dev-b"))
        controller.pause().stop().destroy()
    }

    @Test fun `inline settings persist without submenu`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.nav_settings).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val details = activity.findViewById<android.widget.Switch>(R.id.dashboard_details_switch)
        assertTrue(details.isChecked)
        details.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(preferences.getBoolean("showDetails", true))

        val live = activity.findViewById<android.widget.Switch>(R.id.dashboard_live_notification_switch)
        assertFalse(live.isChecked)
        live.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(preferences.getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false))

        controller.pause().stop().destroy()
    }

    @Test fun `Sugarlicious about tile exposes project and contact links`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.nav_settings).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val settingsText = textOf(activity.findViewById(R.id.dashboard_content))
        assertTrue(settingsText.contains("Sugarlicious"))
        assertTrue(settingsText.contains("typ1.diafreddy@gmail.com"))
        assertTrue(settingsText.contains("FreDiabetics/aaps_wearable-suite"))

        activity.findViewById<View>(R.id.dashboard_github).performClick()
        assertEquals(
            "https://github.com/FreDiabetics/aaps_wearable-suite",
            shadowOf(activity).nextStartedActivity.dataString,
        )
        activity.findViewById<View>(R.id.dashboard_contact_email).performClick()
        val emailIntent = shadowOf(activity).nextStartedActivity
        assertEquals("mailto", emailIntent.data?.scheme)
        assertEquals("typ1.diafreddy@gmail.com", emailIntent.data?.schemeSpecificPart)
        controller.pause().stop().destroy()
    }

    @Test fun `header menus use pill dropdowns and keep their actions inline`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.menu_button).performClick()
        val sectionPopup = activity.activeDropdown
        assertNotNull(sectionPopup)
        val sectionPanel = sectionPopup!!.contentView as ViewGroup
        assertEquals(R.id.dropdown_panel, sectionPanel.id)
        assertEquals(3, sectionPanel.childCount)
        assertNotNull(sectionPanel.findViewById<View>(R.id.dropdown_overview).background)
        assertEquals(activity.getColor(R.color.app_accent), sectionPanel.findViewById<TextView>(R.id.dropdown_overview).currentTextColor)
        sectionPanel.findViewById<View>(R.id.dropdown_settings).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(activity.activeDropdown)
        assertTrue(textOf(activity.findViewById(R.id.dashboard_content)).contains("Einstellungen"))

        activity.findViewById<View>(R.id.more_button).performClick()
        val morePanel = activity.activeDropdown!!.contentView as ViewGroup
        assertEquals(3, morePanel.childCount)
        assertNotNull(morePanel.findViewById<View>(R.id.dropdown_app_info).background)
        activity.activeDropdown?.dismiss()

        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `live notification switch opens the Android 16 promotion permission when needed`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.findViewById<View>(R.id.nav_settings).performClick()
        activity.findViewById<View>(R.id.dashboard_live_notification_switch).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false))
        val settingsIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, settingsIntent.action)
        assertEquals(activity.packageName, settingsIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        controller.pause().stop().destroy()
    }

    private fun textOf(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString(" ") { textOf(view.getChildAt(it)) }
        else -> ""
    }
}
