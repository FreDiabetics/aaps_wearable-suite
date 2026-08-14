package app.aapswear.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.model.ComplicationPresentationFormatter
import app.aapswear.model.GlucoseSample
import app.aapswear.model.SugarliciousComplicationIds
import app.aapswear.model.TherapyDisplayState
import kotlin.math.cos
import kotlin.math.sin

/** Product-fixed preview time: hour at 10, minute at 2, second at 6. */
internal val fixedWatchPreviewHandAngles =
    WatchPreviewHandAngles(
        hour = 300f,
        minute = 60f,
        second = 180f,
    )

@Composable
internal fun SugarliciousFacePreview(
    index: Int,
    state: TherapyDisplayState?,
    complicationIds: List<Int>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clip(CircleShape).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            when (index.coerceIn(0, 3)) {
                0 -> {
                    drawCircle(Color(0xFF050B10), r)
                    repeat(12) { tick ->
                        val a = Math.toRadians(tick * 30.0 - 90.0)
                        val inner = r - 13.dp.toPx()
                        val outer = r - 4.dp.toPx()
                        drawLine(
                            color = if (tick % 3 == 0) Color(0xFF19D7E8) else Color(0xFF68757C),
                            start = Offset(center.x + cos(a).toFloat() * inner, center.y + sin(a).toFloat() * inner),
                            end = Offset(center.x + cos(a).toFloat() * outer, center.y + sin(a).toFloat() * outer),
                            strokeWidth = if (tick % 3 == 0) 2.2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    drawPreviewCard(0.26f, 0.10f, 0.48f, 0.20f)
                    drawPreviewCard(0.08f, 0.37f, 0.25f, 0.17f)
                    drawPreviewCard(0.67f, 0.37f, 0.25f, 0.17f)
                    drawPreviewCard(0.34f, 0.76f, 0.32f, 0.13f)
                }
                1 -> {
                    drawCircle(Color(0xFF050505), r)
                    drawCircle(Color(0xFF303030), r - 6.dp.toPx(), style = Stroke(4.dp.toPx()))
                    drawArc(
                        color = Color(0xFF19D7E8),
                        startAngle = 220f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCircle(Color(0xFF343434), r * 0.47f, style = Stroke(7.dp.toPx()))
                    drawArc(
                        Color(0xFF54DF30),
                        130f,
                        205f,
                        false,
                        Offset(center.x - r * 0.47f, center.y - r * 0.47f),
                        Size(r * 0.94f, r * 0.94f),
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                2 -> {
                    drawCircle(Color(0xFF050505), r)
                    drawCircle(Color(0xFF222222), r - 6.dp.toPx(), style = Stroke(4.dp.toPx()))
                    drawArc(
                        Color(0xFFFF8B60),
                        120f,
                        120f,
                        false,
                        Offset(10.dp.toPx(), 10.dp.toPx()),
                        Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        Color(0xFF19D7E8),
                        300f,
                        120f,
                        false,
                        Offset(16.dp.toPx(), 16.dp.toPx()),
                        Size(size.width - 32.dp.toPx(), size.height - 32.dp.toPx()),
                        style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                else -> {
                    drawCircle(Color(0xFF050505), r)
                    drawCircle(Color(0xFF19D7E8), r - 5.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                    drawRoundRect(
                        Color(0xFF151515),
                        topLeft = Offset(size.width * 0.105f, size.height * 0.115f),
                        size = Size(size.width * 0.79f, size.height * 0.235f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    )
                    drawPreviewCard(0.08f, 0.39f, 0.27f, 0.14f)
                    drawPreviewCard(0.65f, 0.39f, 0.27f, 0.14f)
                    drawPreviewCard(0.08f, 0.56f, 0.84f, 0.30f)
                }
            }
        }

        val ids = complicationIds.take(4)
        val slots = previewSlots(index.coerceIn(0, 3), maxWidth, maxHeight)
        ids.forEachIndexed { slotIndex, id ->
            val slot = slots.getOrNull(slotIndex) ?: return@forEachIndexed
            PreviewComplication(
                id = id,
                state = state,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = slot.x, y = slot.y)
                        .size(width = slot.width, height = slot.height),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val scale = size.minDimension / 512f
            fun sx(value: Float): Float = (center.x - 256f * scale) + value * scale
            fun sy(value: Float): Float = (center.y - 256f * scale) + value * scale
            val handAngles = fixedWatchPreviewHandAngles

            withTransform({ rotate(handAngles.hour, center) }) {
                drawRoundRect(
                    Color.White,
                    Offset(sx(243f), sy(113.57f)),
                    Size(26f * scale, 140f * scale),
                    androidx.compose.ui.geometry.CornerRadius(13f * scale),
                )
            }
            withTransform({ rotate(handAngles.minute, center) }) {
                drawRoundRect(
                    Color.White,
                    Offset(sx(243f), sy(34.47f)),
                    Size(26f * scale, 219.5f * scale),
                    androidx.compose.ui.geometry.CornerRadius(13f * scale),
                )
            }
            drawCircle(Color(0xFFBCBCBC), 12f * scale, center)
            withTransform({ rotate(handAngles.second, center) }) {
                drawRect(
                    Color.White,
                    Offset(sx(254f), sy(6f)),
                    Size(4f * scale, 290f * scale),
                )
                drawCircle(Color.White, 8.5f * scale, center)
            }
            drawCircle(Color.Black, 4f * scale, center)
        }
    }
}

private data class PreviewSlot(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
)

private fun previewSlots(
    index: Int,
    width: Dp,
    height: Dp,
): List<PreviewSlot> =
    when (index) {
        0 -> listOf(
            PreviewSlot(width * 0.27f, height * 0.11f, width * 0.46f, height * 0.19f),
            PreviewSlot(width * 0.08f, height * 0.37f, width * 0.25f, height * 0.17f),
            PreviewSlot(width * 0.67f, height * 0.37f, width * 0.25f, height * 0.17f),
            PreviewSlot(width * 0.34f, height * 0.76f, width * 0.32f, height * 0.13f),
        )
        1 -> listOf(
            PreviewSlot(width * 0.29f, height * 0.31f, width * 0.42f, height * 0.25f),
            PreviewSlot(width * 0.02f, height * 0.38f, width * 0.25f, height * 0.15f),
            PreviewSlot(width * 0.73f, height * 0.38f, width * 0.25f, height * 0.15f),
            PreviewSlot(width * 0.17f, height * 0.72f, width * 0.66f, height * 0.15f),
        )
        2 -> listOf(
            PreviewSlot(width * 0.33f, height * 0.61f, width * 0.34f, height * 0.24f),
            PreviewSlot(width * 0.08f, height * 0.23f, width * 0.28f, height * 0.16f),
            PreviewSlot(width * 0.64f, height * 0.23f, width * 0.28f, height * 0.16f),
            PreviewSlot(width * 0.16f, height * 0.43f, width * 0.68f, height * 0.16f),
        )
        else -> listOf(
            PreviewSlot(width * 0.15f, height * 0.13f, width * 0.70f, height * 0.20f),
            PreviewSlot(width * 0.08f, height * 0.56f, width * 0.84f, height * 0.28f),
            PreviewSlot(width * 0.08f, height * 0.39f, width * 0.27f, height * 0.14f),
            PreviewSlot(width * 0.65f, height * 0.39f, width * 0.27f, height * 0.14f),
        )
    }

@Composable
private fun PreviewComplication(
    id: Int,
    state: TherapyDisplayState?,
    modifier: Modifier,
) {
    if (id == SugarliciousComplicationIds.GRAPH) {
        MiniPreviewGraph(state, modifier)
        return
    }

    val now = System.currentTimeMillis()
    val presentation = ComplicationPresentationFormatter.format(id, state ?: previewFaceState(now), now)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = presentation.text,
            color = Color.White,
            fontSize = if (id == SugarliciousComplicationIds.GLUCOSE) 12.sp else 7.5.sp,
            lineHeight = if (id == SugarliciousComplicationIds.GLUCOSE) 12.sp else 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        presentation.title?.takeIf(String::isNotBlank)?.let { title ->
            Text(
                text = title,
                modifier = Modifier.offset(y = (-1).dp),
                color = Color(0xFFB8C0C4),
                fontSize = 5.5.sp,
                lineHeight = 6.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiniPreviewGraph(
    state: TherapyDisplayState?,
    modifier: Modifier,
) {
    val now = System.currentTimeMillis()
    val cutoff = now - 90L * 60_000L
    val points =
        (state?.glucoseHistory.orEmpty() +
            listOfNotNull(state?.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs) }))
            .filter { it.measuredAtEpochMs in cutoff..now + 5 * 60_000L }
            .sortedBy { it.measuredAtEpochMs }

    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val low = 70.0
        val high = 180.0
        fun x(ts: Long): Float =
            (((ts - cutoff).toFloat() / (90f * 60_000f)).coerceIn(0f, 1f)) * size.width
        fun y(value: Double): Float =
            size.height - (((value - 50.0) / 180.0).coerceIn(0.0, 1.0).toFloat() * size.height)
        drawRoundRect(Color(0x2219D7E8), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        points.forEach { point ->
            val color = when {
                point.valueMgDl < low -> Color(0xFFFF6464)
                point.valueMgDl > high -> Color(0xFFFFA24B)
                else -> Color(0xFF54DF30)
            }
            drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(x(point.measuredAtEpochMs), y(point.valueMgDl)))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewCard(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    drawRoundRect(
        color = Color(0xFF091117),
        topLeft = Offset(size.width * x, size.height * y),
        size = Size(size.width * width, size.height * height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.035f),
    )
}

private fun previewFaceState(now: Long): TherapyDisplayState =
    TherapyDisplayState(
        receivedAtEpochMs = now,
        sourceVersion = "AndroidAPS",
        glucose = app.aapswear.model.GlucoseState(
            valueMgDl = 123.0,
            displayUnit = app.aapswear.model.GlucoseUnit.MG_DL,
            trend = app.aapswear.model.Trend.FORTY_FIVE_UP,
            measuredAtEpochMs = now - 2 * 60_000L,
            deltaMgDl = 5.0,
        ),
        insulin = app.aapswear.model.InsulinState(totalIob = 1.2, bolusIob = 0.8, basalIob = 0.4),
        carbs = app.aapswear.model.CarbState(cobGrams = 15.0),
        basal = app.aapswear.model.BasalState(currentUnitsPerHour = 0.70),
    )
