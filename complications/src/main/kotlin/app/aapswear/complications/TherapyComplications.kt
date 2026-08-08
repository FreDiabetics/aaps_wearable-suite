package app.aapswear.complications

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.aapswear.model.BasalState
import app.aapswear.model.CarbState
import app.aapswear.model.DataCapability
import app.aapswear.model.DeviceState
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.InsulinState
import app.aapswear.model.LoopState
import app.aapswear.model.ProfileState
import app.aapswear.model.PumpState
import app.aapswear.model.TargetState
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first

enum class ProviderKind {
    GLUCOSE,
    GLUCOSE_TREND,
    GLUCOSE_DELTA,
    GLUCOSE_TREND_DELTA,
    GLUCOSE_AGE,
    GLUCOSE_IMAGE,
    GLUCOSE_RANGE,
    GLUCOSE_RANGED,
    GRAPH,
    GRAPH_LARGE,
    IOB,
    BOLUS_IOB,
    BASAL_IOB,
    COB,
    IOB_COB,
    BASAL,
    TEMP_BASAL,
    TEMP_TARGET,
    LOOP,
    LOOP_LAST,
    PROFILE,
    RESERVOIR,
    PUMP_BATTERY,
    PHONE_BATTERY,
    SOURCE,
    AAPS_STATUS,
    LONG_STATUS,
}

