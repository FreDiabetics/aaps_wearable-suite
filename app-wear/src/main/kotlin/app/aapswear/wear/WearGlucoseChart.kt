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
            textSize =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    10f,
                    resources.displayMetrics,
                )
            textAlign = Paint.Align.CENTER
        }
    private val targetLabelPaint =
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
    private var stateSignature: List<Any?>? = null

    fun bind(
        newState: TherapyDisplayState?,
        graphHours: Int,
        showPredictions: Boolean,
        colors: WatchGraphColors,
        style: WatchGraphStyle,
    ) {
        val resolvedDuration =
            graphHours
                .takeIf { it in WearDisplayPreferences.allowedGraphHours }
                ?: 3
        val newStateSignature =
            newState?.let {
                listOf(
                    it.glucose,
                    it.glucoseHistory,
                    it.glucosePredictions,
                    it.target,
                )
            }
        if (
            stateSignature == newStateSignature &&
            durationHours == resolvedDuration &&
            this.showPredictions == showPredictions &&
            this.colors == colors &&
            graphStyle == style
        ) {
            return
        }

        state = newState
        stateSignature = newStateSignature
        durationHours = resolvedDuration
        this.showPredictions = showPredictions
        this.colors = colors
        graphStyle = style
        emptyTextPaint.color = colors.divider
        targetLabelPaint.color = colors.divider
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.currentTimeMillis()
        val outerLeft = 1f.dp
        val outerTop = 1f.dp
        val outerRight = width - 1f.dp
        val outerBottom = height - 1f.dp
        if (outerRight <= outerLeft || outerBottom <= outerTop) return

        fillPaint.color = colors.graphBackground
        canvas.drawRoundRect(
            outerLeft,
            outerTop,
            outerRight,
            outerBottom,
            14f.dp,
            14f.dp,
            fillPaint,
        )

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

        val left = 6f.dp
        val right = width - 6f.dp
        val top = 5f.dp
        val bottom = height - 5f.dp
        if (right <= left || bottom <= top) return

        linePaint.color = colors.divider
        linePaint.strokeWidth = 0.8f.dp
        linePaint.pathEffect = null
        canvas.drawRoundRect(
            outerLeft,
            outerTop,
            outerRight,
            outerBottom,
            14f.dp,
            14f.dp,
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

        fillPaint.color = colors.rangeHigh
        canvas.drawRect(
            left,
            top,
            right,
            targetTop,
            fillPaint,
        )

        fillPaint.color = colors.rangeInRange
        canvas.drawRoundRect(
            left,
            targetTop,
            right,
            targetBottom,
            5f.dp,
            5f.dp,
            fillPaint,
        )

        fillPaint.color = colors.rangeLow
        canvas.drawRect(
            left,
            targetBottom,
            right,
            bottom,
            fillPaint,
        )

        linePaint.color = colors.divider
        linePaint.pathEffect = null
        linePaint.strokeWidth = 0.7f.dp
        canvas.drawLine(left, targetTop, right, targetTop, linePaint)
        canvas.drawLine(left, targetBottom, right, targetBottom, linePaint)

        canvas.drawText(
            targetHigh.toInt().toString(),
            right - 1f.dp,
            targetTop - 2f.dp,
            targetLabelPaint,
        )
        canvas.drawText(
            targetLow.toInt().toString(),
            right - 1f.dp,
            targetBottom - 2f.dp,
            targetLabelPaint,
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
            drawCgmDot(
                canvas = canvas,
                x = xFor(point.measuredAtEpochMs),
                y = yFor(point.valueMgDl),
                valueMgDl = point.valueMgDl,
                targetLow = targetLow,
                targetHigh = targetHigh,
                radius = dotRadius,
                outlineWidth = outlineWidth,
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

    private fun drawCgmDot(
        canvas: Canvas,
        x: Float,
        y: Float,
        valueMgDl: Double,
        targetLow: Double,
        targetHigh: Double,
        radius: Float,
        outlineWidth: Float,
    ) {
        if (graphStyle.cgmDotOutlineEnabled) {
            fillPaint.color = colors.outline
            canvas.drawCircle(
                x,
                y,
                radius + outlineWidth,
                fillPaint,
            )
        }

        fillPaint.color =
            glucoseColor(
                valueMgDl,
                targetLow,
                targetHigh,
            )
        canvas.drawCircle(x, y, radius, fillPaint)
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
        private const val HOUR_MS = 60L * 60_000L
    }
}
