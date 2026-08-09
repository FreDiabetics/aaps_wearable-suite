package app.aapswear.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.withClip
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.GlucosePrediction
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.PredictionKind
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.TherapyHistorySample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val HOUR_MS = 60L * 60_000L
private const val BASAL_HEIGHT_FRACTION = 0.28f
private const val ACTIVITY_HEIGHT_FRACTION = 0.8f
private const val GRAPH_PLOT_LEFT_DP = 6f
private const val GRAPH_LABEL_GUTTER_DP = 31f
private const val GRAPH_CORNER_RADIUS_DP = 18f
private const val GLUCOSE_LOG_MIN = 40.0
private const val GLUCOSE_LOG_MAX = 400.0
private const val TOOLKIT_SECONDARY_MINIMUM = -2.0
private const val TOOLKIT_ACTIVITY_SCALE_FACTOR = 2.2

internal class ChartViewport(initialHours: Int) {
    var hours = initialHours.toFloat().coerceIn(1f, 24f)
    var panMs = 0L

    fun zoom(scaleFactor: Float) {
        hours = (hours / scaleFactor.coerceAtLeast(0.05f)).coerceIn(1f, 24f)
        clampPan()
    }

    fun pan(deltaPixels: Float, width: Float) {
        if (width <= 0f) return
        panMs -= (deltaPixels / width * hours * HOUR_MS).toLong()
        clampPan()
    }

    private fun clampPan() {
        panMs = panMs.coerceIn(-24L * HOUR_MS, 2L * HOUR_MS)
    }
}

internal abstract class InteractiveChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    initialHours: Int,
) : View(context, attrs) {
    protected val viewport = ChartViewport(initialHours)
    private var lastX = 0f
    private var downX = 0f
    private var downY = 0f
    private var moving = false
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                viewport.zoom(detector.scaleFactor)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                downX = event.x
                downY = event.y
                moving = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val delta = event.x - lastX
                    val horizontalGesture = abs(event.x - downX) > abs(event.y - downY) + 4f
                    if (horizontalGesture && abs(delta) > 0.4f) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        viewport.pan(delta, width.toFloat())
                        moving = true
                        invalidate()
                    }
                    lastX = event.x
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moving) performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

@SuppressLint("DrawAllocation")
internal class GlucoseDashboardChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : InteractiveChartView(context, attrs, 6) {
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var state: TherapyDisplayState? = null
    private var unit = GlucoseUnit.MG_DL
    private var showPredictions = true

