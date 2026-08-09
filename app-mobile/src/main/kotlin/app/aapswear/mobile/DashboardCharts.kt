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
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val HOUR_MS = 60L * 60_000L

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
                    val horizontalGesture = kotlin.math.abs(event.x - downX) > kotlin.math.abs(event.y - downY) + 4f
                    if (horizontalGesture && kotlin.math.abs(delta) > 0.4f) {
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val outer = RectF(0.5f.dp, 0.5f.dp, width - 0.5f.dp, height - 0.5f.dp)
        if (outer.width() <= 24f || outer.height() <= 24f) return
        val radius = 16f.dp
        val clip = Path().apply { addRoundRect(outer, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
        canvas.drawRoundRect(outer, radius, radius, fillPaint)

        val plot = RectF(9f.dp, 15f.dp, width - 38f.dp, height - 24f.dp)
        val now = System.currentTimeMillis()
        val targetLow = state?.target?.lowMgDl ?: 80.0
        val targetHigh = state?.target?.highMgDl ?: 160.0
        val predictions = if (showPredictions) state?.glucosePredictions.orEmpty() else emptyList()
        val predictionHorizon = predictions.flatMap { it.samples }
            .maxOfOrNull { it.measuredAtEpochMs }?.minus(now)?.coerceAtLeast(0L) ?: 0L
        val anchorEnd = now + min(predictionHorizon, 2L * HOUR_MS) + viewport.panMs
        val start = anchorEnd - (viewport.hours * HOUR_MS).toLong()
        val end = anchorEnd.coerceAtLeast(start + 60_000L)

        val history = buildList {
            addAll(state?.glucoseHistory.orEmpty())
            state?.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs, state!!.source)) }
        }.distinctBy { it.measuredAtEpochMs to it.source }.sortedBy { it.measuredAtEpochMs }
            .filter { it.measuredAtEpochMs in start..min(end, now) }
        val visiblePredictions = predictions.map { series ->
            series.copy(samples = series.samples.filter { it.measuredAtEpochMs > now && it.measuredAtEpochMs in start..end })
        }.filter { it.samples.isNotEmpty() }
        val values = history.map { it.valueMgDl } + visiblePredictions.flatMap { it.samples }.map { it.valueMgDl } + targetLow + targetHigh
        val yMin = max(20.0, min(targetLow * 0.70, (values.minOrNull() ?: targetLow) * 0.84))
        val yMax = ceil(max(targetHigh * 1.28, (values.maxOrNull() ?: targetHigh) * 1.12) / 10.0) * 10.0

        val targetTop = mapYLog(targetHigh, yMin, yMax, plot)
        val targetBottom = mapYLog(targetLow, yMin, yMax, plot)
        fillPaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.RANGE_IN_RANGE), 40)
        canvas.drawRect(plot.left, targetTop, plot.right, targetBottom, fillPaint)
        drawGrid(canvas, plot, start, end)
        drawBasal(canvas, plot, start, end, state?.therapyHistory.orEmpty())
        drawInsulinActivity(canvas, RectF(plot.left, targetTop, plot.right, targetBottom), start, end, state?.therapyHistory.orEmpty())

        val dividerX = mapX(now, start, end, plot).coerceIn(plot.left, plot.right)
        if (visiblePredictions.isNotEmpty() && now in start..end) {
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_DIVIDER)
            linePaint.strokeWidth = 1f.dp
            linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
            canvas.drawLine(dividerX, plot.top, dividerX, plot.bottom, linePaint)
            linePaint.pathEffect = null
        }

        history.forEach { point ->
            val x = min(mapX(point.measuredAtEpochMs, start, end, plot), dividerX - 2f.dp)
            val y = mapYLog(point.valueMgDl, yMin, yMax, plot)
            fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)
            canvas.drawCircle(x, y, 3.4f.dp, fillPaint)
            fillPaint.color = dotColor(point.valueMgDl, targetLow, targetHigh)
            canvas.drawCircle(x, y, 2.45f.dp, fillPaint)
        }
        history.lastOrNull()?.let { point ->
            val x = min(mapX(point.measuredAtEpochMs, start, end, plot), dividerX - 2f.dp)
            val y = mapYLog(point.valueMgDl, yMin, yMax, plot)
            linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)
            linePaint.strokeWidth = 1.5f.dp
            canvas.drawCircle(x, y, 4.6f.dp, linePaint)
        }
        visiblePredictions.forEach { drawPrediction(canvas, it, plot, start, end, yMin, yMax, dividerX) }

        drawTargetLabel(canvas, glucoseLabel(targetHigh), plot.right + 5f.dp, targetTop + 3f.dp)
        drawTargetLabel(canvas, glucoseLabel(targetLow), plot.right + 5f.dp, targetBottom + 3f.dp)
        if (history.size < 2) drawText(canvas, "Noch kein Verlauf", plot.centerX(), plot.centerY(), 10f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED), Paint.Align.CENTER)
        }

        linePaint.style = Paint.Style.STROKE
        linePaint.pathEffect = null
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
        linePaint.strokeWidth = 1f.dp
        canvas.drawRoundRect(outer, radius, radius, linePaint)
    }

    private fun drawGrid(canvas: Canvas, plot: RectF, start: Long, end: Long) {
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_GRID)
        linePaint.strokeWidth = 0.7f.dp
        linePaint.pathEffect = DashPathEffect(floatArrayOf(3f.dp, 3f.dp), 0f)
        repeat(5) { index ->
            val y = plot.top + plot.height() * index / 4f
            canvas.drawLine(plot.left, y, plot.right, y, linePaint)
        }
        repeat(4) { index ->
            val x = plot.left + plot.width() * index / 3f
            canvas.drawLine(x, plot.top, x, plot.bottom, linePaint)
            drawText(canvas, timeFormat.format(Date(start + (end - start) * index / 3)), x, plot.bottom + 15f.dp, 8.5f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), Paint.Align.CENTER)
        }
        linePaint.pathEffect = null
    }

    private fun drawBasal(canvas: Canvas, plot: RectF, start: Long, end: Long, points: List<TherapyHistorySample>) {
        val values = points.filter { it.measuredAtEpochMs in start..end && (it.baseBasalUnitsPerHour != null || it.basalUnitsPerHour != null) }
        if (values.size < 2) return
        val baseValues = values.mapNotNull { it.baseBasalUnitsPerHour ?: it.basalUnitsPerHour }
        val tempValues = values.mapNotNull { it.tempBasalUnitsPerHour ?: it.basalUnitsPerHour }
        val maxBasal = max(0.1, (baseValues + tempValues).maxOrNull() ?: 0.1)
        val laneHeight = plot.height() * 0.24f
        fun basalY(value: Double) = plot.top + (value / maxBasal).coerceIn(0.0, 1.0).toFloat() * laneHeight
        val cyan = SugarliciousColors.argb(SugarliciousColorRole.SECONDARY)

        val fill = Path()
        val firstX = mapX(values.first().measuredAtEpochMs, start, end, plot)
        fill.moveTo(firstX, plot.top)
        values.forEach { sample ->
            val x = mapX(sample.measuredAtEpochMs, start, end, plot)
            val effective = sample.tempBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: sample.baseBasalUnitsPerHour ?: return@forEach
            fill.lineTo(x, basalY(effective))
        }
        fill.lineTo(mapX(values.last().measuredAtEpochMs, start, end, plot), plot.top)
        fill.close()
        fillPaint.shader = LinearGradient(0f, plot.top, 0f, plot.top + laneHeight, withAlpha(cyan, 8), withAlpha(cyan, 120), Shader.TileMode.CLAMP)
        canvas.drawPath(fill, fillPaint)
        fillPaint.shader = null

        val tempPath = Path()
        values.forEachIndexed { index, sample ->
            val x = mapX(sample.measuredAtEpochMs, start, end, plot)
            val y = basalY(sample.tempBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: sample.baseBasalUnitsPerHour ?: 0.0)
            if (index == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
        }
        linePaint.color = cyan
        linePaint.strokeWidth = 1.6f.dp
        canvas.drawPath(tempPath, linePaint)

        val basePath = Path()
        values.forEachIndexed { index, sample ->
            val x = mapX(sample.measuredAtEpochMs, start, end, plot)
            val y = basalY(sample.baseBasalUnitsPerHour ?: sample.basalUnitsPerHour ?: 0.0)
            if (index == 0) basePath.moveTo(x, y) else basePath.lineTo(x, y)
        }
        linePaint.strokeWidth = 1f.dp
        linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 3f.dp), 0f)
        canvas.drawPath(basePath, linePaint)
        linePaint.pathEffect = null
    }

    private fun drawInsulinActivity(canvas: Canvas, band: RectF, start: Long, end: Long, points: List<TherapyHistorySample>) {
        val activity = points.mapNotNull { point ->
            point.insulinActivityUnitsPerMinute?.let { point.measuredAtEpochMs to it }
        }.filter { it.first in start..end }
        if (activity.size < 2) return
        val maxActivity = max(0.001, activity.maxOf { it.second })
        val path = Path()
        activity.forEachIndexed { index, (time, value) ->
            val x = mapX(time, start, end, band)
            val fraction = (ln(1.0 + value * 1000.0) / ln(1.0 + maxActivity * 1000.0)).toFloat()
            val y = band.bottom - fraction * band.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_IOB)
        linePaint.strokeWidth = 1.35f.dp
        canvas.drawPath(path, linePaint)
    }

    private fun drawPrediction(canvas: Canvas, series: GlucosePrediction, plot: RectF, start: Long, end: Long, yMin: Double, yMax: Double, dividerX: Float) {
        val color = when (series.kind) {
            PredictionKind.IOB -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_IOB)
            PredictionKind.COB, PredictionKind.ACOB -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_COB)
            PredictionKind.UAM -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_UAM)
            PredictionKind.ZERO_TEMP -> SugarliciousColors.argb(SugarliciousColorRole.PREDICTION_ZERO_TEMP)
        }
        series.samples.forEach { point ->
            val x = max(mapX(point.measuredAtEpochMs, start, end, plot), dividerX + 3f.dp)
            if (x > plot.right) return@forEach
            fillPaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE), 190)
            canvas.drawCircle(x, mapYLog(point.valueMgDl, yMin, yMax, plot), 2.45f.dp, fillPaint)
            fillPaint.color = color
            canvas.drawCircle(x, mapYLog(point.valueMgDl, yMin, yMax, plot), 1.75f.dp, fillPaint)
        }
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
        drawText(canvas, value, x, y, 9f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), Paint.Align.LEFT)

    private val Float.dp get() = this * density
}

