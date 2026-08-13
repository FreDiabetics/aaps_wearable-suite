package app.aapswear.wear

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import app.aapswear.model.GlucosePrediction
import app.aapswear.model.GlucoseSample
import app.aapswear.model.PredictionKind
import app.aapswear.model.TherapyDisplayState
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.protocol.WatchGraphStyle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@SuppressLint("DrawAllocation")
class WearGlucoseChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val emptyTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.wear_text_secondary)
            textSize =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    10f,
                    resources.displayMetrics,
                )
            textAlign = Paint.Align.CENTER
        }

    private var state: TherapyDisplayState? = null
    private var durationHours: Int = 3
    private var showPredictions: Boolean = false
    private var colors: WatchGraphColors = WatchGraphColors()
    private var graphStyle: WatchGraphStyle = WatchGraphStyle()

    fun bind(
        newState: TherapyDisplayState?,
        graphHours: Int,
        showPredictions: Boolean,
        colors: WatchGraphColors,
        style: WatchGraphStyle,
    ) {
        state = newState
        durationHours = graphHours.takeIf { it in listOf(3, 6, 12, 24) } ?: 3
        this.showPredictions = showPredictions
        this.colors = colors
        graphStyle = style
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.currentTimeMillis()
        canvas.drawColor(colors.graphBackground)
        val start = now - durationHours * 60L * 60_000L

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
                        it.measuredAtEpochMs <= now + FUTURE_TOLERANCE_MS
                }
                .associateBy { it.measuredAtEpochMs }
                .values
                .sortedBy { it.measuredAtEpochMs }

        val predictions =
            if (showPredictions) state?.glucosePredictions.orEmpty() else emptyList()

        val predictionEnd =
            predictions
                .flatMap { it.samples }
                .maxOfOrNull { it.measuredAtEpochMs }
                ?: now

        val end = max(now, predictionEnd).coerceAtLeast(start + 60_000L)
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
                visiblePredictions.flatMap { it.samples }.map { it.valueMgDl }

        val minimum = min(40.0, values.minOrNull() ?: 40.0) - 10.0
        val maximum = max(200.0, values.maxOrNull() ?: 200.0) + 10.0
        val yMin = (minimum / 20.0).toInt() * 20.0
        val yMax = ceil(maximum / 20.0) * 20.0

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

        val targetLow = state?.target?.lowMgDl ?: TARGET_LOW
        val targetHigh = state?.target?.highMgDl ?: TARGET_HIGH
        val targetTop = yFor(targetHigh)
        val targetBottom = yFor(targetLow)

        // Keep the exact ARGB value from the phone, including user-selected alpha.
        fillPaint.color = colors.rangeInRange
        canvas.drawRoundRect(
            left,
            targetTop,
            right,
            targetBottom,
            6f.dp,
            6f.dp,
            fillPaint,
        )

        linePaint.color = colors.divider
        linePaint.strokeWidth = 0.7f.dp
        canvas.drawLine(left, targetTop, right, targetTop, linePaint)
        canvas.drawLine(left, targetBottom, right, targetBottom, linePaint)

        val dividerX = xFor(now)
        if (visiblePredictions.isNotEmpty()) {
            linePaint.color = colors.divider
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect =
                DashPathEffect(
                    floatArrayOf(3f.dp, 3f.dp),
                    0f,
                )
            canvas.drawLine(dividerX, top, dividerX, bottom, linePaint)
            linePaint.pathEffect = null
        }

        val dotRadius =
            graphStyle.cgmDotRadiusDp
                .coerceIn(1.5f, 6.0f)
                .dp
        val outlineWidth =
            graphStyle.cgmDotOutlineWidthDp
                .coerceIn(0.25f, 3.0f)
                .dp

        history.forEachIndexed { index, point ->
            val rawX = xFor(point.measuredAtEpochMs)
            val x =
                if (visiblePredictions.isNotEmpty() && index == history.lastIndex) {
                    min(rawX, dividerX - 4f.dp)
                } else {
                    rawX
                }
            val y = yFor(point.valueMgDl)

            if (graphStyle.cgmDotOutlineEnabled) {
                fillPaint.color = colors.outline
                canvas.drawCircle(
                    x,
                    y,
                    dotRadius + outlineWidth,
                    fillPaint,
                )
            }

            fillPaint.color =
                glucoseColor(
                    point.valueMgDl,
                    targetLow,
                    targetHigh,
                )
            canvas.drawCircle(x, y, dotRadius, fillPaint)
        }

        history.lastOrNull()?.let { point ->
            val rawX = xFor(point.measuredAtEpochMs)
            val x =
                if (visiblePredictions.isNotEmpty()) {
                    min(rawX, dividerX - 4f.dp)
                } else {
                    rawX
                }
            val y = yFor(point.valueMgDl)
            val currentRadius = dotRadius * 1.25f

            if (graphStyle.cgmDotOutlineEnabled) {
                fillPaint.color = colors.outline
                canvas.drawCircle(
                    x,
                    y,
                    currentRadius + outlineWidth,
                    fillPaint,
                )
            }

            fillPaint.color =
                glucoseColor(
                    point.valueMgDl,
                    targetLow,
                    targetHigh,
                )
            canvas.drawCircle(
                x,
                y,
                currentRadius,
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
        fillPaint.color =
            when (series.kind) {
                PredictionKind.IOB -> colors.predictionIob

                PredictionKind.COB,
                PredictionKind.ACOB -> colors.predictionCob

                PredictionKind.UAM -> colors.predictionUam

                PredictionKind.ZERO_TEMP -> colors.predictionZeroTemp
            }

        series.samples.forEachIndexed { index, point ->
            val rawX = xFor(point.measuredAtEpochMs)
            val x =
                if (index == 0) {
                    max(rawX, dividerX + 4f.dp)
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

    private fun glucoseColor(
        valueMgDl: Double,
        low: Double,
        high: Double,
    ): Int =
        when {
            valueMgDl < low -> colors.cgmLow
            valueMgDl > high -> colors.cgmHigh
            else -> colors.cgmInRange
        }

    private val Float.dp: Float
        get() = this * density

    companion object {
        private const val TARGET_LOW = 80.0
        private const val TARGET_HIGH = 160.0
        private const val FUTURE_TOLERANCE_MS = 5 * 60_000L
    }
}
