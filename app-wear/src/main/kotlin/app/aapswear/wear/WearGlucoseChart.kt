package app.aapswear.wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import app.aapswear.model.GlucosePrediction
import app.aapswear.model.GlucoseSample
import app.aapswear.model.PredictionKind
import app.aapswear.model.TherapyDisplayState
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class WearGlucoseChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

    private val emptyTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.wear_text_secondary)
            textSize =
                10f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }

    private var state: TherapyDisplayState? = null
    private var durationHours: Int = 3
    private var showPredictions: Boolean = true

    fun bind(
        newState: TherapyDisplayState?,
        graphHours: Int,
        showPredictions: Boolean,
    ) {
        state = newState
        durationHours =
            graphHours.takeIf {
                it in listOf(3, 6, 12, 24)
            } ?: 3
        this.showPredictions = showPredictions
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.currentTimeMillis()
        val start =
            now - durationHours * 60L * 60_000L

        val history =
            buildList {
                addAll(state?.glucoseHistory.orEmpty())
                state?.glucose?.let {
                    add(
                        GlucoseSample(
                            valueMgDl = it.valueMgDl,
                            measuredAtEpochMs = it.measuredAtEpochMs,
                        ),
                    )
                }
            }
                .filter {
                    it.valueMgDl in 20.0..1000.0 &&
                        it.measuredAtEpochMs >= start &&
                        it.measuredAtEpochMs <=
                        now + FUTURE_TOLERANCE_MS
                }
                .associateBy { it.measuredAtEpochMs }
                .values
                .sortedBy { it.measuredAtEpochMs }

        val predictions =
            if (showPredictions) {
                state?.glucosePredictions.orEmpty()
            } else {
                emptyList()
            }

        val predictionEnd =
            predictions
                .flatMap { it.samples }
                .maxOfOrNull { it.measuredAtEpochMs }
                ?: now

        val end =
            max(now, predictionEnd)
                .coerceAtLeast(start + 60_000L)

        val visiblePredictions =
            predictions
                .map { series ->
                    series.copy(
                        samples =
                            series.samples.filter {
                                it.measuredAtEpochMs in start..end
                            },
                    )
                }
                .filter { it.samples.isNotEmpty() }

        val left = 7f.dp
        val right = width - 7f.dp
        val top = 6f.dp
        val bottom = height - 6f.dp
        if (right <= left || bottom <= top) return

        if (history.isEmpty()) {
            canvas.drawText(
                "Noch keine CGM-Historie",
                width / 2f,
                height / 2f + 4f.dp,
                emptyTextPaint,
            )
            return
        }

        val values =
            history.map { it.valueMgDl } +
                visiblePredictions
                    .flatMap { it.samples }
                    .map { it.valueMgDl }

        val minimum =
            min(
                40.0,
                values.minOrNull() ?: 40.0,
            ) - 10.0
        val maximum =
            max(
                200.0,
                values.maxOrNull() ?: 200.0,
            ) + 10.0

        val yMin =
            (minimum / 20.0).toInt() * 20.0
        val yMax =
            ceil(maximum / 20.0) * 20.0

        fun xFor(timestamp: Long): Float =
            left +
                (
                    (timestamp - start).toDouble() /
                        (end - start).coerceAtLeast(1L)
                    )
                    .coerceIn(0.0, 1.0)
                    .toFloat() *
                (right - left)

        fun yFor(value: Double): Float =
            bottom -
                (
                    (value - yMin) /
                        (yMax - yMin).coerceAtLeast(1.0)
                    )
                    .coerceIn(0.0, 1.0)
                    .toFloat() *
                (bottom - top)

        val targetTop = yFor(TARGET_HIGH)
        val targetBottom = yFor(TARGET_LOW)

        fillPaint.color = Color.rgb(10, 57, 28)
        fillPaint.alpha = 180
        canvas.drawRoundRect(
            left,
            targetTop,
            right,
            targetBottom,
            6f.dp,
            6f.dp,
            fillPaint,
        )
        fillPaint.alpha = 255

        linePaint.color = Color.rgb(70, 70, 70)
        linePaint.strokeWidth = 0.7f.dp
        linePaint.alpha = 145
        canvas.drawLine(
            left,
            targetTop,
            right,
            targetTop,
            linePaint,
        )
        canvas.drawLine(
            left,
            targetBottom,
            right,
            targetBottom,
            linePaint,
        )
        linePaint.alpha = 255

        val dividerX = xFor(now)

        if (visiblePredictions.isNotEmpty()) {
            linePaint.color = Color.rgb(130, 130, 130)
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect =
                DashPathEffect(
                    floatArrayOf(3f.dp, 3f.dp),
                    0f,
                )
            canvas.drawLine(
                dividerX,
                top,
                dividerX,
                bottom,
                linePaint,
            )
            linePaint.pathEffect = null
        }

        history.forEachIndexed { index, point ->
            val rawX = xFor(point.measuredAtEpochMs)
            val x =
                if (
                    visiblePredictions.isNotEmpty() &&
                    index == history.lastIndex
                ) {
                    min(
                        rawX,
                        dividerX - 4f.dp,
                    )
                } else {
                    rawX
                }

            fillPaint.color =
                glucoseColor(point.valueMgDl)
            canvas.drawCircle(
                x,
                yFor(point.valueMgDl),
                2.4f.dp,
                fillPaint,
            )
        }

        history.lastOrNull()?.let { point ->
            val rawX = xFor(point.measuredAtEpochMs)
            val x =
                if (visiblePredictions.isNotEmpty()) {
                    min(
                        rawX,
                        dividerX - 4f.dp,
                    )
                } else {
                    rawX
                }
            val y = yFor(point.valueMgDl)

            fillPaint.color = Color.BLACK
            canvas.drawCircle(
                x,
                y,
                4.2f.dp,
                fillPaint,
            )
            fillPaint.color =
                glucoseColor(point.valueMgDl)
            canvas.drawCircle(
                x,
                y,
                3f.dp,
                fillPaint,
            )
        }

        visiblePredictions.forEach { series ->
            drawPrediction(
                canvas = canvas,
                series = series,
                xFor = ::xFor,
                yFor = ::yFor,
                dividerX = dividerX,
            )
        }
    }

    private fun drawPrediction(
        canvas: Canvas,
        series: GlucosePrediction,
        xFor: (Long) -> Float,
        yFor: (Double) -> Float,
        dividerX: Float,
    ) {
        val color =
            when (series.kind) {
                PredictionKind.IOB ->
                    Color.rgb(82, 193, 255)

                PredictionKind.COB,
                PredictionKind.ACOB ->
                    Color.rgb(244, 222, 0)

                PredictionKind.UAM ->
                    Color.rgb(255, 174, 31)

                PredictionKind.ZERO_TEMP ->
                    Color.rgb(48, 219, 222)
            }

        fillPaint.color = color

        series.samples.forEachIndexed { index, point ->
            val rawX = xFor(point.measuredAtEpochMs)
            val x =
                if (index == 0) {
                    max(
                        rawX,
                        dividerX + 4f.dp,
                    )
                } else {
                    rawX
                }

            canvas.drawCircle(
                x,
                yFor(point.valueMgDl),
                1.8f.dp,
                fillPaint,
            )
        }
    }

    private fun glucoseColor(valueMgDl: Double): Int =
        when {
            valueMgDl < TARGET_LOW ->
                Color.rgb(255, 92, 105)

            valueMgDl > TARGET_HIGH ->
                Color.rgb(255, 208, 64)

            else ->
                Color.WHITE
        }

    private val Float.dp: Float
        get() = this * density

    companion object {
        private const val TARGET_LOW = 80.0
        private const val TARGET_HIGH = 160.0
        private const val FUTURE_TOLERANCE_MS =
            5 * 60_000L
    }
}
