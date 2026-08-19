package app.aapswear.complications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.aapswear.model.DataSourceId
import app.aapswear.model.Freshness
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TargetState
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

internal data class G6StyleHeaderPresentation(
    val text: String,
    val title: String,
)

internal data class G6StyleStatusPresentation(
    val text: String,
    val title: String,
)

internal object G6StylePresentationFormatter {
    fun header(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): G6StyleHeaderPresentation {
        val freshness = TherapyDisplayFormatter.freshness(state, nowEpochMs)
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, nowEpochMs)) {
            return G6StyleHeaderPresentation(
                text = DASH,
                title = TherapyDisplayFormatter.freshnessLabel(freshness),
            )
        }

        val glucose = state?.glucose
            ?: return G6StyleHeaderPresentation(DASH, TherapyDisplayFormatter.freshnessLabel(Freshness.NO_DATA))
        val arrow = TherapyDisplayFormatter.trendArrow(glucose.trend)
        val value = TherapyDisplayFormatter.glucose(glucose)
        return G6StyleHeaderPresentation(
            text = listOf(value, arrow).filter(String::isNotBlank).joinToString(" "),
            title = unitLabel(glucose.displayUnit),
        )
    }

    fun status(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): G6StyleStatusPresentation {
        val freshness = TherapyDisplayFormatter.freshness(state, nowEpochMs)
        val age = TherapyDisplayFormatter.ageMinutes(state?.glucose?.measuredAtEpochMs, nowEpochMs)
        return G6StyleStatusPresentation(
            text = TherapyDisplayFormatter.sourceName(state?.source),
            title = "$age · ${TherapyDisplayFormatter.freshnessLabel(freshness)}",
        )
    }

    fun samples(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): List<GlucoseSample> {
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, nowEpochMs)) return emptyList()
        val cutoff = nowEpochMs - GRAPH_WINDOW_MS
        val merged = linkedMapOf<Long, GlucoseSample>()
        state?.glucoseHistory.orEmpty().forEach { sample ->
            if (
                sample.measuredAtEpochMs in cutoff..(nowEpochMs + FUTURE_TOLERANCE_MS) &&
                sample.valueMgDl in 20.0..1000.0
            ) {
                merged[sample.measuredAtEpochMs] = sample
            }
        }
        state?.glucose?.let { glucose ->
            if (glucose.measuredAtEpochMs in cutoff..(nowEpochMs + FUTURE_TOLERANCE_MS)) {
                merged[glucose.measuredAtEpochMs] = GlucoseSample(glucose.valueMgDl, glucose.measuredAtEpochMs)
            }
        }
        return merged.values.sortedBy(GlucoseSample::measuredAtEpochMs)
    }

    private fun unitLabel(unit: GlucoseUnit): String =
        if (unit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"

    internal const val GRAPH_WINDOW_MS = 3L * 60L * 60_000L
    private const val FUTURE_TOLERANCE_MS = 5L * 60_000L
    private const val DASH = "—"
}

private abstract class G6StyleComplicationService : SuspendingComplicationDataSourceService() {
    final override fun getPreviewData(type: ComplicationType): ComplicationData =
        build(previewState(), System.currentTimeMillis())

    final override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val phoneState = TherapyStateStore(this).state.first()
        val resolved = G7LocalReadingResolver.resolve(this, phoneState)
        return build(resolved, System.currentTimeMillis())
    }

    protected abstract fun build(
        state: TherapyDisplayState?,
        nowEpochMs: Long,
    ): ComplicationData

    private fun previewState(): TherapyDisplayState {
        val now = System.currentTimeMillis()
        val history = (0..36).map { index ->
            val minutesAgo = (36 - index) * 5L
            val value = when {
                index < 8 -> 105.0 + index * 1.4
                index < 20 -> 116.0 + (index - 8) * 0.6
                index < 27 -> 123.0 + (index - 20) * 3.0
                else -> 144.0 + (index - 27) * 0.9
            }
            GlucoseSample(valueMgDl = value, measuredAtEpochMs = now - minutesAgo * 60_000L)
        }
        return TherapyDisplayState(
            source = DataSourceId.DEXCOM_G7_WATCH,
            sourceVersion = "G7 Watch Collector",
            receivedAtEpochMs = now,
            glucose = GlucoseState(
                valueMgDl = 152.0,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.FLAT,
                measuredAtEpochMs = now - 2 * 60_000L,
                deltaMgDl = 1.0,
            ),
            glucoseHistory = history,
            target = TargetState(lowMgDl = 70.0, highMgDl = 180.0),
        )
    }
}

