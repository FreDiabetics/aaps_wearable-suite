package app.aapswear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `all documented providers remain active`() {
        assertEquals(19, AllProviders.classes.distinct().size)
        assertEquals(GlucoseComplication::class.java, AllProviders.classes.first())
        assertEquals(GlucoseGraphComplication::class.java, AllProviders.classes.last())
    }

    @Test
    fun `glucose plus delta exposes both values`() {
        val service = Robolectric.buildService(GlucosePlusDeltaComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("123 +5", data.text.getTextAt(service.resources, Instant.now()).toString())
    }

    @Test
    fun `IOB COB basal keeps basal in the title`() {
        val service = Robolectric.buildService(IobCobBasalComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("1.2U · 15g", data.text.getTextAt(service.resources, Instant.now()).toString())
        assertEquals("Basal 0.80U/h", data.title!!.getTextAt(service.resources, Instant.now()).toString())
    }

    @Test
    fun `glucose trend also supplies short text`() {
        val service = Robolectric.buildService(GlucoseTrendComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("123↗", data.text.getTextAt(service.resources, Instant.now()).toString())
    }

    @Test
    fun `glucose trend ranged complication separates value and trend for renderers`() {
        val service =
            Robolectric
                .buildService(GlucoseTrendComplication::class.java)
                .create()
                .get()

        val data =
            service.getPreviewData(
                ComplicationType.RANGED_VALUE,
            ) as RangedValueComplicationData

        assertEquals(40f, data.min)
        assertEquals(260f, data.max)
        assertEquals(123f, data.value)
        assertEquals(
            "123",
            data.text!!
                .getTextAt(
                    service.resources,
                    Instant.now(),
                )
                .toString(),
        )
        assertEquals(
            "↗",
            data.title!!
                .getTextAt(
                    service.resources,
                    Instant.now(),
                )
                .toString(),
        )
        assertNull(data.monochromaticImage)
        assertNull(data.smallImage)
    }

    @Test
    fun `delta complication contains delta and age without title`() {
        val service =
            Robolectric
                .buildService(GlucoseDeltaComplication::class.java)
                .create()
                .get()

        val data =
            service.getPreviewData(
                ComplicationType.SHORT_TEXT,
            ) as ShortTextComplicationData

        assertEquals(
            "0m · +5",
            data.text
                .getTextAt(
                    service.resources,
                    Instant.now(),
                )
                .toString(),
        )
        assertNull(data.title)
    }

    @Test
    fun `iob and cob show values without titles`() {
        val iobService =
            Robolectric
                .buildService(IobComplication::class.java)
                .create()
                .get()
        val iob =
            iobService.getPreviewData(
                ComplicationType.SHORT_TEXT,
            ) as ShortTextComplicationData
        assertEquals(
            "1.20U",
            iob.text
                .getTextAt(
                    iobService.resources,
                    Instant.now(),
                )
                .toString(),
        )
        assertNull(iob.title)

        val cobService =
            Robolectric
                .buildService(CobComplication::class.java)
                .create()
                .get()
        val cob =
            cobService.getPreviewData(
                ComplicationType.SHORT_TEXT,
            ) as ShortTextComplicationData
        assertEquals(
            "15g",
            cob.text
                .getTextAt(
                    cobService.resources,
                    Instant.now(),
                )
                .toString(),
        )
        assertNull(cob.title)
    }

    @Test
    fun `both graph providers return image complications`() {
        listOf(
            GlucoseGraphComplication::class.java,
            GlucoseGraphLargeComplication::class.java,
        ).forEach { provider ->
            val service =
                Robolectric
                    .buildService(provider)
                    .create()
                    .get()

            val data =
                service.getPreviewData(
                    ComplicationType.PHOTO_IMAGE,
                )

            assertEquals(
                ComplicationType.PHOTO_IMAGE,
                data.type,
            )
}
    }
}