    fun bind(state: TherapyDisplayState?, unit: GlucoseUnit, showPredictions: Boolean, durationHours: Int) {
        this.state = state
        this.unit = unit
        this.showPredictions = showPredictions
        if (!isAttachedToWindow) viewport.hours = durationHours.toFloat().coerceIn(1f, 24f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scaleContainer = RectF(0.5f.dp, 0.5f.dp, width - 0.5f.dp, height - 0.5f.dp)
        val plot = RectF(
            scaleContainer.left + GRAPH_PLOT_LEFT_DP.dp,
            scaleContainer.top + 7f.dp,
            scaleContainer.right - GRAPH_LABEL_GUTTER_DP.dp,
            scaleContainer.bottom - 23f.dp,
        )
        if (plot.width() <= 24f || plot.height() <= 24f) return
        val radius = GRAPH_CORNER_RADIUS_DP.dp
        val clip = Path().apply { addRoundRect(scaleContainer, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
            fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
            canvas.drawRoundRect(scaleContainer, radius, radius, fillPaint)
            val now = System.currentTimeMillis()
            val targetLow = state?.target?.lowMgDl ?: 80.0
            val targetHigh = state?.target?.highMgDl ?: 160.0
            val predictions = if (showPredictions) state?.glucosePredictions.orEmpty() else emptyList()
            val horizon = predictions.flatMap { it.samples }.maxOfOrNull { it.measuredAtEpochMs }
                ?.minus(now)?.coerceAtLeast(0L) ?: 0L
            val end = (now + min(horizon, 2L * HOUR_MS) + viewport.panMs)
            val start = end - (viewport.hours * HOUR_MS).toLong()

            val history = buildList {
                addAll(state?.glucoseHistory.orEmpty())
                state?.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, state!!.source)) }
            }.sortedBy { it.measuredAtEpochMs }
                .distinctBy { it.measuredAtEpochMs to it.source }
                .filter { it.measuredAtEpochMs in start..min(end, now) }
            val visiblePredictions = predictions.map { series ->
                series.copy(samples = series.samples.filter { it.measuredAtEpochMs > now && it.measuredAtEpochMs in start..end })
            }.filter { it.samples.isNotEmpty() }
            val targetTop = mapGlucoseY(targetHigh, plot)
            val targetBottom = mapGlucoseY(targetLow, plot)
            fillPaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.RANGE_IN_RANGE), 40)
            canvas.drawRect(plot.left, targetTop, plot.right, targetBottom, fillPaint)
            drawGrid(canvas, plot, start, end)
            drawBasal(canvas, plot, start, end, state?.therapyHistory.orEmpty())
            drawInsulinActivity(
                canvas,
                RectF(plot.left, targetTop, plot.right, targetBottom),
                start,
                end,
                now,
                state?.therapyHistory.orEmpty(),
            )

            val dividerX = mapX(now, start, end, plot).coerceIn(plot.left, plot.right)
            if (visiblePredictions.isNotEmpty() && now in start..end) {
                linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_DIVIDER)
                linePaint.strokeWidth = 1f.dp
                linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
                canvas.drawLine(dividerX, plot.top, dividerX, plot.bottom, linePaint)
                linePaint.pathEffect = null
            }

            history.forEachIndexed { index, point ->
                val x = min(mapX(point.measuredAtEpochMs, start, end, plot), dividerX - 2f.dp)
                val y = mapGlucoseY(point.valueMgDl, plot)
                val current = index == history.lastIndex
                fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)
                canvas.drawCircle(x, y, (if (current) 3.75f else 3.35f).dp, fillPaint)
                fillPaint.color = dotColor(point.valueMgDl, targetLow, targetHigh)
                canvas.drawCircle(x, y, (if (current) 2.5f else 2.4f).dp, fillPaint)
            }
            visiblePredictions.forEach { drawPrediction(canvas, it, plot, start, end, dividerX) }

            drawTargetLabel(
                canvas,
                glucoseLabel(targetHigh),
                scaleContainer.right - 7f.dp,
                targetTop + 3f.dp,
            )
            drawTargetLabel(
                canvas,
                glucoseLabel(targetLow),
                scaleContainer.right - 7f.dp,
                targetBottom + 3f.dp,
            )
            if (history.size < 2) {
                drawText(canvas, "Noch kein Verlauf", plot.centerX(), plot.centerY(), 10f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED), Paint.Align.CENTER)
            }
        }
        drawRoundedBorder(canvas, scaleContainer, radius)
    }

    private fun drawGrid(canvas: Canvas, plot: RectF, start: Long, end: Long) {
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_GRID)
        linePaint.strokeWidth = 0.7f.dp
        linePaint.pathEffect = DashPathEffect(floatArrayOf(3f.dp, 3f.dp), 0f)
        for (index in 1..3) {
            val y = plot.top + plot.height() * index / 4f
            canvas.drawLine(plot.left, y, plot.right, y, linePaint)
        }
        for (index in 1..2) {
            val x = plot.left + plot.width() * index / 3f
            canvas.drawLine(x, plot.top, x, plot.bottom, linePaint)
        }
        linePaint.pathEffect = null
        repeat(4) { index ->
            val x = plot.left + plot.width() * index / 3f
            val align = when (index) { 0 -> Paint.Align.LEFT; 3 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            drawText(canvas, timeFormat.format(Date(start + (end - start) * index / 3)), x, plot.bottom + 15f.dp, 8.5f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), align)
        }
    }

    private fun drawBasal(canvas: Canvas, plot: RectF, start: Long, end: Long, points: List<TherapyHistorySample>) {
        val sorted = points.filter { it.baseBasalUnitsPerHour != null || it.basalUnitsPerHour != null }
            .sortedBy { it.measuredAtEpochMs }
        val visible = windowedStepSamples(sorted, start, end)
        if (visible.size < 2) return
        val maxBasal = max(
            0.1,
            visible.flatMap { listOfNotNull(it.baseBasalUnitsPerHour ?: it.basalUnitsPerHour, effectiveBasal(it)) }
                .maxOrNull() ?: 0.1,
        )
        fun basalY(value: Double): Float = plot.top +
            (value.coerceIn(0.0, maxBasal) / maxBasal).toFloat() * plot.height() * BASAL_HEIGHT_FRACTION
        val cyan = SugarliciousColors.argb(SugarliciousColorRole.SECONDARY)
        val effective = visible.map { it.measuredAtEpochMs to effectiveBasal(it) }
        val base = visible.map { it.measuredAtEpochMs to (it.baseBasalUnitsPerHour ?: it.basalUnitsPerHour ?: 0.0) }
        val clip = Path().apply { addRoundRect(plot, 14f.dp, 14f.dp, Path.Direction.CW) }
        canvas.withClip(clip) {
            val area = stepPath(effective, start, end, plot, ::basalY, closeAt = plot.top)
            fillPaint.color = withAlpha(cyan, 76)
            drawPath(area, fillPaint)

            linePaint.color = cyan
            linePaint.strokeWidth = 1.2f.dp
            linePaint.pathEffect = null
            drawPath(stepPath(effective, start, end, plot, ::basalY), linePaint)
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = DashPathEffect(floatArrayOf(1f.dp, 2f.dp), 0f)
            drawPath(stepPath(base, start, end, plot, ::basalY), linePaint)
            linePaint.pathEffect = null
        }
    }

    private fun drawInsulinActivity(
        canvas: Canvas,
        band: RectF,
        start: Long,
        end: Long,
        now: Long,
        points: List<TherapyHistorySample>,
    ) {
        val actual = points.mapNotNull { point ->
            point.insulinActivityUnitsPerMinute?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { point.measuredAtEpochMs to it }
        }.filter { it.first in start..min(end, now) }.sortedBy { it.first }
        if (actual.size < 2) return
        val future = buildActivityProjection(actual.last(), max(now, actual.last().first), end)
        val maxActivity = max(0.0001, (actual.map { it.second } + future.map { it.second }).maxOrNull() ?: 0.0001)
        fun activityY(value: Double): Float = band.bottom -
            (value / maxActivity).coerceIn(0.0, 1.0).toFloat() * band.height() * ACTIVITY_HEIGHT_FRACTION
        val yellow = Color.rgb(242, 201, 76)
        linePaint.color = yellow
        linePaint.strokeWidth = 1.1f.dp
        linePaint.pathEffect = null
        canvas.drawPath(valuePath(actual, start, end, band, ::activityY), linePaint)
        if (future.size >= 2) {
            linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
            canvas.drawPath(valuePath(future, start, end, band, ::activityY), linePaint)
            linePaint.pathEffect = null
        }
    }

    private fun drawPrediction(
        canvas: Canvas,
        series: GlucosePrediction,
        plot: RectF,
        start: Long,
        end: Long,
        dividerX: Float,
    ) {
        val color = when (series.kind) {
            PredictionKind.IOB -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_IOB)
            PredictionKind.COB, PredictionKind.ACOB -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_COB)
            PredictionKind.UAM -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_UAM)
            PredictionKind.ZERO_TEMP -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_ZERO_TEMP)
        }
        series.samples.forEach { point ->
            val x = max(mapX(point.measuredAtEpochMs, start, end, plot), dividerX + 3f.dp)
            if (x > plot.right) return@forEach
            val y = mapGlucoseY(point.valueMgDl, plot)
            fillPaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE), 190)
            canvas.drawCircle(x, y, 2.45f.dp, fillPaint)
            fillPaint.color = color
            canvas.drawCircle(x, y, 1.75f.dp, fillPaint)
        }
    }

    private fun drawRoundedBorder(canvas: Canvas, rect: RectF, radius: Float) {
        linePaint.style = Paint.Style.STROKE
        linePaint.pathEffect = null
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
        linePaint.strokeWidth = 1f.dp
        canvas.drawRoundRect(rect, radius, radius, linePaint)
    }

    private fun dotColor(value: Double, low: Double, high: Double): Int = when {
        value < low -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_LOW)
        value > high -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_HIGH)
        else -> SugarliciousColors.argb(SugarliciousColorRole.CGM_DOT_IN_RANGE)
    }

    private fun glucoseLabel(valueMgDl: Double): String = if (unit == GlucoseUnit.MMOL_L) {
        String.format(Locale.getDefault(), "%.1f", valueMgDl / 18.0)
    } else valueMgDl.toInt().toString()

    private fun drawTargetLabel(canvas: Canvas, value: String, x: Float, y: Float) =
        drawText(canvas, value, x, y, 9f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), Paint.Align.RIGHT)

    private val Float.dp get() = this * density
}