class G6StyleHeaderComplication : G6StyleComplicationService() {
    override fun build(state: TherapyDisplayState?, nowEpochMs: Long): ComplicationData {
        val presentation = G6StylePresentationFormatter.header(state, nowEpochMs)
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(presentation.text).build(),
            PlainComplicationText.Builder("Sugarlicious G6 Style ${presentation.text}").build(),
        ).setTitle(PlainComplicationText.Builder(presentation.title).build()).build()
    }
}

class G6StyleStatusComplication : G6StyleComplicationService() {
    override fun build(state: TherapyDisplayState?, nowEpochMs: Long): ComplicationData {
        val presentation = G6StylePresentationFormatter.status(state, nowEpochMs)
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(presentation.text).build(),
            PlainComplicationText.Builder("${presentation.text}, ${presentation.title}").build(),
        ).setTitle(PlainComplicationText.Builder(presentation.title).build()).build()
    }
}

class G6StyleGraphComplication : G6StyleComplicationService() {
    override fun build(state: TherapyDisplayState?, nowEpochMs: Long): ComplicationData {
        val bitmap = renderGraph(state, nowEpochMs)
        val icon = Icon.createWithBitmap(bitmap)
        val description = PlainComplicationText.Builder("3 Stunden Glukoseverlauf").build()
        return SmallImageComplicationData.Builder(
            SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
            description,
        ).build()
    }

    private fun renderGraph(state: TherapyDisplayState?, nowEpochMs: Long): Bitmap {
        // Match the WFF slot one-to-one instead of allocating a ~1 MB oversize bitmap every update.
        val width = 402
        val height = 157
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val plotLeft = 4f
        val plotRight = width - 4f
        val plotTop = 4f
        val plotBottom = height - 4f
        val targetLow = (state?.target?.lowMgDl ?: 70.0).coerceIn(40.0, 180.0)
        val targetHigh = (state?.target?.highMgDl ?: 180.0).coerceIn(targetLow + 1.0, 300.0)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_IN_RANGE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        fun yFor(valueMgDl: Double): Float {
            val fraction = ((valueMgDl.coerceIn(GRAPH_MIN, GRAPH_MAX) - GRAPH_MIN) / (GRAPH_MAX - GRAPH_MIN)).toFloat()
            return plotBottom - fraction * (plotBottom - plotTop)
        }

        val highPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_HIGH }
        canvas.drawRect(plotLeft, plotTop, plotRight, yFor(targetHigh), highPaint)
        val lowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_LOW }
        canvas.drawRect(plotLeft, yFor(targetLow), plotRight, plotBottom, lowPaint)

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIVIDER
            strokeWidth = 1f
        }
        canvas.drawLine(plotLeft, yFor(targetHigh), plotRight, yFor(targetHigh), divider)
        divider.color = COLOR_LOW_LINE
        divider.strokeWidth = 3f
        canvas.drawLine(plotLeft, yFor(targetLow), plotRight, yFor(targetLow), divider)

        val samples = G6StylePresentationFormatter.samples(state, nowEpochMs)
        val cutoff = nowEpochMs - G6StylePresentationFormatter.GRAPH_WINDOW_MS
        fun xFor(timestamp: Long): Float {
            val fraction = ((timestamp - cutoff).toDouble() / G6StylePresentationFormatter.GRAPH_WINDOW_MS.toDouble()).coerceIn(0.0, 1.0)
            return plotLeft + (fraction * (plotRight - plotLeft)).toFloat()
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        samples.forEach { sample ->
            canvas.drawCircle(xFor(sample.measuredAtEpochMs), yFor(sample.valueMgDl), 3.2f, dotPaint)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = 19f
        }
        canvas.drawText("3HR", 10f, 24f, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(GRAPH_MAX.roundToInt().toString(), width - 8f, 24f, labelPaint)
        canvas.drawText(targetLow.roundToInt().toString(), width - 8f, yFor(targetLow) - 5f, labelPaint)

        if (samples.isEmpty()) {
            val freshness = TherapyDisplayFormatter.freshness(state, nowEpochMs)
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                textSize = 22f
            }
            canvas.drawText(
                TherapyDisplayFormatter.freshnessLabel(freshness),
                width / 2f,
                height / 2f,
                statusPaint,
            )
        }
        return bitmap
    }

    private companion object {
        const val GRAPH_MIN = 40.0
        const val GRAPH_MAX = 300.0
        const val COLOR_IN_RANGE = 0xFF172234.toInt()
        const val COLOR_HIGH = 0xFF111925.toInt()
        const val COLOR_LOW = 0xFFB0005D.toInt()
        const val COLOR_LOW_LINE = 0xFFFF2C82.toInt()
        const val COLOR_DIVIDER = 0xFF5F6878.toInt()
    }
}
