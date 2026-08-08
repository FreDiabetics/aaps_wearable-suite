package app.aapswear.mobile

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
import android.view.View
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
import kotlin.math.max
import kotlin.math.min

class GlucoseDashboardChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
    }
    private val timeFormat = SimpleDateFormat("HH", Locale.getDefault())
    private var state: TherapyDisplayState? = null
    private var unit = GlucoseUnit.MG_DL
    private var showPredictions = true
    private var durationHours = 6

    fun bind(state: TherapyDisplayState?, unit: GlucoseUnit, showPredictions: Boolean, durationHours: Int) {
        this.state = state
        this.unit = unit
        this.showPredictions = showPredictions
        this.durationHours = durationHours.coerceIn(3, 24)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val plot = RectF(2f.dp, 16f.dp, width - 34f.dp, height - 24f.dp)
        if (plot.width() <= 20f || plot.height() <= 20f) return

        val now = System.currentTimeMillis()
        val history = buildList {
            addAll(state?.glucoseHistory.orEmpty())
            state?.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)) }
        }
            .associateBy { it.measuredAtEpochMs }
            .values
            .sortedBy { it.measuredAtEpochMs }

        val predictions = if (showPredictions) state?.glucosePredictions.orEmpty() else emptyList()
        val start = now - durationHours * 60L * 60_000L
        val predictionEnd = predictions.flatMap { it.samples }.maxOfOrNull { it.measuredAtEpochMs } ?: now
        val end = max(now, predictionEnd).coerceAtLeast(start + 60_000L)

        val visibleHistory = history.filter { it.measuredAtEpochMs in start..end }
        val visiblePredictions = predictions.map { series ->
            series.copy(samples = series.samples.filter { it.measuredAtEpochMs in start..end })
        }.filter { it.samples.isNotEmpty() }

        val dataValues = visibleHistory.map { it.valueMgDl } + visiblePredictions.flatMap { it.samples }.map { it.valueMgDl }
        val minimum = min(40.0, (dataValues.minOrNull() ?: 40.0)) - 10.0
        val maximum = max(200.0, (dataValues.maxOrNull() ?: 200.0)) + 10.0
        val yMin = (minimum / 20.0).toInt() * 20.0
        val yMax = ceil(maximum / 20.0) * 20.0

        val targetTop = mapY(DISPLAY_RANGE_HIGH_MGDL, yMin, yMax, plot)
        val targetBottom = mapY(DISPLAY_RANGE_LOW_MGDL, yMin, yMax, plot)
        fillPaint.color = Color.rgb(10, 57, 28)
        canvas.drawRect(plot.left, targetTop, plot.right, targetBottom, fillPaint)

        drawGrid(canvas, plot, start, end, yMin, yMax)

        val dividerX = mapX(now, start, end, plot)
        if (visiblePredictions.isNotEmpty()) {
            linePaint.color = Color.argb(170, 125, 125, 125)
            linePaint.strokeWidth = 1.0f.dp
            linePaint.pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
            canvas.drawLine(dividerX, plot.top, dividerX, plot.bottom, linePaint)
            linePaint.pathEffect = null
        }

        drawHistoryDots(canvas, visibleHistory, plot, start, end, yMin, yMax, dividerX, visiblePredictions.isNotEmpty())
        drawCurrentDot(canvas, visibleHistory.lastOrNull(), plot, start, end, yMin, yMax, dividerX, visiblePredictions.isNotEmpty())

        visiblePredictions.forEach { series ->
            val color = when (series.kind) {
                PredictionKind.IOB -> Color.rgb(82, 193, 255)
                PredictionKind.COB, PredictionKind.ACOB -> Color.rgb(244, 222, 0)
                PredictionKind.UAM -> Color.rgb(255, 174, 31)
                PredictionKind.ZERO_TEMP -> Color.rgb(48, 219, 222)
            }
            drawPredictionDots(
                canvas,
                series,
                plot,
                start,
                end,
                yMin,
                yMax,
                dividerX,
                color,
            )
        }

        if (visibleHistory.size < 2) {
            drawEmptyMessage(canvas, plot, "Verlauf baut sich aus AAPS-Empfangsdaten auf")
        }
    }

    private fun drawGrid(canvas: Canvas, plot: RectF, start: Long, end: Long, yMin: Double, yMax: Double) {
        linePaint.color = Color.rgb(70, 70, 70)
        linePaint.strokeWidth = 0.8f.dp
        linePaint.pathEffect = DashPathEffect(floatArrayOf(3f.dp, 3f.dp), 0f)

        repeat(5) { index ->
            val y = plot.top + plot.height() * index / 4f
            canvas.drawLine(plot.left, y, plot.right, y, linePaint)
            val value = yMax - (yMax - yMin) * index / 4.0
            drawText(canvas, glucoseLabel(value), plot.right + 5f.dp, y + 3f.dp, 9f, Color.rgb(210, 210, 210), Paint.Align.LEFT)
        }

        repeat(4) { index ->
            val x = plot.left + plot.width() * index / 3f
            canvas.drawLine(x, plot.top, x, plot.bottom, linePaint)
            val time = start + (end - start) * index / 3
            drawText(canvas, timeFormat.format(Date(time)), x, plot.bottom + 15f.dp, 9f, Color.rgb(200, 200, 200), Paint.Align.CENTER)
        }

        linePaint.pathEffect = null
    }

    private fun drawHistoryDots(
        canvas: Canvas,
        points: List<GlucoseSample>,
        plot: RectF,
        start: Long,
        end: Long,
        yMin: Double,
        yMax: Double,
        dividerX: Float,
        pinCurrent: Boolean,
    ) {
        points.forEachIndexed { index, point ->
            val rawX = mapX(point.measuredAtEpochMs, start, end, plot)
            val x = if (pinCurrent && index == points.lastIndex) min(rawX, dividerX - 4f.dp) else rawX
            val y = mapY(point.valueMgDl, yMin, yMax, plot)
            fillPaint.color = glucoseDotColor(point.valueMgDl)
            canvas.drawCircle(x, y, 2.7f.dp, fillPaint)
        }
    }

    private fun drawCurrentDot(
        canvas: Canvas,
        point: GlucoseSample?,
        plot: RectF,
        start: Long,
        end: Long,
        yMin: Double,
        yMax: Double,
        dividerX: Float,
        pinCurrent: Boolean,
    ) {
        point ?: return
        val rawX = mapX(point.measuredAtEpochMs, start, end, plot)
        val x = if (pinCurrent) min(rawX, dividerX - 4f.dp) else rawX
        val y = mapY(point.valueMgDl, yMin, yMax, plot)

        fillPaint.color = Color.BLACK
        canvas.drawCircle(x, y, 4.8f.dp, fillPaint)

        fillPaint.color = glucoseDotColor(point.valueMgDl)
        canvas.drawCircle(x, y, 3.3f.dp, fillPaint)
    }

    private fun drawPredictionDots(
        canvas: Canvas,
        series: GlucosePrediction,
        plot: RectF,
        start: Long,
        end: Long,
        yMin: Double,
        yMax: Double,
        dividerX: Float,
        color: Int,
    ) {
        series.samples.forEachIndexed { index, point ->
            val rawX = mapX(point.measuredAtEpochMs, start, end, plot)
            val x =
                if (index == 0) {
                    max(rawX, dividerX + 4f.dp)
                } else {
                    rawX
                }
            val y = mapY(point.valueMgDl, yMin, yMax, plot)

            fillPaint.color = color
            canvas.drawCircle(x, y, 1.9f.dp, fillPaint)
        }
    }

    private fun glucoseDotColor(valueMgDl: Double): Int = when {
        valueMgDl < DISPLAY_RANGE_LOW_MGDL -> Color.rgb(255, 92, 105)
        valueMgDl > DISPLAY_RANGE_HIGH_MGDL -> Color.rgb(255, 208, 64)
        else -> Color.WHITE
    }

    private fun glucoseLabel(valueMgDl: Double): String =
        if (unit == GlucoseUnit.MMOL_L) {
            String.format(Locale.getDefault(), "%.1f", valueMgDl / 18.0)
        } else {
            valueMgDl.toInt().toString()
        }

    private fun drawEmptyMessage(canvas: Canvas, plot: RectF, message: String) =
        drawText(canvas, message, plot.centerX(), plot.centerY(), 10f, Color.rgb(150, 150, 150), Paint.Align.CENTER)

    private fun mapX(time: Long, start: Long, end: Long, plot: RectF) =
        plot.left + ((time - start).toDouble() / (end - start).coerceAtLeast(1L) * plot.width()).toFloat()

    private fun mapY(value: Double, min: Double, max: Double, plot: RectF) =
        plot.bottom - ((value - min) / (max - min).coerceAtLeast(1.0) * plot.height()).toFloat()

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        sizeSp: Float,
        color: Int,
        align: Paint.Align,
    ) {
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
        textPaint.color = color
        textPaint.textAlign = align
        canvas.drawText(value, x, y, textPaint)
    }

    private companion object {
        const val DISPLAY_RANGE_LOW_MGDL = 80.0
        const val DISPLAY_RANGE_HIGH_MGDL = 160.0
    }

    private val Float.dp get() = this * density
}