@SuppressLint("DrawAllocation")
internal class MetabolicDashboardChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : InteractiveChartView(context, attrs, 6) {
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val radius = 16f.dp
        val clip = Path().apply { addRoundRect(outer, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
        fillPaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
        canvas.drawRoundRect(outer, radius, radius, fillPaint)
        val now = System.currentTimeMillis() + viewport.panMs
        val start = now - (viewport.hours * HOUR_MS).toLong()
        val points = state?.therapyHistory.orEmpty().filter { it.measuredAtEpochMs in start..now }
        val left = 9f.dp; val right = width - 31f.dp; val top = 18f.dp; val bottom = height - 23f.dp; val gap = 18f.dp
        val half = (bottom - top - gap) / 2f
        drawLane(canvas, RectF(left, top, right, top + half), points, start, now, true)
        drawLane(canvas, RectF(left, top + half + gap, right, bottom), points, start, now, false)
        repeat(4) { index ->
            val x = left + (right - left) * index / 3f
            drawText(canvas, timeFormat.format(Date(start + (now - start) * index / 3)), x, bottom + 15f.dp, 8.5f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL), Paint.Align.CENTER)
        }
        if (points.size < 2) drawText(canvas, "Noch kein IOB/COB-Verlauf", (left + right) / 2f, (top + bottom) / 2f, 10f, SugarliciousColors.argb(SugarliciousColorRole.GRAPH_MUTED), Paint.Align.CENTER)
        }
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
        linePaint.strokeWidth = 1f.dp
        canvas.drawRoundRect(outer, radius, radius, linePaint)
    }

    private fun drawLane(canvas: Canvas, plot: RectF, points: List<TherapyHistorySample>, start: Long, end: Long, iob: Boolean) {
        val values = points.mapNotNull { if (iob) it.totalIob else it.cobGrams }
        val maxValue = max(if (iob) 1.0 else 10.0, (values.maxOrNull() ?: 0.0) * 1.18)
        val color = SugarliciousColors.argb(if (iob) SugarliciousColorRole.GRAPH_IOB else SugarliciousColorRole.GRAPH_COB)
        drawText(canvas, if (iob) "IOB (IE)" else "COB (g)", plot.left, plot.top - 6f.dp, 9f, color, Paint.Align.LEFT)
        linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_GRID)
        linePaint.strokeWidth = 0.7f.dp
        linePaint.pathEffect = DashPathEffect(floatArrayOf(3f.dp, 3f.dp), 0f)
        repeat(3) { index -> canvas.drawLine(plot.left, plot.top + plot.height() * index / 2f, plot.right, plot.top + plot.height() * index / 2f, linePaint) }
        linePaint.pathEffect = null
        val actual = points.mapNotNull { point -> (if (iob) point.totalIob else point.cobGrams)?.let { point.measuredAtEpochMs to it } }
        if (actual.isEmpty()) return
        fun y(value: Double): Float {
            val scaled = (ln(1.0 + value.coerceAtLeast(0.0)) / ln(1.0 + maxValue)).toFloat()
            return plot.bottom - scaled * plot.height()
        }
        val area = Path().apply {
            moveTo(mapX(actual.first().first, start, end, plot), plot.bottom)
            actual.forEach { (time, value) -> lineTo(mapX(time, start, end, plot), y(value)) }
            lineTo(mapX(actual.last().first, start, end, plot), plot.bottom)
            close()
        }
        fillPaint.shader = LinearGradient(0f, plot.top, 0f, plot.bottom, withAlpha(color, 150), withAlpha(color, 10), Shader.TileMode.CLAMP)
        canvas.drawPath(area, fillPaint)
        fillPaint.shader = null
        val line = Path()
        actual.forEachIndexed { index, (time, value) -> if (index == 0) line.moveTo(mapX(time, start, end, plot), y(value)) else line.lineTo(mapX(time, start, end, plot), y(value)) }
        linePaint.color = color
        linePaint.strokeWidth = 1.8f.dp
        canvas.drawPath(line, linePaint)
    }

    private val Float.dp get() = this * density
}

private fun mapX(time: Long, start: Long, end: Long, plot: RectF): Float =
    plot.left + ((time - start).toDouble() / (end - start).coerceAtLeast(1L) * plot.width()).toFloat()

private fun mapYLog(value: Double, minValue: Double, maxValue: Double, plot: RectF): Float {
    val normalized = ln(1.0 + (value - minValue).coerceAtLeast(0.0)) /
        ln(1.0 + (maxValue - minValue).coerceAtLeast(1.0))
    return plot.bottom - normalized.toFloat().coerceIn(0f, 1f) * plot.height()
}

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
