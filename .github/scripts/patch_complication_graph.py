from pathlib import Path
import re

path = Path("complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt")
text = path.read_text(encoding="utf-8")
if "import android.graphics.DashPathEffect\n" not in text:
    text = text.replace("import android.graphics.Color\n", "import android.graphics.Color\nimport android.graphics.DashPathEffect\n", 1)
if "import kotlin.math.ln\n" not in text:
    text = text.replace("import kotlinx.coroutines.flow.first\n", "import kotlinx.coroutines.flow.first\nimport kotlin.math.ln\n", 1)

replacement = r'''    private fun drawGraphImage(
        canvas: Canvas,
        state: TherapyDisplayState?,
        height: Int,
        now: Long,
        windowMs: Long,
    ) {
        val width = canvas.width
        val glucose = state?.glucose
        val colors = readGraphColors()
        val graphStyle = readGraphStyle()
        val targetLow = state?.target?.lowMgDl ?: DISPLAY_LOW_MGDL
        val targetHigh = state?.target?.highMgDl ?: DISPLAY_HIGH_MGDL
        val density = resources.displayMetrics.density
        val plotLeft = 1f
        val plotRight = width - 1f
        val plotTop = 1f
        val plotBottom = height - 1f
        val plotHeight = plotBottom - plotTop

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.graphBackground
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 22f, 22f, backgroundPaint)

        fun glucoseRatio(valueMgDl: Double): Double {
            val value = valueMgDl.coerceIn(0.0, 400.0)
            return when {
                value <= 80.0 ->
                    0.055 + value / 80.0 * (0.215 - 0.055)
                value <= 160.0 ->
                    0.215 + (ln(value / 80.0) / ln(2.0)) * (0.515 - 0.215)
                else ->
                    0.515 + (ln(value / 160.0) / ln(400.0 / 160.0)) * (1.0 - 0.515)
            }.coerceIn(0.055, 1.0)
        }

        fun yFor(valueMgDl: Double): Float =
            plotBottom - (glucoseRatio(valueMgDl) * plotHeight).toFloat()

        val targetTop = yFor(targetHigh)
        val targetBottom = yFor(targetLow)
        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.rangeInRange
            style = Paint.Style.FILL
        }
        canvas.drawRect(plotLeft, targetTop, plotRight, targetBottom, targetPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.divider
            style = Paint.Style.STROKE
            strokeWidth = 0.7f * density
        }
        canvas.drawLine(plotLeft, yFor(0.0), plotRight, yFor(0.0), gridPaint)
        gridPaint.pathEffect = DashPathEffect(floatArrayOf(3f * density, 3f * density), 0f)
        for (index in 1..3) {
            val y = plotTop + plotHeight * index / 4f
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
        }

        val cutoff = now - windowMs
        val gridIntervalMs = if (windowMs <= GRAPH_WINDOW_MS) 60L * 60_000L else 2L * 60L * 60_000L
        var tick = (cutoff / gridIntervalMs) * gridIntervalMs
        if (tick < cutoff) tick += gridIntervalMs
        while (tick <= now) {
            val fraction = ((tick - cutoff).toDouble() / windowMs.toDouble()).coerceIn(0.0, 1.0)
            val x = plotLeft + (fraction * (plotRight - plotLeft)).toFloat()
            canvas.drawLine(x, plotTop, x, plotBottom, gridPaint)
            tick += gridIntervalMs
        }
        gridPaint.pathEffect = null

        val merged = linkedMapOf<Long, GlucoseSample>()
        state?.glucoseHistory.orEmpty().forEach { merged[it.measuredAtEpochMs] = it }
        glucose?.let { merged[it.measuredAtEpochMs] = GlucoseSample(it.valueMgDl, it.measuredAtEpochMs) }
        val samples = merged.values.asSequence()
            .filter {
                it.measuredAtEpochMs in cutoff..(now + FUTURE_TOLERANCE_MS) &&
                    it.valueMgDl in 20.0..1000.0
            }
            .sortedBy { it.measuredAtEpochMs }
            .toList()

        if (samples.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.divider
                textAlign = Paint.Align.CENTER
                textSize = 26f
            }
            canvas.drawText("No history", width / 2f, (plotTop + plotBottom) / 2f, emptyPaint)
            return
        }

        fun xFor(timestamp: Long): Float {
            val fraction = ((timestamp - cutoff).toDouble() / windowMs.toDouble()).coerceIn(0.0, 1.0)
            return (plotLeft + (fraction * (plotRight - plotLeft)).toFloat()).coerceIn(
                plotLeft + 2f * density,
                plotRight - 2f * density,
            )
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = colors.outline
        }
        samples.forEachIndexed { index, sample ->
            dotPaint.color = when {
                sample.valueMgDl < targetLow -> colors.cgmLow
                sample.valueMgDl > targetHigh -> colors.cgmHigh
                else -> colors.cgmInRange
            }
            val dotRadius = (
                graphStyle.cgmDotRadiusDp.coerceIn(1.5f, 6.0f) +
                    if (index == samples.lastIndex) 0.1f else 0f
                ) * density
            val x = xFor(sample.measuredAtEpochMs)
            val y = yFor(sample.valueMgDl)
            canvas.drawCircle(x, y, dotRadius, dotPaint)
            if (graphStyle.cgmDotOutlineEnabled) {
                val outlineWidth = graphStyle.cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f) * density
                outlinePaint.strokeWidth = outlineWidth
                canvas.drawCircle(x, y, dotRadius + outlineWidth / 2f, outlinePaint)
            }
        }

        val targetLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.divider
            textAlign = Paint.Align.RIGHT
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            targetHigh.toInt().toString(),
            plotRight - 3f,
            (targetTop - 4f).coerceAtLeast(plotTop + 18f),
            targetLabelPaint,
        )
        canvas.drawText(
            targetLow.toInt().toString(),
            plotRight - 3f,
            (targetBottom + 18f).coerceAtMost(plotBottom - 4f),
            targetLabelPaint,
        )

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.divider
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawRoundRect(plotLeft, plotTop, plotRight, plotBottom, 22f, 22f, borderPaint)
    }

'''
pattern = r'    private fun drawGraphImage\([\s\S]*?(?=    private fun readGraphColors\(\): WatchGraphColors)'
text, count = re.subn(pattern, replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"expected one graph renderer, got {count}")
path.write_text("\n".join(line.rstrip() for line in text.splitlines()) + "\n", encoding="utf-8")
