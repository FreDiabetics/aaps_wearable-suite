package app.aapswear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
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
import org.junit.Assert.assertNotNull
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
                GlucoseGraphLargeComplication::class.java ->
                    ComplicationType.PHOTO_IMAGE

                GlucoseRangedComplication::class.java ->
                    ComplicationType.RANGED_VALUE

                LongStatusComplication::class.java ->
                    ComplicationType.LONG_TEXT

                else ->
                    ComplicationType.SHORT_TEXT
            }

            val data = service.getPreviewData(type)
            assertNotNull(data)
            assertEquals(
                "${providerClass.simpleName} returned the wrong type",
                type,
                data.type,
            )
        }
    }

    @Test
    fun `glucose preview providers expose semantic text`() {
        val deltaService =
            Robolectric.buildService(GlucoseDeltaComplication::class.java)
                .create()
                .get()

        val delta =
            deltaService.getPreviewData(
                ComplicationType.SHORT_TEXT,
            ) as ShortTextComplicationData

        assertEquals(
            "+5",
            delta.text
                .getTextAt(deltaService.resources, Instant.now())
                .toString(),
        )

        val ageService =
            Robolectric.buildService(GlucoseAgeComplication::class.java)
                .create()
                .get()

        val age =
            ageService.getPreviewData(
                ComplicationType.SHORT_TEXT,
            ) as ShortTextComplicationData

        assertEquals(
            "0m",
            age.text
                .getTextAt(ageService.resources, Instant.now())
                .toString(),
        )
    }

    @Test
    fun `glucose providers expose ranged value with glucose and trend`() {
        listOf(
            GlucoseComplication::class.java,
            GlucoseTrendComplication::class.java,
            GlucoseRangedComplication::class.java,
        ).forEach { providerClass ->
            val service = Robolectric.buildService(providerClass).create().get()
            val data =
                service.getPreviewData(
                    ComplicationType.RANGED_VALUE,
                ) as RangedValueComplicationData

            assertEquals(ComplicationType.RANGED_VALUE, data.type)
            assertEquals(40f, data.min)
            assertEquals(260f, data.max)
            assertEquals(123f, data.value)
            assertEquals(
                "123",
                data.text!!
                    .getTextAt(service.resources, Instant.now())
                    .toString(),
            )
            assertEquals(
                "↗",
                data.title!!
                    .getTextAt(service.resources, Instant.now())
                    .toString(),
            )
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
        val service =
            Robolectric.buildService(IobComplication::class.java)
                .create()
                .get()

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
                insulin = InsulinState(
                    totalIob = 3.75,
                    bolusIob = 2.0,
                    basalIob = 1.75,
                ),
            ),
        )

        val data = service.onComplicationRequest(
            ComplicationRequest(
                1,
                ComplicationType.SHORT_TEXT,
                false,
            ),
        ) as ShortTextComplicationData

        assertEquals(
            "—",
            data.text
                .getTextAt(service.resources, Instant.now())
                .toString(),
        )
    }
}
