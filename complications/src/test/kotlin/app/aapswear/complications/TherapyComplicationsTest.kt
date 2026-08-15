package app.aapswear.complications

import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
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
        assertEquals(35, AllProviders.classes.distinct().size)
        assertEquals(GlucoseComplication::class.java, AllProviders.classes.first())
        assertEquals(GlucoseGraphLargeComplication::class.java, AllProviders.classes.last())
    }

    @Test
    fun `glucose plus delta exposes both values`() {
        val service = Robolectric.buildService(GlucosePlusDeltaComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("123", data.text.getTextAt(service.resources, Instant.now()).toString())
        assertEquals("+5", data.title!!.getTextAt(service.resources, Instant.now()).toString())
    }

    @Test
    fun `IOB COB basal keeps basal in the title`() {
        val service = Robolectric.buildService(IobCobBasalComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("1.2U · 15g", data.text.getTextAt(service.resources, Instant.now()).toString())
        assertEquals("0.80U/h", data.title!!.getTextAt(service.resources, Instant.now()).toString())
    }

    @Test
    fun `glucose trend also supplies short text`() {
        val service = Robolectric.buildService(GlucoseTrendComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("123", data.text.getTextAt(service.resources, Instant.now()).toString())
        org.junit.Assert.assertNotNull(data.monochromaticImage)
    }

    @Test
    fun `glucose trend ranged complication separates value and trend for renderers`() {
        val service =
            Robolectric
                .buildService(GlucoseTrendRangedValueComplication::class.java)
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
        assertNull(data.title)
        org.junit.Assert.assertNotNull(data.monochromaticImage)
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
            "+5",
            data.text.getTextAt(service.resources, Instant.now()).toString(),
        )
        assertEquals(
            "0m",
            data.title!!.getTextAt(service.resources, Instant.now()).toString(),
        )
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
    fun `graph providers each return only their declared image type`() {
        val small = Robolectric.buildService(GlucoseGraphComplication::class.java).create().get()
        val large = Robolectric.buildService(GlucoseGraphLargeComplication::class.java).create().get()

        assertEquals(
            ComplicationType.SMALL_IMAGE,
            (small.getPreviewData(ComplicationType.PHOTO_IMAGE) as SmallImageComplicationData).type,
        )
        assertEquals(
            ComplicationType.PHOTO_IMAGE,
            (large.getPreviewData(ComplicationType.SMALL_IMAGE) as PhotoImageComplicationData).type,
        )
    }

    @Test
    fun `glucose providers keep short long and ranged data in separate services`() {
        val short = Robolectric.buildService(GlucoseComplication::class.java).create().get()
        val long = Robolectric.buildService(GlucoseLongTextComplication::class.java).create().get()
        val ranged = Robolectric.buildService(GlucoseRangedValueComplication::class.java).create().get()

        assertEquals(ComplicationType.SHORT_TEXT, short.getPreviewData(ComplicationType.LONG_TEXT).type)
        assertEquals(
            ComplicationType.LONG_TEXT,
            (long.getPreviewData(ComplicationType.SHORT_TEXT) as LongTextComplicationData).type,
        )
        assertEquals(
            ComplicationType.RANGED_VALUE,
            (ranged.getPreviewData(ComplicationType.SHORT_TEXT) as RangedValueComplicationData).type,
        )
    }
}