class MetabolicDashboardChart @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timeFormat = SimpleDateFormat("HH", Locale.getDefault())
    private var state: TherapyDisplayState? = null
    private var durationHours = 6

    fun bind(state: TherapyDisplayState?, durationHours: Int) { this.state = state; this.durationHours = durationHours.coerceIn(3, 24); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis(); val start = now - durationHours * 60L * 60_000L
        val points = state?.therapyHistory.orEmpty().filter { it.measuredAtEpochMs in start..now }
        val left = 2f.dp; val right = width - 28f.dp; val top = 18f.dp; val bottom = height - 22f.dp; val gap = 18f.dp
        val half = (bottom - top - gap) / 2f
        val iobPlot = RectF(left, top, right, top + half)
        val cobPlot = RectF(left, top + half + gap, right, bottom)
        drawLane(canvas, iobPlot, points, start, now, true)
        drawLane(canvas, cobPlot, points, start, now, false)
        repeat(4) { index ->
            val x = left + (right - left) * index / 3f
            drawText(canvas, timeFormat.format(Date(start + (now - start) * index / 3)), x, bottom + 15f.dp, 9f, Color.rgb(200, 200, 200), Paint.Align.CENTER)
        }
        if (points.size < 2) drawText(canvas, "IOB/COB-Verlauf baut sich lokal auf", (left + right) / 2f, (top + bottom) / 2f, 10f, Color.rgb(150, 150, 150), Paint.Align.CENTER)
    }

    private fun drawLane(canvas: Canvas, plot: RectF, points: List<TherapyHistorySample>, start: Long, end: Long, iob: Boolean) {
        val values = points.mapNotNull { if (iob) it.totalIob else it.cobGrams }
        val maxValue = max(if (iob) 1.0 else 10.0, (values.maxOrNull() ?: 0.0) * 1.18)
        val color = if (iob) Color.rgb(100, 191, 255) else Color.rgb(255, 157, 24)
        val title = if (iob) "IOB (IE)" else "COB (g)"
        drawText(canvas, title, plot.left, plot.top - 6f.dp, 9f, color, Paint.Align.LEFT)
        linePaint.color = Color.rgb(70, 70, 70); linePaint.strokeWidth = 0.7f.dp; linePaint.pathEffect = DashPathEffect(floatArrayOf(3f.dp, 3f.dp), 0f)
        repeat(3) { index ->
            val y = plot.top + plot.height() * index / 2f
            canvas.drawLine(plot.left, y, plot.right, y, linePaint)
            drawText(canvas, format(maxValue * (2 - index) / 2.0, if (iob) 1 else 0), plot.right + 5f.dp, y + 3f.dp, 8f, Color.rgb(210, 210, 210), Paint.Align.LEFT)
        }
        linePaint.pathEffect = null
        val actual = points.mapNotNull { point -> (if (iob) point.totalIob else point.cobGrams)?.let { point.measuredAtEpochMs to it } }
        if (actual.isEmpty()) return
        val path = Path(); val first = actual.first(); val firstX = mapX(first.first, start, end, plot); path.moveTo(firstX, plot.bottom)
        actual.forEach { (time, value) -> path.lineTo(mapX(time, start, end, plot), mapY(value, maxValue, plot)) }
        val lastX = mapX(actual.last().first, start, end, plot); path.lineTo(lastX, plot.bottom); path.close()
        fillPaint.shader = LinearGradient(0f, plot.top, 0f, plot.bottom, Color.argb(145, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(12, Color.red(color), Color.green(color), Color.blue(color)), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fillPaint); fillPaint.shader = null
        val line = Path(); actual.forEachIndexed { index, (time, value) -> val x = mapX(time, start, end, plot); val y = mapY(value, maxValue, plot); if (index == 0) line.moveTo(x, y) else line.lineTo(x, y) }
        linePaint.color = color; linePaint.strokeWidth = 1.8f.dp; canvas.drawPath(line, linePaint)
    }

    private fun mapX(time: Long, start: Long, end: Long, plot: RectF) = plot.left + ((time - start).toDouble() / (end - start).coerceAtLeast(1L) * plot.width()).toFloat()
    private fun mapY(value: Double, max: Double, plot: RectF) = plot.bottom - (value.coerceIn(0.0, max) / max * plot.height()).toFloat()
    private fun format(value: Double, digits: Int) = String.format(Locale.getDefault(), "%.${digits}f", value)
    private fun drawText(canvas: Canvas, value: String, x: Float, y: Float, sizeSp: Float, color: Int, align: Paint.Align) { textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics); textPaint.color = color; textPaint.textAlign = align; canvas.drawText(value, x, y, textPaint) }
    private val Float.dp get() = this * density
}
