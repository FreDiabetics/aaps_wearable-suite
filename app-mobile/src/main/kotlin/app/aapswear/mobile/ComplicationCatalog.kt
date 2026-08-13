package app.aapswear.mobile

import androidx.core.content.edit
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.protocol.WearProtocol
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

internal data class ComplicationCatalogEntry(
    val id: Int,
    val name: String,
    val category: ComplicationCategory,
    val types: String,
)

internal enum class ComplicationCategory(
    val label: String,
    val range: String,
) {
    GLUCOSE("Glukose", "02 · 28 · 03 · 09 · 10"),
    THERAPY("Therapie", "11 · 14"),
}

internal val SugarliciousComplicationCatalog = listOf(
    ComplicationCatalogEntry(1, "Glukose", ComplicationCategory.GLUCOSE, "SHORT · RANGED · LONG"),
    ComplicationCatalogEntry(35, "Trend", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(36, "Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(5, "Zeit seit letztem Wert", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(16, "Basal", ComplicationCategory.THERAPY, "SHORT"),
    ComplicationCatalogEntry(11, "IOB", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(14, "COB", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(2, "Glukose + Trend", ComplicationCategory.GLUCOSE, "SHORT · RANGED"),
    ComplicationCatalogEntry(29, "Glukose + Delta", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(3, "Zeit + Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(33, "Glukose + Trend + Zeit", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(4, "Glukose + Trend + Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(32, "Glukose + Trend + Delta + Zeit", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(34, "IOB + COB + Basal", ComplicationCategory.THERAPY, "SHORT · LONG"),
    ComplicationCatalogEntry(19, "Loop Status", ComplicationCategory.THERAPY, "SHORT · ICON"),
    ComplicationCatalogEntry(22, "Pumpe / Reservoir", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(30, "Sensoralter", ComplicationCategory.GLUCOSE, "SHORT · RANGED"),
    ComplicationCatalogEntry(31, "TIR", ComplicationCategory.GLUCOSE, "SHORT · GOAL · WEIGHTED"),
    ComplicationCatalogEntry(9, "CGM Graph", ComplicationCategory.GLUCOSE, "IMAGE"),
)

@Composable
internal fun ComplicationStudio(
    state: TherapyDisplayState?,
    onPresetChanged: (List<Int>) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(loadComplicationPreset(context)) }
    var graphHours by remember { mutableStateOf(loadComplicationGraphHours(context)) }
    var syncLabel by remember {
        mutableStateOf(
            if (selected.isEmpty()) "Noch kein Smartphone-Preset"
            else "Preset lokal gespeichert",
        )
    }

    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.75f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "COMPLICATION STUDIO",
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    "Watch-Preset & Live-Vorschau",
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SugarliciousColors.SurfaceHigh,
            ) {
                Text(
                    "${selected.size}/4",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = SugarliciousColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            "Hier stellst du bis zu vier bevorzugte Sugarlicious-Complications zusammen. " +
                "Das Preset wird zur Watch synchronisiert. Bei fremden Samsung-/Wear-OS-Watchfaces " +
                "bleibt die einmalige Slot-Zuordnung aus Sicherheitsgründen beim Wear-OS-Picker.",
            color = SugarliciousColors.TextSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )

        PresetStrip(selected)

        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        syncComplicationPreset(context, selected, graphHours)
                    }.onSuccess {
                        syncLabel = "Preset an Wear Data Layer übergeben"
                    }.onFailure {
                        syncLabel = "Preset-Sync fehlgeschlagen"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SugarliciousColors.SurfaceHigh,
                contentColor = SugarliciousColors.Primary,
            ),
        ) {
            Text("PRESET AN WATCH SENDEN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            syncLabel,
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
        )

        Text(
            "CGM-GRAPH ZEITRAUM",
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(1, 2, 6, 12, 24).forEach { hours ->
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        graphHours = hours
                        saveComplicationGraphHours(context, hours)
                        scope.launch {
                            runCatching { syncComplicationPreset(context, selected, hours) }
                                .onSuccess { syncLabel = "Graph auf ${hours} h synchronisiert" }
                                .onFailure { syncLabel = "Graph lokal auf ${hours} h gesetzt" }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (graphHours == hours) SugarliciousColors.SurfaceSelected else SugarliciousColors.SurfaceHigh,
                ) {
                    Text(
                        "${hours}h",
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = if (graphHours == hours) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        SugarliciousComplicationCatalog.chunked(3).forEach { entries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                entries.forEach { entry ->
                    ComplicationCatalogTile(
                        modifier = Modifier.weight(1f),
                        entry = entry,
                        state = state,
                        graphHours = graphHours,
                        selected = entry.id in selected,
                        onToggle = {
                            val updated = togglePresetEntry(context, selected, entry.id)
                            if (updated == selected && entry.id !in selected && selected.size >= 4) {
                                Toast.makeText(context, "Das Watch-Preset hat maximal 4 Plätze.", Toast.LENGTH_SHORT).show()
                            } else {
                                selected = updated
                                onPresetChanged(updated)
                                syncLabel = "Preset geändert · wird synchronisiert"
                                scope.launch {
                                    runCatching { syncComplicationPreset(context, updated, graphHours) }
                                        .onSuccess { syncLabel = "Preset an Watch synchronisiert" }
                                        .onFailure { syncLabel = "Lokal gespeichert · Watch-Sync ausstehend" }
                                }
                            }
                        },
                    )
                }
                repeat(3 - entries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetStrip(selected: List<Int>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(4) { index ->
            val id = selected.getOrNull(index)
            val entry = SugarliciousComplicationCatalog.firstOrNull { it.id == id }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = SugarliciousColors.SurfaceHigh,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        entry?.id?.toString()?.padStart(2, '0') ?: "—",
                        color = if (entry != null) {
                            SugarliciousColors.Primary
                        } else {
                            SugarliciousColors.TextSecondary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        entry?.name ?: "frei",
                        color = SugarliciousColors.TextSecondary,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: ComplicationCategory,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                SugarliciousColors.SurfaceHigh,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.label,
            color = SugarliciousColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            category.range,
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (expanded) "−" else "+",
            color = SugarliciousColors.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ComplicationCatalogTile(
    modifier: Modifier,
    entry: ComplicationCatalogEntry,
    state: TherapyDisplayState?,
    graphHours: Int,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.aspectRatio(1f)
            .background(if (selected) SugarliciousColors.SurfaceSelected else SugarliciousColors.SurfaceHigh, shape)
            .border(1.dp, if (selected) SugarliciousColors.Primary.copy(alpha = 0.62f) else SugarliciousColors.Border.copy(alpha = 0.55f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            entry.name,
            color = SugarliciousColors.TextPrimary,
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        CompactComplicationPreview(entry, state, graphHours)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CompactComplicationPreview(
    entry: ComplicationCatalogEntry,
    state: TherapyDisplayState?,
    graphHours: Int,
) {
    if (entry.id == 9) {
        MiniGlucosePreview(
            samples = state?.glucoseHistory.orEmpty(),
            current = state?.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs) },
            hours = graphHours,
        )
        return
    }
    val preview = previewFor(entry.id, state)
    Text(preview.primary, color = preview.color, fontSize = 10.sp, lineHeight = 11.sp,
        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (preview.secondary.isNotBlank()) {
        Text(preview.secondary, color = SugarliciousColors.TextSecondary, fontSize = 6.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ComplicationCatalogRow(
    entry: ComplicationCatalogEntry,
    state: TherapyDisplayState?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    SugarliciousColors.SurfaceSelected
                } else {
                    SugarliciousColors.Surface.copy(alpha = 0.01f)
                },
                shape,
            )
            .border(
                1.dp,
                if (selected) {
                    SugarliciousColors.Primary.copy(alpha = 0.5f)
                } else {
                    SugarliciousColors.Border.copy(alpha = 0.55f)
                },
                shape,
            )
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    SugarliciousColors.Primary
                } else {
                    SugarliciousColors.SurfaceHigh
                },
            ) {
                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        entry.id.toString().padStart(2, '0'),
                        color = if (selected) {
                            SugarliciousColors.OnPrimary
                        } else {
                            SugarliciousColors.TextPrimary
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(9.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.types,
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 8.sp,
                )
            }

            Text(
                if (selected) "✓ PRESET" else "+ PRESET",
                color = if (selected) {
                    SugarliciousColors.Primary
                } else {
                    SugarliciousColors.TextSecondary
                },
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        ComplicationDataPreview(entry, state)
    }
}

@Composable
private fun ComplicationDataPreview(
    entry: ComplicationCatalogEntry,
    state: TherapyDisplayState?,
) {
    val preview = previewFor(entry.id, state)
    val shape = RoundedCornerShape(14.dp)

    if (entry.id == 2) {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, now)
        val current = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
        val g = glucose.takeIf { current }

        CircularGlucoseComplicationPreview(
            glucoseValue = g?.valueMgDl ?: 123.0,
            glucoseText = g?.let { TherapyDisplayFormatter.glucose(it) } ?: "123",
            trendText = g?.let { TherapyDisplayFormatter.trendArrow(it.trend) }
                ?.ifBlank { "↗" }
                ?: "↗",
            modifier = Modifier
                .fillMaxWidth()
                .background(SugarliciousColors.Background, shape)
                .padding(vertical = 10.dp),
        )
        return
    }

    if (entry.id == 9 || entry.id == 10) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SugarliciousColors.Background, shape)
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (entry.id == 9) "3h Datenvorschau" else "6h Datenvorschau",
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 8.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    preview.primary,
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            MiniGlucosePreview(
                samples = state?.glucoseHistory.orEmpty(),
                current = state?.glucose?.let {
                    GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)
                },
                hours = if (entry.id == 9) 3 else 6,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Background, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "VORSCHAU",
            color = SugarliciousColors.TextSecondary,
            fontSize = 7.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            preview.primary,
            modifier = Modifier.weight(1f),
            color = preview.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            preview.secondary,
            color = SugarliciousColors.TextSecondary,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CircularGlucoseComplicationPreview(
    glucoseValue: Double,
    glucoseText: String,
    trendText: String,
    modifier: Modifier = Modifier,
) {
    val foreground = when {
        glucoseValue < 80.0 -> SugarliciousColors.GlucoseLow
        glucoseValue > 160.0 -> SugarliciousColors.GlucoseHigh
        else -> SugarliciousColors.GlucoseInRange
    }
    val progress =
        ((glucoseValue - 40.0) / (260.0 - 40.0))
            .coerceIn(0.0, 1.0)
            .toFloat()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(122.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 17.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f,
                )
                val arcSize =
                    androidx.compose.ui.geometry.Size(
                        diameter,
                        diameter,
                    )

                drawArc(
                    color = SugarliciousColors.SurfaceHigh,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                    ),
                )

                drawArc(
                    color = foreground,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                    ),
                )
            }

            Column(
                modifier = Modifier.offset(y = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = glucoseText,
                    color = foreground,
                    fontSize = 30.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = trendText,
                    modifier = Modifier.offset(y = (-4).dp),
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 37.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
@Composable
private fun MiniGlucosePreview(samples: List<GlucoseSample>, current: GlucoseSample?, hours: Int) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE) }
    val now = System.currentTimeMillis()
    val cutoff = now - hours * 60L * 60_000L
    val merged = (samples + listOfNotNull(current))
        .filter { it.measuredAtEpochMs in cutoff..(now + 5 * 60_000L) && it.valueMgDl in 20.0..1000.0 }
        .distinctBy { it.measuredAtEpochMs }
        .sortedBy { it.measuredAtEpochMs }
        .ifEmpty { demoHistory(now, hours) }
    val dotRadiusDp = preferences.getFloat("cgm.dotRadiusDp", 2.4f).coerceIn(1.5f, 6f)
    val outlineEnabled = preferences.getBoolean("cgm.dotOutlineEnabled", true)
    val outlineWidthDp = preferences.getFloat("cgm.dotOutlineWidthDp", 0.95f).coerceIn(0.25f, 3f)
    Canvas(
        Modifier.fillMaxWidth().height(52.dp)
            .background(SugarliciousColors.color(SugarliciousColorRole.GRAPH_BACKGROUND)),
    ) {
        val left = 3.dp.toPx()
        val right = size.width - 3.dp.toPx()
        val top = 3.dp.toPx()
        val bottom = size.height - 3.dp.toPx()
        val values = merged.map { it.valueMgDl }
        val minimum = kotlin.math.min(40.0, values.minOrNull() ?: 40.0) - 10.0
        val maximum = kotlin.math.max(200.0, values.maxOrNull() ?: 200.0) + 10.0
        val yMin = kotlin.math.floor(minimum / 20.0) * 20.0
        val yMax = kotlin.math.ceil(maximum / 20.0) * 20.0
        fun x(timestamp: Long) = left + (((timestamp - cutoff).toDouble() / (hours * 60L * 60_000L).toDouble()).coerceIn(0.0, 1.0) * (right - left)).toFloat()
        fun y(value: Double) = bottom - (((value - yMin) / (yMax - yMin).coerceAtLeast(1.0)).coerceIn(0.0, 1.0) * (bottom - top)).toFloat()
        val low = 80.0
        val high = 160.0
        drawRect(
            color = SugarliciousColors.color(SugarliciousColorRole.RANGE_IN_RANGE),
            topLeft = Offset(left, y(high)),
            size = androidx.compose.ui.geometry.Size(right - left, (y(low) - y(high)).coerceAtLeast(1f)),
        )
        drawLine(SugarliciousColors.color(SugarliciousColorRole.GRAPH_DIVIDER), Offset(left, y(high)), Offset(right, y(high)), 0.7.dp.toPx())
        drawLine(SugarliciousColors.color(SugarliciousColorRole.GRAPH_DIVIDER), Offset(left, y(low)), Offset(right, y(low)), 0.7.dp.toPx())
        merged.forEachIndexed { index, sample ->
            val radius = dotRadiusDp.dp.toPx() * if (index == merged.lastIndex) 1.25f else 1f
            val center = Offset(x(sample.measuredAtEpochMs), y(sample.valueMgDl))
            if (outlineEnabled) {
                drawCircle(SugarliciousColors.color(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE), radius + outlineWidthDp.dp.toPx(), center)
            }
            val dotColor = when {
                sample.valueMgDl < low -> SugarliciousColors.color(SugarliciousColorRole.CGM_DOT_LOW)
                sample.valueMgDl > high -> SugarliciousColors.color(SugarliciousColorRole.CGM_DOT_HIGH)
                else -> SugarliciousColors.color(SugarliciousColorRole.CGM_DOT_IN_RANGE)
            }
            drawCircle(dotColor, radius, center)
        }
    }
}

private data class PhonePreview(
    val primary: String,
    val secondary: String,
    val color: Color = SugarliciousColors.TextPrimary,
)

private fun previewFor(
    id: Int,
    state: TherapyDisplayState?,
): PhonePreview {
    val now = System.currentTimeMillis()
    val glucose = state?.glucose
    val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, now)
    val current = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
    val g = glucose.takeIf { current }

    val glucoseText = g?.let { TherapyDisplayFormatter.glucose(it) } ?: "123"
    val trend = g?.let { TherapyDisplayFormatter.trendArrow(it.trend) }
        ?.ifBlank { "→" } ?: "→"
    val delta = g?.let {
        TherapyDisplayFormatter.signedDelta(it.deltaMgDl, it.displayUnit)
    }?.ifBlank { "+5" } ?: "+5"
    val age = g?.measuredAtEpochMs?.let {
        TherapyDisplayFormatter.ageMinutes(it, now)
    } ?: "2m"

    val glucoseColor = when {
        g == null -> SugarliciousColors.TextPrimary
        g.valueMgDl < 80.0 -> SugarliciousColors.GlucoseLow
        g.valueMgDl > 160.0 -> SugarliciousColors.GlucoseHigh
        else -> SugarliciousColors.GlucoseInRange
    }

    fun number(v: Double?, digits: Int, fallback: String): String =
        v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: fallback

    return when (id) {
        1 -> PhonePreview(glucoseText, unitLabel(g?.displayUnit), glucoseColor)
        35 -> PhonePreview(trend, "")
        36 -> PhonePreview(delta, "")
        2 -> PhonePreview("$glucoseText$trend", "Glucose + Trend", glucoseColor)
        28 -> PhonePreview("$glucoseText$trend", "Text", glucoseColor)
        29 -> PhonePreview("$glucoseText $delta", "", glucoseColor)
        30 -> PhonePreview("—", "Sensoralter")
        31 -> {
            val samples = state?.glucoseHistory.orEmpty().filter { it.measuredAtEpochMs >= now - 24L * 60L * 60_000L }
            val tir = if (samples.isEmpty()) 100 else samples.count { it.valueMgDl in 70.0..180.0 } * 100 / samples.size
            PhonePreview("${tir}%", "TIR 70–180")
        }
        32 -> PhonePreview("$glucoseText$trend $delta $age", "", glucoseColor)
        33 -> PhonePreview("$glucoseText$trend $age", "", glucoseColor)
        34 -> PhonePreview(
            "${number(state?.insulin?.totalIob, 1, "1.2")}U · ${number(state?.carbs?.cobGrams, 0, "15")}g",
            "Basal ${number(state?.basal?.currentUnitsPerHour, 2, "0.80")}U/h",
        )
        3 -> PhonePreview("$age · $delta", "")
        4 -> PhonePreview("$glucoseText$trend", "Δ $delta", glucoseColor)
        5 -> PhonePreview(age, freshness.name.lowercase())
        6 -> PhonePreview("$glucoseText$trend", "Bild-Complication", glucoseColor)
        7 -> PhonePreview(
            glucoseText,
            when {
                g == null -> "Demo: im Bereich"
                g.valueMgDl < 80.0 -> "niedrig"
                g.valueMgDl > 160.0 -> "hoch"
                else -> "im Bereich"
            },
            glucoseColor,
        )
        8 -> PhonePreview(glucoseText, trend, glucoseColor)
        9, 10 -> PhonePreview("$glucoseText$trend", "Dot-Graph", glucoseColor)

        11 -> PhonePreview(number(state?.insulin?.totalIob, 2, "1.20") + "U", "")
        12 -> PhonePreview(number(state?.insulin?.bolusIob, 2, "0.80") + " U", "Bolus")
        13 -> PhonePreview(number(state?.insulin?.basalIob, 2, "0.40") + " U", "Basal IOB")
        14 -> PhonePreview(number(state?.carbs?.cobGrams, 0, "15") + "g", "")
        15 -> PhonePreview(
            "${number(state?.insulin?.totalIob, 1, "1.2")} U · " +
                "${number(state?.carbs?.cobGrams, 0, "15")} g",
            "IOB · COB",
        )
        16 -> PhonePreview(
            number(state?.basal?.currentUnitsPerHour, 2, "0.80") + " U/h",
            "Basal",
        )
        17 -> PhonePreview(
            state?.basal?.displayText
                ?: state?.basal?.tempPercent?.let { "$it%" }
                ?: "120%",
            "Temp basal",
        )
        18 -> PhonePreview(
            TherapyDisplayFormatter.target(
                state?.target ?: app.aapswear.model.TargetState(80.0, 160.0),
                g?.displayUnit ?: GlucoseUnit.MG_DL,
            ),
            "Target",
        )
        19 -> {
            val loopStatus = state?.loop?.status
            PhonePreview(
                when (loopStatus?.lowercase()) {
                    "enacted" -> "aktiv"
                    "suggested" -> "Vorschlag"
                    null -> "aktiv"
                    else -> loopStatus
                },
                "Loop",
            )
        }
        20 -> PhonePreview(
            state?.loop?.lastRunAtEpochMs?.let {
                TherapyDisplayFormatter.ageMinutes(it, now)
            } ?: "2m",
            "letzter Loop",
        )
        21 -> PhonePreview(state?.profile?.name ?: "Daily 1.9", "Profil")

        22 -> PhonePreview(number(state?.pump?.reservoirUnits, 0, "120") + " U", "Reservoir")
        23 -> PhonePreview(state?.pump?.batteryPercent?.let { "$it%" } ?: "80%", "Pumpenakku")
        24 -> PhonePreview(state?.device?.phoneBatteryPercent?.let { "$it%" } ?: "85%", "Telefonakku")
        25 -> PhonePreview("Lokale Quelle", "Datenquelle")
        26 -> PhonePreview(
            "$glucoseText$trend",
            "IOB ${number(state?.insulin?.totalIob, 1, "1.2")} · " +
                "COB ${number(state?.carbs?.cobGrams, 0, "15")}",
            glucoseColor,
        )
        27 -> PhonePreview(
            "$glucoseText$trend · Δ $delta",
            "IOB ${number(state?.insulin?.totalIob, 1, "1.2")} · " +
                "COB ${number(state?.carbs?.cobGrams, 0, "15")} · Loop",
            glucoseColor,
        )
        else -> PhonePreview("—", "")
    }
}

internal fun complicationPreviewLabel(id: Int, state: TherapyDisplayState?): String {
    val entry = SugarliciousComplicationCatalog.firstOrNull { it.id == id }
    val preview = previewFor(id, state)
    return "${entry?.name ?: "Comp"} ${preview.primary}".take(20)
}

private fun demoHistory(now: Long, hours: Int): List<GlucoseSample> {
    val count = hours * 12
    return (0..count).map { index ->
        val minutesAgo = (count - index) * 5L
        val phase = index % 24
        val value = when {
            phase < 8 -> 105.0 + phase * 4.0
            phase < 16 -> 137.0 - (phase - 8) * 3.0
            else -> 113.0 + (phase - 16) * 2.0
        }
        GlucoseSample(value, now - minutesAgo * 60_000L)
    }
}

private fun unitLabel(unit: GlucoseUnit?): String =
    if (unit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"

private const val PRESET_PREFS = "complication_setup"
private const val PRESET_KEY = "selected_ids"
private const val COMPLICATION_GRAPH_HOURS_KEY = "graph_hours"

internal fun loadComplicationPreset(context: Context): List<Int> =
    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE)
        .getString(PRESET_KEY, null)
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?.filter { id -> SugarliciousComplicationCatalog.any { it.id == id } }
        ?.distinct()
        ?.take(4)
        .orEmpty()

private fun loadComplicationGraphHours(context: Context): Int =
    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE)
        .getInt(COMPLICATION_GRAPH_HOURS_KEY, 3)
        .takeIf { it in listOf(1, 2, 6, 12, 24) } ?: 3

private fun saveComplicationGraphHours(context: Context, hours: Int) {
    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE).edit {
        putInt(COMPLICATION_GRAPH_HOURS_KEY, hours.takeIf { it in listOf(1, 2, 6, 12, 24) } ?: 3)
    }
}

private fun togglePresetEntry(
    context: Context,
    current: List<Int>,
    entryId: Int,
): List<Int> {
    val updated = when {
        entryId in current -> current.filterNot { it == entryId }
        current.size >= 4 -> current
        else -> current + entryId
    }

    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE).edit {
        putString(PRESET_KEY, updated.joinToString(","))
    }

    return updated
}

internal suspend fun syncComplicationPreset(
    context: Context,
    ids: List<Int>,
    graphHours: Int = loadComplicationGraphHours(context),
) {
    val request = PutDataMapRequest.create(WearProtocol.COMPLICATION_PRESET_PATH).apply {
        dataMap.putIntegerArrayList("ids", ArrayList(ids))
        dataMap.putInt("graphHours", graphHours.takeIf { it in listOf(1, 2, 6, 12, 24) } ?: 3)
        dataMap.putLong("updatedAt", System.currentTimeMillis())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(context).putDataItem(request).await()
}