abstract class TherapyComplicationService(
    private val kind: ProviderKind,
) : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        build(type, preview())

    override suspend fun onComplicationRequest(
        request: ComplicationRequest,
    ): ComplicationData =
        build(request.complicationType, TherapyStateStore(this).state.first())

    private fun build(
        type: ComplicationType,
        state: TherapyDisplayState?,
    ): ComplicationData {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        val freshness = FreshnessPolicy.classify(
            glucose?.measuredAtEpochMs ?: state?.receivedAtEpochMs,
            now,
        )
        val displayable =
            freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
        val therapyState = state.takeIf { displayable }

        val glucoseText =
            if (displayable && glucose != null) glucose(glucose) else DASH
        val trendText =
            if (displayable && glucose != null) arrow(glucose.trend) else ""
        val deltaText =
            if (displayable && glucose != null) {
                signed(glucose.deltaMgDl, glucose.displayUnit)
            } else {
                ""
            }
        val ageText = glucose?.measuredAtEpochMs
            ?.let { timeAgo(it, now) }
            ?: DASH

        val pair: Pair<String, String> = when (kind) {
            ProviderKind.GLUCOSE ->
                glucoseText to "Glucose"

            ProviderKind.GLUCOSE_TREND ->
                "$glucoseText$trendText" to "Glucose"

            ProviderKind.GLUCOSE_DELTA ->
                deltaText.ifBlank { DASH } to "Delta"

            ProviderKind.GLUCOSE_TREND_DELTA ->
                "$glucoseText$trendText" to deltaText.ifBlank { DASH }

            ProviderKind.GLUCOSE_AGE ->
                ageText to freshnessLabel(freshness)

            ProviderKind.GLUCOSE_RANGE ->
                glucoseText to displayRange(glucose, displayable)

            ProviderKind.IOB ->
                units(therapyState?.insulin?.totalIob, "U", 2) to "IOB"

            ProviderKind.BOLUS_IOB ->
                units(therapyState?.insulin?.bolusIob, "U", 2) to "Bolus IOB"

            ProviderKind.BASAL_IOB ->
                units(therapyState?.insulin?.basalIob, "U", 2) to "Basal IOB"

            ProviderKind.COB ->
                units(therapyState?.carbs?.cobGrams, "g", 0) to "COB"

            ProviderKind.IOB_COB ->
                "${units(therapyState?.insulin?.totalIob, "U", 1)} " +
                    units(therapyState?.carbs?.cobGrams, "g", 0) to "IOB · COB"

            ProviderKind.BASAL ->
                units(therapyState?.basal?.currentUnitsPerHour, "U/h", 2) to "Basal"

            ProviderKind.TEMP_BASAL ->
                (
                    therapyState?.basal?.displayText
                        ?: therapyState?.basal?.tempPercent?.let { "$it%" }
                        ?: units(
                            therapyState?.basal?.tempAbsoluteUnitsPerHour,
                            "U/h",
                            2,
                        )
                    ) to "Temp basal"

            ProviderKind.TEMP_TARGET ->
                target(
                    therapyState?.target,
                    glucose?.displayUnit ?: GlucoseUnit.MG_DL,
                ) to "Target"

            ProviderKind.LOOP ->
                loopLabel(therapyState?.loop?.status) to "Loop"

            ProviderKind.LOOP_LAST ->
                timeAgo(therapyState?.loop?.lastRunAtEpochMs, now) to "Last loop"

            ProviderKind.PROFILE ->
                (therapyState?.profile?.name ?: DASH) to "Profile"

            ProviderKind.RESERVOIR ->
                units(therapyState?.pump?.reservoirUnits, "U", 0) to "Reservoir"

            ProviderKind.PUMP_BATTERY ->
                percent(therapyState?.pump?.batteryPercent) to "Pump battery"

            ProviderKind.PHONE_BATTERY ->
                percent(therapyState?.device?.phoneBatteryPercent) to "Phone battery"

            ProviderKind.SOURCE ->
                (state?.sourceVersion ?: "No data") to freshnessLabel(freshness)

            ProviderKind.AAPS_STATUS ->
                "$glucoseText$trendText" to compactTherapyStatus(therapyState)

            ProviderKind.LONG_STATUS ->
                longStatus(
                    glucoseText = glucoseText,
                    trendText = trendText,
                    deltaText = deltaText,
                    ageText = ageText,
                    state = therapyState,
                    freshness = freshness,
                ) to "Sugarlicious"

            ProviderKind.GLUCOSE_IMAGE,
            ProviderKind.GRAPH,
            ProviderKind.GRAPH_LARGE,
            ProviderKind.GLUCOSE_RANGED ->
                glucoseText to "Glucose"
        }

        val description = PlainComplicationText.Builder(pair.second).build()
        val tap = PendingIntent.getActivity(
            this,
            kind.ordinal,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        )

        if (
            kind == ProviderKind.GLUCOSE_IMAGE ||
            kind == ProviderKind.GRAPH ||
            kind == ProviderKind.GRAPH_LARGE
        ) {
            val icon = Icon.createWithBitmap(
                renderImage(
                    state = therapyState,
                    kind = kind,
                    now = now,
                ),
            )
            return if (type == ComplicationType.PHOTO_IMAGE) {
                PhotoImageComplicationData.Builder(icon, description)
                    .setTapAction(tap)
                    .build()
            } else {
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
                    description,
                )
                    .setTapAction(tap)
                    .build()
            }
        }

        if (type == ComplicationType.RANGED_VALUE) {
            when (kind) {
                ProviderKind.GLUCOSE,
                ProviderKind.GLUCOSE_TREND,
                ProviderKind.GLUCOSE_RANGED -> {
                    val value =
                        if (displayable && glucose != null) {
                            glucose.valueMgDl
                                .toFloat()
                                .coerceIn(
                                    GLUCOSE_GAUGE_MIN,
                                    GLUCOSE_GAUGE_MAX,
                                )
                        } else {
                            GLUCOSE_GAUGE_MIN
                        }

                    val rangedDescription =
                        PlainComplicationText.Builder(
                            if (displayable && glucose != null) {
                                "$glucoseText $trendText"
                            } else {
                                "Glucose no data"
                            },
                        ).build()

                    val rangedVisual =
                        SmallImage.Builder(
                            Icon.createWithBitmap(
                                renderRangedGlucoseVisual(
                                    glucoseText = glucoseText,
                                    trendText = trendText.ifBlank { "→" },
                                ),
                            ),
                            SmallImageType.ICON,
                        ).build()

                    return RangedValueComplicationData.Builder(
                        value,
                        GLUCOSE_GAUGE_MIN,
                        GLUCOSE_GAUGE_MAX,
                        rangedDescription,
                    )                        .setText(
                            PlainComplicationText.Builder(
                                glucoseText,
                            ).build(),
                        )
                        .setTitle(
                            PlainComplicationText.Builder(
                                trendText.ifBlank { "→" },
                            ).build(),
                        )
                        .setSmallImage(rangedVisual)
                        .setTapAction(tap)
                        .build()
                }

                ProviderKind.RESERVOIR -> {
                    val value =
                        therapyState?.pump?.reservoirUnits
                            ?.toFloat()
                            ?.coerceIn(0f, 300f)
                            ?: 0f

                    return RangedValueComplicationData.Builder(
                        value,
                        0f,
                        300f,
                        description,
                    )
                        .setText(
                            PlainComplicationText.Builder(
                                pair.first,
                            ).build(),
                        )
                        .setTapAction(tap)
                        .build()
                }

                ProviderKind.PUMP_BATTERY -> {
                    val value =
                        therapyState?.pump?.batteryPercent
                            ?.toFloat()
                            ?.coerceIn(0f, 100f)
                            ?: 0f

                    return RangedValueComplicationData.Builder(
                        value,
                        0f,
                        100f,
                        description,
                    )
                        .setText(
                            PlainComplicationText.Builder(
                                pair.first,
                            ).build(),
                        )
                        .setTapAction(tap)
                        .build()
                }

                ProviderKind.PHONE_BATTERY -> {
                    val value =
                        therapyState?.device?.phoneBatteryPercent
                            ?.toFloat()
                            ?.coerceIn(0f, 100f)
                            ?: 0f

                    return RangedValueComplicationData.Builder(
                        value,
                        0f,
                        100f,
                        description,
                    )
                        .setText(
                            PlainComplicationText.Builder(
                                pair.first,
                            ).build(),
                        )
                        .setTapAction(tap)
                        .build()
                }

                else -> Unit
            }
        }

        if (
            kind == ProviderKind.LONG_STATUS ||
            type == ComplicationType.LONG_TEXT
        ) {
            return LongTextComplicationData.Builder(
                PlainComplicationText.Builder(pair.first).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder(pair.second).build())
                .setTapAction(tap)
                .build()
        }

        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(pair.first.take(24)).build(),
            description,
        )
            .setTitle(PlainComplicationText.Builder(pair.second.take(16)).build())
            .setTapAction(tap)
            .build()
    }

    private fun renderRangedGlucoseVisual(
        glucoseText: String,
        trendText: String,
    ): Bitmap {
        val size = 192
        val bitmap =
            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

        canvas.drawColor(Color.TRANSPARENT)

        paint.textSize = 82f
        canvas.drawText(
            glucoseText,
            size / 2f,
            100f,
            paint,
        )

        paint.textSize = 48f
        canvas.drawText(
            trendText,
            size / 2f,
            158f,
            paint,
        )

        return bitmap
    }
    private fun renderImage(
        state: TherapyDisplayState?,
        kind: ProviderKind,
        now: Long,
    ): Bitmap {
        val valueOnly = kind == ProviderKind.GLUCOSE_IMAGE
        val width = 400
        val height = if (valueOnly) 200 else 240
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val glucose = state?.glucose
        if (valueOnly) {
            drawValueImage(canvas, glucose, width, height, now)
            return bitmap
        }

        val windowMs =
            if (kind == ProviderKind.GRAPH_LARGE) GRAPH_LARGE_WINDOW_MS
            else GRAPH_WINDOW_MS

        drawGraphImage(
            canvas = canvas,
            state = state,
            width = width,
            height = height,
            now = now,
            windowMs = windowMs,
        )
        return bitmap
    }

    private fun drawValueImage(
        canvas: Canvas,
        glucose: GlucoseState?,
        width: Int,
        height: Int,
        now: Long,
    ) {
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glucoseColor(glucose)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 88f
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            textSize = 28f
        }

        val value = glucose?.let { glucose(it) + arrow(it.trend) } ?: DASH
        canvas.drawText(value, width / 2f, height * 0.58f, valuePaint)

        val meta = glucose?.let {
            val delta = signed(it.deltaMgDl, it.displayUnit).ifBlank { DASH }
            "$delta · ${timeAgo(it.measuredAtEpochMs, now)}"
        } ?: "No data"
        canvas.drawText(meta, width / 2f, height * 0.82f, metaPaint)
    }

    private fun drawGraphImage(
        canvas: Canvas,
        state: TherapyDisplayState?,
        width: Int,
        height: Int,
        now: Long,
        windowMs: Long,
    ) {
        val glucose = state?.glucose
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glucoseColor(glucose)
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
            textSize = 42f
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.DEFAULT
            textSize = 24f
        }

        val header =
            glucose?.let { glucose(it) + arrow(it.trend) } ?: DASH
        canvas.drawText(header, 14f, 44f, headerPaint)
        canvas.drawText(
            glucose?.let { timeAgo(it.measuredAtEpochMs, now) } ?: "No data",
            width - 14f,
            40f,
            metaPaint,
        )

        val plotLeft = 12f
        val plotRight = width - 12f
        val plotTop = 62f
        val plotBottom = height - 12f

        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 62, 30)
            alpha = 180
            style = Paint.Style.FILL
        }
        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(105, 105, 105)
            alpha = 120
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val inRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(84, 223, 48)
            style = Paint.Style.FILL
        }
        val outOfRangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 92, 105)
            style = Paint.Style.FILL
        }
        val currentRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        fun yFor(valueMgDl: Double): Float {
            val clamped = valueMgDl.coerceIn(GRAPH_MIN_MGDL, GRAPH_MAX_MGDL)
            val fraction =
                (GRAPH_MAX_MGDL - clamped) /
                    (GRAPH_MAX_MGDL - GRAPH_MIN_MGDL)
            return plotTop +
                (fraction * (plotBottom - plotTop)).toFloat()
        }

        val targetTop = yFor(DISPLAY_HIGH_MGDL)
        val targetBottom = yFor(DISPLAY_LOW_MGDL)
        canvas.drawRoundRect(
            plotLeft,
            targetTop,
            plotRight,
            targetBottom,
            10f,
            10f,
            targetPaint,
        )
        canvas.drawLine(
            plotLeft,
            targetTop,
            plotRight,
            targetTop,
            guidePaint,
        )
        canvas.drawLine(
            plotLeft,
            targetBottom,
            plotRight,
            targetBottom,
            guidePaint,
        )

        val cutoff = now - windowMs
        val merged = linkedMapOf<Long, GlucoseSample>()

        state?.glucoseHistory
            .orEmpty()
            .forEach { merged[it.measuredAtEpochMs] = it }

        glucose?.let {
            merged[it.measuredAtEpochMs] =
                GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)
        }

        val samples = merged
            .values
            .asSequence()
            .filter {
                it.measuredAtEpochMs in cutoff..(now + FUTURE_TOLERANCE_MS) &&
                    it.valueMgDl in 20.0..1000.0
            }
            .sortedBy { it.measuredAtEpochMs }
            .toList()

        if (samples.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                textAlign = Paint.Align.CENTER
                textSize = 26f
            }
            canvas.drawText(
                "No history",
                width / 2f,
                (plotTop + plotBottom) / 2f,
                emptyPaint,
            )
            return
        }

        fun xFor(timestamp: Long): Float {
            val fraction =
                ((timestamp - cutoff).toDouble() / windowMs.toDouble())
                    .coerceIn(0.0, 1.0)
            return plotLeft +
                (fraction * (plotRight - plotLeft)).toFloat()
        }

        samples.forEach { sample ->
            val paint =
                if (sample.valueMgDl in DISPLAY_LOW_MGDL..DISPLAY_HIGH_MGDL) {
                    inRangePaint
                } else {
                    outOfRangePaint
                }

            canvas.drawCircle(
                xFor(sample.measuredAtEpochMs),
                yFor(sample.valueMgDl),
                5.5f,
                paint,
            )
        }

        samples.lastOrNull()?.let { newest ->
            canvas.drawCircle(
                xFor(newest.measuredAtEpochMs),
                yFor(newest.valueMgDl),
                9f,
                currentRingPaint,
            )
        }
    }

    private fun glucoseColor(glucose: GlucoseState?): Int =
        when {
            glucose == null -> Color.GRAY
            glucose.valueMgDl in DISPLAY_LOW_MGDL..DISPLAY_HIGH_MGDL ->
                Color.WHITE

            else -> Color.rgb(255, 92, 105)
        }

    private fun compactTherapyStatus(
        state: TherapyDisplayState?,
    ): String =
        "${units(state?.insulin?.totalIob, "U", 1)} · " +
            units(state?.carbs?.cobGrams, "g", 0)

    private fun longStatus(
        glucoseText: String,
        trendText: String,
        deltaText: String,
        ageText: String,
        state: TherapyDisplayState?,
        freshness: Freshness,
    ): String =
        buildString {
            append(glucoseText)
            append(trendText)
            append(" · Δ ")
            append(deltaText.ifBlank { DASH })
            append(" · ")
            append(ageText)
            append(" · IOB ")
            append(units(state?.insulin?.totalIob, "U", 2))
            append(" · COB ")
            append(units(state?.carbs?.cobGrams, "g", 0))
            append(" · Basal ")
            append(units(state?.basal?.currentUnitsPerHour, "U/h", 2))
            append(" · ")
            append(loopLabel(state?.loop?.status))
            append(" · ")
            append(freshnessLabel(freshness))
        }

    private fun displayRange(
        glucose: GlucoseState?,
        displayable: Boolean,
    ): String =
        when {
            !displayable || glucose == null -> "no data"
            glucose.valueMgDl < DISPLAY_LOW_MGDL -> "low"
            glucose.valueMgDl > DISPLAY_HIGH_MGDL -> "high"
            else -> "in range"
        }

    private fun freshnessLabel(freshness: Freshness): String =
        when (freshness) {
            Freshness.CURRENT -> "live"
            Freshness.DELAYED -> "delayed"
            Freshness.STALE -> "stale"
            Freshness.NO_DATA -> "no data"
        }

    private fun loopLabel(status: String?): String =
        when (status?.lowercase()) {
            "enacted" -> "Loop active"
            "suggested" -> "Loop suggested"
            null -> DASH
            else -> status
        }

    private fun glucose(g: GlucoseState) =
        TherapyDisplayFormatter.glucose(g)

    private fun signed(v: Double?, u: GlucoseUnit) =
        TherapyDisplayFormatter.signedDelta(v, u)

    private fun arrow(t: Trend) =
        TherapyDisplayFormatter.trendArrow(t)

    private fun units(v: Double?, suffix: String, digits: Int) =
        TherapyDisplayFormatter.units(v, suffix, digits)

    private fun percent(v: Int?) =
        TherapyDisplayFormatter.percent(v)

    private fun timeAgo(t: Long?, now: Long) =
        TherapyDisplayFormatter.ageMinutes(t, now)

    private fun target(t: TargetState?, u: GlucoseUnit) =
        TherapyDisplayFormatter.target(t, u)

    private fun preview(): TherapyDisplayState {
        val now = System.currentTimeMillis()
        val history = (0..36).map { index ->
            val minutesAgo = (36 - index) * 5L
            val wave = when {
                index < 10 -> 108.0 + index * 2.0
                index < 22 -> 128.0 - (index - 10) * 1.3
                else -> 112.0 + (index - 22) * 0.8
            }
            GlucoseSample(
                valueMgDl = wave,
                measuredAtEpochMs = now - minutesAgo * 60_000L,
            )
        }

        return TherapyDisplayState(
            receivedAtEpochMs = now,
            sourceVersion = "AAPS dev",
            glucose = GlucoseState(
                valueMgDl = 123.0,
                displayUnit = GlucoseUnit.MG_DL,
                trend = Trend.FORTY_FIVE_UP,
                measuredAtEpochMs = now,
                deltaMgDl = 5.0,
                averageDeltaMgDl = 3.0,
            ),
            glucoseHistory = history,
            insulin = InsulinState(1.2, 0.8, 0.4),
            carbs = CarbState(15.0, 0.0),
            basal = BasalState(
                currentUnitsPerHour = 0.8,
                tempPercent = 120,
                displayText = "120%",
            ),
            target = TargetState(80.0, 160.0),
            loop = LoopState("enacted", now),
            pump = PumpState("OK", 120.0, 80),
            device = DeviceState(85, 90),
            profile = ProfileState("Default"),
            capabilities = DataCapability.entries.toSet(),
        )
    }

    companion object {
        private const val DASH = "—"

        private const val DISPLAY_LOW_MGDL = 80.0
        private const val DISPLAY_HIGH_MGDL = 160.0

        private const val GLUCOSE_GAUGE_MIN = 40f
        private const val GLUCOSE_GAUGE_MAX = 260f

        private const val GRAPH_MIN_MGDL = 40.0
        private const val GRAPH_MAX_MGDL = 260.0
        private const val GRAPH_WINDOW_MS = 3 * 60 * 60_000L
        private const val GRAPH_LARGE_WINDOW_MS = 6 * 60 * 60_000L
        private const val FUTURE_TOLERANCE_MS = 5 * 60_000L
    }
}

class GlucoseComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE)

class GlucoseTrendComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_TREND)

class GlucoseDeltaComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_DELTA)

class GlucoseTrendDeltaComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_TREND_DELTA)

class GlucoseAgeComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_AGE)

class GlucoseImageComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_IMAGE)

class GlucoseRangeComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_RANGE)

class GlucoseRangedComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_RANGED)

class GlucoseGraphComplication :
    TherapyComplicationService(ProviderKind.GRAPH)

class GlucoseGraphLargeComplication :
    TherapyComplicationService(ProviderKind.GRAPH_LARGE)

class IobComplication :
    TherapyComplicationService(ProviderKind.IOB)

class BolusIobComplication :
    TherapyComplicationService(ProviderKind.BOLUS_IOB)

class BasalIobComplication :
    TherapyComplicationService(ProviderKind.BASAL_IOB)

class CobComplication :
    TherapyComplicationService(ProviderKind.COB)

class IobCobComplication :
    TherapyComplicationService(ProviderKind.IOB_COB)

class BasalComplication :
    TherapyComplicationService(ProviderKind.BASAL)

class TempBasalComplication :
    TherapyComplicationService(ProviderKind.TEMP_BASAL)

class TempTargetComplication :
    TherapyComplicationService(ProviderKind.TEMP_TARGET)

