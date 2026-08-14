package app.aapswear.wear

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import app.aapswear.model.GlucoseGraphScale
import app.aapswear.model.GlucosePrediction
import app.aapswear.model.GlucoseSample
import app.aapswear.model.PredictionKind
import app.aapswear.model.TherapyDisplayState
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.protocol.WatchGraphStyle
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("DrawAllocation")
class WearGlucoseChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    10f,
                    resources.displayMetrics,
                )
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.DEFAULT_BOLD
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
        durationHours =
            graphHours
                .takeIf { it in WearDisplayPreferences.allowedGraphHours }
                ?: 3
        this.showPredictions = showPredictions
        this.colors = colors
        graphStyle = style
        emptyTextPaint.color = colors.divider
        labelPaint.color = colors.divider
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.currentTimeMillis()
        canvas.drawColor(colors.graphBackground)

        val currentMeasurement =
            state?.glucose?.measuredAtEpochMs
                ?.coerceAtMost(now + FUTURE_TOLERANCE_MS)
                ?: now
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
                ?: currentMeasurement
        val end =
            if (showPredictions) {
                max(currentMeasurement, predictionEnd)
            } else {
                currentMeasurement
            }.coerceAtLeast(60_000L)
        val start = end - durationHours * HOUR_MS

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
                        it.measuredAtEpochMs in start..end
                }
                .associateBy { it.measuredAtEpochMs }
                .values
                .sortedBy { it.measuredAtEpochMs }

        val visiblePredictions =
            predictions
                .map { series ->
                    series.copy(
                        samples =
                            series.samples.filter {
                                it.measuredAtEpochMs in currentMeasurement..end
                            },
                    )
                }
                .filter { it.samples.isNotEmpty() }

        val left = 5f.dp
        val right = width - 5f.dp
        val top = 4f.dp
        val bottom = height - 4f.dp
        if (right <= left || bottom <= top) return

        linePaint.color = colors.divider
        linePaint.strokeWidth = 0.8f.dp
        linePaint.pathEffect = null
        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            10f.dp,
            10f.dp,
            linePaint,
        )

        if (history.isEmpty()) {
            canvas.drawText(
                "Noch keine CGM-Historie",
                width / 2f,
                height / 2f + 4f.dp,
                emptyTextPaint,
            )
            return
        }

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
                GlucoseGraphScale
                    .ratio(value)
                    .toFloat() *
                (bottom - top)

        val targetLow = state?.target?.lowMgDl ?: TARGET_LOW
        val targetHigh = state?.target?.highMgDl ?: TARGET_HIGH
        val targetTop = yFor(targetHigh)
        val targetBottom = yFor(targetLow)

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
        linePaint.strokeWidth = 0.55f.dp
        linePaint.pathEffect =
            DashPathEffect(
                floatArrayOf(3f.dp, 3f.dp),
                0f,
            )
        for (index in 1..3) {
            val gridY = top + (bottom - top) * index / 4f
            canvas.drawLine(left, gridY, right, gridY, linePaint)
        }
        linePaint.pathEffect = null

        linePaint.strokeWidth = 0.7f.dp
        canvas.drawLine(left, targetTop, right, targetTop, linePaint)
        canvas.drawLine(left, targetBottom, right, targetBottom, linePaint)
        canvas.drawText(
            targetHigh.roundForLabel(),
            right - 2f.dp,
            (targetTop - 2f.dp).coerceAtLeast(top + 10f.dp),
            labelPaint,
        )
        canvas.drawText(
            targetLow.roundForLabel(),
            right - 2f.dp,
            (targetBottom - 2f.dp).coerceAtLeast(top + 10f.dp),
            labelPaint,
        )

        val dividerX = xFor(currentMeasurement)
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

        history.forEach { point ->
            val x = xFor(point.measuredAtEpochMs)
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
            val x = xFor(point.measuredAtEpochMs)
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

    private fun Double.roundForLabel(): String = roundToInt().toString()

    private val Float.dp: Float
        get() = this * density

    companion object {
        private const val TARGET_LOW = 80.0
        private const val TARGET_HIGH = 160.0
        private const val FUTURE_TOLERANCE_MS = 5 * 60_000L
        private const val HOUR_MS = 60L * 60_000L
    }
}
