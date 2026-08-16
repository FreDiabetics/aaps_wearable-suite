package app.aapswear.wear

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.parser.StaticPreviewDataParser
import app.aapswear.complications.BasalComplication
import app.aapswear.complications.CobComplication
import app.aapswear.complications.CobRangedValueComplication
import app.aapswear.complications.DateComplication
import app.aapswear.complications.DeltaOnlyComplication
import app.aapswear.complications.GlucoseAgeComplication
import app.aapswear.complications.GlucoseComplication
import app.aapswear.complications.GlucoseDeltaComplication
import app.aapswear.complications.GlucoseGraphComplication
import app.aapswear.complications.GlucoseLongTextComplication
import app.aapswear.complications.GlucosePlusDeltaComplication
import app.aapswear.complications.GlucosePlusDeltaLongTextComplication
import app.aapswear.complications.GlucoseRangedValueComplication
import app.aapswear.complications.GlucoseTrendAgeComplication
import app.aapswear.complications.GlucoseTrendAgeLongTextComplication
import app.aapswear.complications.GlucoseTrendComplication
import app.aapswear.complications.GlucoseTrendDeltaAgeComplication
import app.aapswear.complications.GlucoseTrendDeltaAgeLongTextComplication
import app.aapswear.complications.GlucoseTrendDeltaComplication
import app.aapswear.complications.GlucoseTrendLongTextComplication
import app.aapswear.complications.GlucoseTrendRangedValueComplication
import app.aapswear.complications.IobCobBasalComplication
import app.aapswear.complications.IobCobBasalLongTextComplication
import app.aapswear.complications.IobComplication
import app.aapswear.complications.IobRangedValueComplication
import app.aapswear.complications.LoopComplication
import app.aapswear.complications.LoopIconComplication
import app.aapswear.complications.ReservoirComplication
import app.aapswear.complications.ReservoirRangedValueComplication
import app.aapswear.complications.SensorAgeComplication
import app.aapswear.complications.SensorAgeRangedValueComplication
import app.aapswear.complications.TirComplication
import app.aapswear.complications.TirGoalProgressComplication
import app.aapswear.complications.TrendOnlyComplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComplicationStaticPreviewTest {
    @Test
    fun `every parser supported provider has matching static picker data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = listOf(
            GlucoseComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseLongTextComplication::class.java to ComplicationType.LONG_TEXT,
            GlucoseRangedValueComplication::class.java to ComplicationType.RANGED_VALUE,
            GlucoseTrendComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseTrendLongTextComplication::class.java to ComplicationType.LONG_TEXT,
            GlucoseTrendRangedValueComplication::class.java to ComplicationType.RANGED_VALUE,
            GlucosePlusDeltaComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucosePlusDeltaLongTextComplication::class.java to ComplicationType.LONG_TEXT,
            GlucoseTrendAgeComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseTrendAgeLongTextComplication::class.java to ComplicationType.LONG_TEXT,
            GlucoseTrendDeltaComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseTrendDeltaAgeComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseTrendDeltaAgeLongTextComplication::class.java to ComplicationType.LONG_TEXT,
            GlucoseGraphComplication::class.java to ComplicationType.SMALL_IMAGE,
            TrendOnlyComplication::class.java to ComplicationType.SHORT_TEXT,
            DeltaOnlyComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseAgeComplication::class.java to ComplicationType.SHORT_TEXT,
            GlucoseDeltaComplication::class.java to ComplicationType.SHORT_TEXT,
            SensorAgeComplication::class.java to ComplicationType.SHORT_TEXT,
            SensorAgeRangedValueComplication::class.java to ComplicationType.RANGED_VALUE,
            BasalComplication::class.java to ComplicationType.SHORT_TEXT,
            IobComplication::class.java to ComplicationType.SHORT_TEXT,
            IobRangedValueComplication::class.java to ComplicationType.RANGED_VALUE,
            CobComplication::class.java to ComplicationType.SHORT_TEXT,
            CobRangedValueComplication::class.java to ComplicationType.RANGED_VALUE,
            IobCobBasalComplication::class.java to ComplicationType.SHORT_TEXT,
            IobCobBasalLongTextComplication::class.java to ComplicationType.LONG_TEXT,
            LoopComplication::class.java to ComplicationType.SHORT_TEXT,
            LoopIconComplication::class.java to ComplicationType.MONOCHROMATIC_IMAGE,
            ReservoirComplication::class.java to ComplicationType.SHORT_TEXT,
            ReservoirRangedValueComplication::class.java to ComplicationType.RANGED_VALUE,
            TirComplication::class.java to ComplicationType.SHORT_TEXT,
            TirGoalProgressComplication::class.java to ComplicationType.GOAL_PROGRESS,
            DateComplication::class.java to ComplicationType.SHORT_TEXT,
        )

        expected.forEach { (provider, type) ->
            val preview = StaticPreviewDataParser.parsePreviewData(
                context,
                ComponentName(context, provider),
            )
            assertNotNull("Missing static preview for ${provider.simpleName}", preview)
            assertEquals(type, preview!![type]?.type)
        }
    }
}