class LoopComplication :
    TherapyComplicationService(ProviderKind.LOOP)

class LastLoopComplication :
    TherapyComplicationService(ProviderKind.LOOP_LAST)

class ProfileComplication :
    TherapyComplicationService(ProviderKind.PROFILE)

class ReservoirComplication :
    TherapyComplicationService(ProviderKind.RESERVOIR)

class PumpBatteryComplication :
    TherapyComplicationService(ProviderKind.PUMP_BATTERY)

class PhoneBatteryComplication :
    TherapyComplicationService(ProviderKind.PHONE_BATTERY)

class SourceComplication :
    TherapyComplicationService(ProviderKind.SOURCE)

class AapsStatusComplication :
    TherapyComplicationService(ProviderKind.AAPS_STATUS)

class LongStatusComplication :
    TherapyComplicationService(ProviderKind.LONG_STATUS)

object AllProviders {
    val classes = listOf(
        GlucoseComplication::class.java,
        GlucoseTrendComplication::class.java,
        GlucoseDeltaComplication::class.java,
        GlucoseTrendDeltaComplication::class.java,
        GlucoseAgeComplication::class.java,
        GlucoseImageComplication::class.java,
        GlucoseRangeComplication::class.java,
        GlucoseRangedComplication::class.java,
        GlucoseGraphComplication::class.java,
        GlucoseGraphLargeComplication::class.java,
        IobComplication::class.java,
        BolusIobComplication::class.java,
        BasalIobComplication::class.java,
        CobComplication::class.java,
        IobCobComplication::class.java,
        BasalComplication::class.java,
        TempBasalComplication::class.java,
        TempTargetComplication::class.java,
        LoopComplication::class.java,
        LastLoopComplication::class.java,
        ProfileComplication::class.java,
        ReservoirComplication::class.java,
        PumpBatteryComplication::class.java,
        PhoneBatteryComplication::class.java,
        SourceComplication::class.java,
        AapsStatusComplication::class.java,
        LongStatusComplication::class.java,
    )
}