@SuppressLint("DrawAllocation")
internal class MetabolicDashboardChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : InteractiveChartView(context, attrs, 6) {
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var state: TherapyDisplayState? = null

    fun bind(state: TherapyDisplayState?, durationHours: Int) {
        this.state = state
        if (!isAttachedToWindow) viewport.hours = durationHours.toFloat().coerceIn(1f, 24f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val outer = RectF(0.5f.dp, 0.5f.dp, width - 0.5f.dp, height - 0.5f.dp)
        if (outer.width() <= 24f || outer.height() <= 24f) return
        val radius = GRAPH_CORNER_RADIUS_DP.dp
        val clip = Path().apply { addRoundRect(outer, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
            fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
            canvas.drawRoundRect(outer, radius, radius, fillPaint)
            val end = System.currentTimeMillis() + viewport.panMs
            val start = end - (viewport.hours * HOUR_MS).toLong()
            val allPoints = state?.therapyHistory.orEmpty()
            val points = allPoints.filter { it.measuredAtEpochMs in start..end }
            val left = GRAPH_PLOT_LEFT_DP.dp
            val right = width - GRAPH_LABEL_GUTTER_DP.dp
            val top = 9f.dp
            val bottom = height - 23f.dp
            val gap = 9f.dp
            val half = (bottom - top - gap) / 2f
            val iobPlot = RectF(left, top, right, top + half)
            val cobPlot = RectF(left, top + half + gap, right, bottom)
            val iobRange = toolkitMetabolicRange(allPoints.mapNotNull { it.totalIob })
            val cobRange = toolkitMetabolicRange(
                allPoints.mapNotNull { it.cobGrams },
                sharedZeroRatio = iobRange.zeroRatio,
            )
            drawSharedGrid(canvas, iobPlot, cobPlot, start, end)
            drawLane(canvas, iobPlot, points, start, end, iob = true, range = iobRange)
            drawInsulinActivity(canvas, iobPlot, allPoints, points, start, end, iobRange.zeroRatio)
            drawLane(canvas, cobPlot, points, start, end, iob = false, range = cobRange)
            drawSmbMarkers(
                canvas,
                iobPlot,
                points,
                start,
                end,
                zeroY = mapY(0.0, iobRange.minimum, iobRange.maximum, iobPlot),
            )
            repeat(4) { index ->
                val x = left + (right - left) * index / 3f
                val align = when (index) { 0 -> Paint.Align.LEFT; 3 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                drawText(canvas, timeFormat.format(Date(start + (end - start) * index / 3)), x, bottom + 15f.dp, 8.5f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), align)
            }
            if (points.none { it.totalIob != null || it.cobGrams != null }) {
                drawText(canvas, "Noch kein IOB/COB-Verlauf", (left + right) / 2f, (top + bottom) / 2f, 10f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED), Paint.Align.CENTER)
            }
        }
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
        linePaint.strokeWidth = 1f.dp
        linePaint.pathEffect = null
        canvas.drawRoundRect(outer, radius, radius, linePaint)
    }

    private fun drawSharedGrid(canvas: Canvas, iob: RectF, cob: RectF, start: Long, end: Long) {
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_GRID)
        linePaint.strokeWidth = 0.7f.dp
        linePaint.pathEffect = DashPathEffect(floatArrayOf(3f.dp, 3f.dp), 0f)
        listOf(iob, cob).forEach { lane ->
            for (index in 1..2) {
                val y = lane.top + lane.height() * index / 3f
                canvas.drawLine(lane.left, y, lane.right, y, linePaint)
            }
        }
        for (index in 1..2) {
            val x = iob.left + iob.width() * index / 3f
            canvas.drawLine(x, iob.top, x, cob.bottom, linePaint)
        }
        linePaint.pathEffect = null
        linePaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.BORDER), 130)
        linePaint.strokeWidth = 0.7f.dp
        canvas.drawLine(iob.left, iob.bottom + (cob.top - iob.bottom) / 2f, iob.right, iob.bottom + (cob.top - iob.bottom) / 2f, linePaint)
    }

    private fun drawLane(
        canvas: Canvas,
        plot: RectF,
        points: List<TherapyHistorySample>,
        start: Long,
        end: Long,
        iob: Boolean,
        range: ToolkitMetabolicRange,
    ) {
        val actual = points.mapNotNull { point ->
            (if (iob) point.totalIob else point.cobGrams)?.takeIf { it.isFinite() }
                ?.let { point.measuredAtEpochMs to it }
        }.sortedBy { it.first }
        if (actual.isEmpty()) return
        fun y(value: Double) = mapY(value, range.minimum, range.maximum, plot)
        val zeroY = y(0.0)
        val color = SugarliciousColors.argb(if (iob) SugarliciousColorRole.GRAPH_IOB else SugarliciousColorRole.GRAPH_COB)
        val area = Path().apply {
            moveTo(mapX(actual.first().first, start, end, plot), zeroY)
            actual.forEach { (time, value) -> lineTo(mapX(time, start, end, plot), y(value)) }
            lineTo(mapX(actual.last().first, start, end, plot), zeroY)
            close()
        }
        fillPaint.shader = LinearGradient(
            0f,
            plot.top,
            0f,
            plot.bottom,
            withAlpha(color, 112),
            withAlpha(color, 7),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(area, fillPaint)
        fillPaint.shader = null

        linePaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED), 150)
        linePaint.strokeWidth = 0.8f.dp
        linePaint.pathEffect = null
        canvas.drawLine(plot.left, zeroY, plot.right, zeroY, linePaint)
        linePaint.color = color
        linePaint.strokeWidth = 2.2f.dp
        linePaint.pathEffect = null
        canvas.drawPath(valuePath(actual, start, end, plot, ::y), linePaint)
    }

    private fun drawInsulinActivity(
        canvas: Canvas,
        plot: RectF,
        allPoints: List<TherapyHistorySample>,
        visiblePoints: List<TherapyHistorySample>,
        start: Long,
        end: Long,
        sharedZeroRatio: Double,
    ) {
        val actual = visiblePoints.mapNotNull { point ->
            point.insulinActivityUnitsPerMinute?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { point.measuredAtEpochMs to it }
        }.sortedBy { it.first }
        if (actual.size < 2) return
        val maximum = allPoints.mapNotNull { it.insulinActivityUnitsPerMinute }
            .filter { it.isFinite() && it >= 0.0 }
            .maxOrNull()
            ?.times(TOOLKIT_ACTIVITY_SCALE_FACTOR)
            ?.coerceAtLeast(0.000001)
            ?: return
        val minimum = toolkitMinimumForZeroRatio(maximum, sharedZeroRatio)
        fun y(value: Double) = mapY(value, minimum, maximum, plot)
        linePaint.color = Color.rgb(242, 201, 76)
        linePaint.strokeWidth = 1.25f.dp
        linePaint.pathEffect = null
        canvas.drawPath(valuePath(actual, start, end, plot, ::y), linePaint)
    }

    private fun drawSmbMarkers(
        canvas: Canvas,
        plot: RectF,
        points: List<TherapyHistorySample>,
        start: Long,
        end: Long,
        zeroY: Float,
    ) {
        val markers = points.mapNotNull { point -> point.smbUnits?.takeIf { it > 0.0 }?.let { point.measuredAtEpochMs to it } }
        if (markers.isEmpty()) return
        fillPaint.color = Color.rgb(42, 202, 186)
        markers.forEach { (time, units) ->
            val side = toolkitSmbMarkerSide(units).dp
            val halfWidth = side / 2f
            val markerHeight = side * (sqrt(3.0).toFloat() / 2f)
            val x = mapX(time, start, end, plot).coerceIn(plot.left + halfWidth, plot.right - halfWidth)
            val baseY = (zeroY + markerHeight).coerceAtMost(plot.bottom - 1f.dp)
            canvas.drawPath(roundedUpTriangle(x, baseY, halfWidth, markerHeight, 1.6f.dp), fillPaint)
        }
    }

    private val Float.dp get() = this * density
}

