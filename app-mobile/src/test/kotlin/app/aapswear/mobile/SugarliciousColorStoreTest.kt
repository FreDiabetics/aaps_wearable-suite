package app.aapswear.mobile

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SugarliciousColorStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every configurable color role round trips exact ARGB in dark and light mode`() {
        val preferences = context.getSharedPreferences("color_picker_roundtrip", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        listOf("DARK", "LIGHT").forEachIndexed { modeIndex, mode ->
            preferences.edit().putString("themeMode", mode).commit()
            SugarliciousColorRole.entries.filter { it.configurable }.forEachIndexed { index, role ->
                val argb = Color.argb(
                    40 + (index * 13 + modeIndex * 7) % 216,
                    20 + (index * 31) % 220,
                    25 + (index * 47) % 215,
                    30 + (index * 61) % 210,
                )
                SugarliciousColorStore.save(preferences, role, argb)
                assertEquals("$mode ${role.name}", argb, SugarliciousColorStore.load(preferences).argb(role))
            }
        }
    }

    @Test
    fun `legacy target band picker is no longer exposed`() {
        assertFalse(SugarliciousColorRole.TARGET_BAND.configurable)
    }
}
