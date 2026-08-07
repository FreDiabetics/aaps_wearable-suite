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
        val bitmap = render(GlucoseDashboardChart(context).apply { bind(state, GlucoseUnit.MG_DL, true, 6) }, 420, 230)
        val greenPixels = count(bitmap) { Color.green(it) > 120 && Color.green(it) > Color.red(it) * 1.4 }
        val predictionPixels = count(bitmap) { Color.blue(it) > 180 && Color.green(it) > 120 }
        assertTrue("green=$greenPixels", greenPixels > 20)
        assertTrue("prediction=$predictionPixels", predictionPixels > 2)
    }

    @Test fun `metabolic chart renders independent iob and cob areas`() {
        val now = System.currentTimeMillis()
        val history = (0..5).map { index -> TherapyHistorySample(now - (5 - index) * 15 * 60_000L, totalIob = 0.7 + index * 0.25, cobGrams = 8.0 + index * 5) }
        val state = TherapyDisplayState(receivedAtEpochMs = now, insulin = InsulinState(totalIob = 1.95), carbs = CarbState(cobGrams = 33.0), therapyHistory = history)
        val bitmap = render(MetabolicDashboardChart(context).apply { bind(state, 6) }, 420, 260)
        val bluePixels = count(bitmap) { Color.blue(it) > 170 && Color.blue(it) > Color.red(it) * 1.2 }
        val orangePixels = count(bitmap) { Color.red(it) > 170 && Color.green(it) > 70 && Color.blue(it) < 120 }
        assertTrue("blue=$bluePixels", bluePixels > 20)
        assertTrue("orange=$orangePixels", orangePixels > 20)
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
        val bitmap = render(GlucoseDashboardChart(context).apply { bind(state, GlucoseUnit.MG_DL, false, 6) }, 420, 230)
        val redPixels = count(bitmap) { Color.red(it) > 180 && Color.red(it) > Color.green(it) * 1.5 }
        assertTrue("red=$redPixels", redPixels > 2)
    }

    private fun render(view: View, width: Int, height: Int): Bitmap {
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