internal data class ToolkitMetabolicRange(val minimum: Double, val maximum: Double) {
    val zeroRatio: Double
        get() = ((0.0 - minimum) / (maximum - minimum).coerceAtLeast(0.000001)).coerceIn(0.01, 0.95)
}

internal fun toolkitMetabolicRange(
    values: List<Double>,
    sharedZeroRatio: Double? = null,
): ToolkitMetabolicRange {
    val referenceMaximum = values.filter { it.isFinite() && it >= 0.0 }.maxOrNull() ?: 0.0
    val maximum = max(0.01, referenceMaximum * 2.0 - TOOLKIT_SECONDARY_MINIMUM)
    val minimum = sharedZeroRatio?.let { toolkitMinimumForZeroRatio(maximum, it) }
        ?: TOOLKIT_SECONDARY_MINIMUM
    return ToolkitMetabolicRange(minimum, maximum)
}

internal fun toolkitMinimumForZeroRatio(maximum: Double, zeroRatio: Double): Double {
    val ratio = zeroRatio.coerceIn(0.01, 0.95)
    return -(ratio * maximum.coerceAtLeast(0.000001)) / (1.0 - ratio).coerceAtLeast(0.01)
}

internal fun toolkitSmbMarkerSide(units: Double): Float = when {
    kotlin.math.abs(units) <= 0.1 -> 9f
    kotlin.math.abs(units) < 0.5 -> 12f
    else -> 15f
}

