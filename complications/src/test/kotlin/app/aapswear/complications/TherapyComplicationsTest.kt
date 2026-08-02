package app.aapswear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.InsulinState
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TherapyComplicationsTest {
    @Test
    fun `all 27 providers create preview data in a supported primary type`() {
        assertEquals(27, AllProviders.classes.size)
        AllProviders.classes.forEach { providerClass ->
            val service = Robolectric.buildService(providerClass).create().get()
            val type = when (providerClass) {
                GlucoseImageComplication::class.java,
                GlucoseGraphComplication::class.java,
                GlucoseGraphLargeComplication::class.java -> ComplicationType.PHOTO_IMAGE
                GlucoseRangedComplication::class.java -> ComplicationType.RANGED_VALUE
                LongStatusComplication::class.java -> ComplicationType.LONG_TEXT
                else -> ComplicationType.SHORT_TEXT
            }
            val data = service.getPreviewData(type)
            assertEquals("${providerClass.simpleName} returned the wrong type", type, data.type)
        }
    }

    @Test
    fun `reservoir and battery providers honor ranged value requests`() {
        listOf(
            ReservoirComplication::class.java,
            PumpBatteryComplication::class.java,
            PhoneBatteryComplication::class.java,
        ).forEach { providerClass ->
            val service = Robolectric.buildService(providerClass).create().get()
            val data = service.getPreviewData(ComplicationType.RANGED_VALUE)
            assertEquals(ComplicationType.RANGED_VALUE, data.type)
        }
    }

    @Test
    fun `stale therapy values are replaced by dash`() = runBlocking {
        val service = Robolectric.buildService(IobComplication::class.java).create().get()
        val now = System.currentTimeMillis()
        TherapyStateStore(service).save(
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose = GlucoseState(
                    valueMgDl = 111.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FLAT,
                    measuredAtEpochMs = now - 13 * 60_000L,
                    deltaMgDl = 1.0,
                    averageDeltaMgDl = 1.0,
                ),
                insulin = InsulinState(totalIob = 3.75, bolusIob = 2.0, basalIob = 1.75),
            ),
        )

        val data = service.onComplicationRequest(
            ComplicationRequest(1, ComplicationType.SHORT_TEXT, false),
        ) as ShortTextComplicationData

        assertEquals("—", data.text.getTextAt(service.resources, Instant.now()).toString())
    }
}
