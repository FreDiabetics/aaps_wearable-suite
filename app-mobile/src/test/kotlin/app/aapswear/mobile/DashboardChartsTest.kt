package app.aapswear.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.aapswear.model.CarbState
import app.aapswear.model.GlucosePrediction
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.InsulinState
import app.aapswear.model.PredictionKind
import app.aapswear.model.TargetState
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.TherapyHistorySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardChartsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `glucose chart renders source target and prediction streams`() {
        val now = System.currentTimeMillis()
        val state = TherapyDisplayState(
            receivedAtEpochMs = now,
            glucose = GlucoseState(129.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
            glucoseHistory = listOf(100.0, 118.0, 112.0, 129.0).mapIndexed { index, value -> GlucoseSample(value, now - (3 - index) * 15 * 60_000L) },
            glucosePredictions = listOf(GlucosePrediction(PredictionKind.IOB, listOf(129.0, 120.0, 108.0).mapIndexed { index, value -> GlucoseSample(value, now + index * 5 * 60_000L) })),
            target = TargetState(80.0, 160.0),
        )
        val viewport = ChartViewport(6).apply {
            setFutureWindow(15L * 60_000L)
        }
        val bitmap = render(
            GlucoseDashboardChart(context = context, sharedViewport = viewport).apply {
                bind(
                    state = state,
                    unit = GlucoseUnit.MG_DL,
                    showPredictions = true,
                    durationHours = 6,
                    showTargetRange = true,
                    showPredictionIob = true,
                )
            },
            230,
        )
        val inRangePixels = count(bitmap) {
            Color.green(it) > 150 && Color.green(it) > Color.red(it) * 1.3
        }
        val predictionPixels = count(bitmap) { Color.blue(it) > 180 && Color.green(it) > 120 }
        assertTrue("inRange=$inRangePixels", inRangePixels > 20)
        assertTrue("prediction=$predictionPixels", predictionPixels > 2)
    }

    @Test fun `metabolic chart renders independent iob and cob areas`() {
        val now = System.currentTimeMillis()
        val history = (0..5).map { index ->
            TherapyHistorySample(
                now - (5 - index) * 15 * 60_000L,
                totalIob = 0.7 + index * 0.25,
                cobGrams = 8.0 + index * 5,
                insulinActivityUnitsPerMinute = 0.008 + index * 0.002,
                smbUnits = if (index == 2) 0.3 else null,
            )
        }
        val state = TherapyDisplayState(receivedAtEpochMs = now, insulin = InsulinState(totalIob = 1.95), carbs = CarbState(cobGrams = 33.0), therapyHistory = history)
        val bitmap = render(MetabolicDashboardChart(context).apply { bind(state, 6) }, 260)
        val bluePixels = count(bitmap) { Color.blue(it) > 170 && Color.blue(it) > Color.red(it) * 1.2 }
        val orangePixels = count(bitmap) { Color.red(it) > 170 && Color.green(it) > 70 && Color.blue(it) < 120 }
        val smbPixels = count(bitmap) { Color.green(it) > 170 && Color.blue(it) > 150 && Color.red(it) < 100 }
        val activityPixels = count(bitmap) { Color.red(it) > 190 && Color.green(it) > 150 && Color.blue(it) < 120 }
        assertTrue("blue=$bluePixels", bluePixels > 20)
        assertTrue("orange=$orangePixels", orangePixels > 20)
        assertTrue("smb=$smbPixels", smbPixels > 10)
        assertTrue("activity=$activityPixels", activityPixels > 4)
    }

    @Test fun `toolkit metabolic scaling adds headroom aligns zero and uses fixed smb sizes`() {
        val iob = toolkitMetabolicRange(listOf(0.5, 2.0))
        val cob = toolkitMetabolicRange(listOf(10.0, 30.0), iob.zeroRatio)

        assertEquals(2.0 * 1.08, iob.maximum, 0.0001)
        assertEquals(-iob.maximum * 0.08, iob.minimum, 0.0001)
        assertEquals(30.0 * 1.08, cob.maximum, 0.0001)
        assertEquals(iob.zeroRatio, cob.zeroRatio, 0.0001)
        assertEquals(9f, toolkitSmbMarkerSide(0.1))
        assertEquals(12f, toolkitSmbMarkerSide(0.25))
        assertEquals(15f, toolkitSmbMarkerSide(0.5))
    }

    @Test fun `metabolic future projections follow recent observed decay`() {
        val now = 10_000_000L
        val history = listOf(
            TherapyHistorySample(
                measuredAtEpochMs = now - 10 * 60_000L,
                totalIob = 1.0,
                cobGrams = 30.0,
            ),
            TherapyHistorySample(
                measuredAtEpochMs = now,
                totalIob = 0.8,
                cobGrams = 20.0,
            ),
        )

        val iob = buildIobProjection(
            history,
            now,
            now + 10 * 60_000L,
        )
        val cob = buildCobProjection(
            history,
            now,
            now + 10 * 60_000L,
        )

        assertEquals(3, iob.size)
        assertEquals(0.8, iob[0].second, 0.0001)
        assertEquals(0.7, iob[1].second, 0.0001)
        assertEquals(0.6, iob[2].second, 0.0001)

        assertEquals(3, cob.size)
        assertEquals(20.0, cob[0].second, 0.0001)
        assertEquals(15.0, cob[1].second, 0.0001)
        assertEquals(10.0, cob[2].second, 0.0001)
    }

    @Test fun `glucose dots use alert color outside display range`() {
        val now = System.currentTimeMillis()
        val state = TherapyDisplayState(
            receivedAtEpochMs = now,
            glucose = GlucoseState(55.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
            glucoseHistory = listOf(62.0, 58.0, 55.0).mapIndexed { index, value ->
                GlucoseSample(value, now - (2 - index) * 5 * 60_000L)
            },
            target = TargetState(80.0, 160.0),
        )
        val bitmap = render(GlucoseDashboardChart(context).apply { bind(state, GlucoseUnit.MG_DL, false, 6) }, 230)
        val redPixels = count(bitmap) { Color.red(it) > 180 && Color.red(it) > Color.green(it) * 1.5 }
        assertTrue("red=$redPixels", redPixels > 2)
    }

    @Test fun `glucose chart compresses sub target range and keeps zero above edge`() {
        val zero =
            glucoseLogRatio(
                0.0,
            )
        val low =
            glucoseLogRatio(
                80.0,
            )
        val targetHigh =
            glucoseLogRatio(
                160.0,
            )
        val maximum =
            glucoseLogRatio(
                400.0,
            )

        assertTrue(
            "zero=$zero",
            zero > 0.0,
        )
        assertTrue(
            "subTarget=${low - zero}",
            low -
                zero <
                targetHigh -
                    low,
        )
        assertEquals(
            1.0,
            maximum,
            0.0001,
        )
    }

    @Test fun `viewport cannot pan beyond configured future edge`() {
        val now = 10_000_000L
        val viewport = ChartViewport(6)
        viewport.setFutureWindow(0L)
        viewport.pan(-10_000f, 100f)
        assertEquals(0L, viewport.panMs)
        assertEquals(now, viewport.endEpochMs(now))

        viewport.setFutureWindow(60L * 60_000L)
        viewport.pan(-10_000f, 100f)
        assertEquals(0L, viewport.panMs)
        assertEquals(now + 60L * 60_000L, viewport.endEpochMs(now))
    }

    private fun render(view: View, height: Int): Bitmap {
        val width = 420
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun count(bitmap: Bitmap, predicate: (Int) -> Boolean): Int {
        var result = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (predicate(bitmap.getPixel(x, y))) result++
        return result
    }
}