internal fun glucoseLogRatio(valueMgDl: Double): Double {
    val safe = valueMgDl.coerceIn(GLUCOSE_LOG_MIN, GLUCOSE_LOG_MAX)
    return (
        ln(safe / GLUCOSE_LOG_MIN) /
            ln(GLUCOSE_LOG_MAX / GLUCOSE_LOG_MIN)
        ).coerceIn(0.0, 1.0)
}

private fun mapGlucoseY(valueMgDl: Double, plot: RectF): Float =
    plot.bottom - glucoseLogRatio(valueMgDl).toFloat() * plot.height()

private fun windowedStepSamples(points: List<TherapyHistorySample>, start: Long, end: Long): List<TherapyHistorySample> {
    if (points.isEmpty()) return emptyList()
    val seed = points.lastOrNull { it.measuredAtEpochMs <= start }
    val visible = points.filter { it.measuredAtEpochMs in (start + 1)..end }
    val combined = buildList {
        seed?.let { add(it.copy(measuredAtEpochMs = start)) }
        addAll(visible)
    }.toMutableList()
    if (combined.isEmpty()) return emptyList()
    if (combined.first().measuredAtEpochMs > start) combined.add(0, combined.first().copy(measuredAtEpochMs = start))
    if (combined.last().measuredAtEpochMs < end) combined += combined.last().copy(measuredAtEpochMs = end)
    return combined
}

