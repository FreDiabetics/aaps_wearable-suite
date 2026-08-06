package app.aapswear.mobile

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

    @Test fun `diagnostics update while tile dashboard stays visible`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostics = context.getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
        diagnostics.edit().clear().commit()
        context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertTrue(textOf(activity.findViewById(R.id.dashboard_sync_status)).contains("Keine Watch erreichbar"))

        diagnostics.edit()
            .putLong("received", 1_000L)
            .putString("contract", "AAPS_EXTENDED_STATUS_V1")
            .putString("sourceVersion", "4.0.0-dev-b")
            .putInt("reachableWatches", 1)
            .putString("lastSyncStatus", "ok")
            .commit()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(textOf(activity.findViewById(R.id.dashboard_sync_status)).contains("Watch verbunden"))
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
        assertEquals(4, sectionPanel.childCount)
        assertNotNull(sectionPanel.findViewById<View>(R.id.dropdown_overview).background)
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

    private fun textOf(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString(" ") { textOf(view.getChildAt(it)) }
        else -> ""
    }
}