private fun effectiveBasal(sample: TherapyHistorySample): Double =
    sample.tempBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: sample.baseBasalUnitsPerHour ?: 0.0

private fun stepPath(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    plot: RectF,
    mapValue: (Double) -> Float,
    closeAt: Float? = null,
): Path = Path().apply {
    if (values.isEmpty()) return@apply
    val firstX = mapX(values.first().first, start, end, plot)
    if (closeAt != null) moveTo(firstX, closeAt) else moveTo(firstX, mapValue(values.first().second))
    if (closeAt != null) lineTo(firstX, mapValue(values.first().second))
    var priorY = mapValue(values.first().second)
    values.drop(1).forEach { (time, value) ->
        val x = mapX(time, start, end, plot)
        lineTo(x, priorY)
        priorY = mapValue(value)
        lineTo(x, priorY)
    }
    if (closeAt != null) {
        lineTo(mapX(values.last().first, start, end, plot), closeAt)
        close()
    }
}

private fun valuePath(
    values: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    plot: RectF,
    mapValue: (Double) -> Float,
): Path = Path().apply {
    values.forEachIndexed { index, (time, value) ->
        val x = mapX(time, start, end, plot)
        val y = mapValue(value)
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
}

private fun buildActivityProjection(last: Pair<Long, Double>, projectionStart: Long, end: Long): List<Pair<Long, Double>> {
    if (end <= projectionStart || last.second <= 0.0) return emptyList()
    val duration = 3L * HOUR_MS
    return buildList {
        var time = projectionStart
        while (time <= min(end, projectionStart + duration)) {
            val elapsed = (time - projectionStart).toDouble() / duration
            add(time to last.second * (1.0 - elapsed).coerceAtLeast(0.0).pow(2.0))
            time += 5 * 60_000L
        }
    }
}

private fun roundedUpTriangle(cx: Float, baseY: Float, halfWidth: Float, height: Float, radius: Float): Path =
    Path().apply {
        val apexY = baseY - height
        moveTo(cx - halfWidth + radius, baseY)
        lineTo(cx + halfWidth - radius, baseY)
        quadTo(cx + halfWidth, baseY, cx + halfWidth - radius * 0.7f, baseY - radius)
        lineTo(cx + radius * 0.7f, apexY + radius)
        quadTo(cx, apexY, cx - radius * 0.7f, apexY + radius)
        lineTo(cx - halfWidth + radius * 0.7f, baseY - radius)
        quadTo(cx - halfWidth, baseY, cx - halfWidth + radius, baseY)
        close()
    }

private fun mapX(time: Long, start: Long, end: Long, plot: RectF): Float =
    plot.left + ((time - start).toDouble() / (end - start).coerceAtLeast(1L) * plot.width()).toFloat()

private fun mapY(value: Double, minValue: Double, maxValue: Double, plot: RectF): Float =
    plot.bottom - ((value - minValue) / (maxValue - minValue).coerceAtLeast(0.0001))
        .toFloat().coerceIn(0f, 1f) * plot.height()

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

private fun View.drawText(canvas: Canvas, value: String, x: Float, y: Float, sizeSp: Float, color: Int, align: Paint.Align) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
        this.color = color
        textAlign = align
    }
    canvas.drawText(value, x, y, paint)
}
