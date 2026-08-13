package app.aapswear.mobile

import androidx.core.content.edit
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    ComplicationCatalogEntry(
        2,
        "Glucose + Trend",
        ComplicationCategory.GLUCOSE,
        "RANGED",
    ),
    ComplicationCatalogEntry(
        28,
        "Glucose + Trend Text",
        ComplicationCategory.GLUCOSE,
        "SHORT",
    ),
    ComplicationCatalogEntry(
        3,
        "Delta + Alter",
        ComplicationCategory.GLUCOSE,
        "SHORT",
    ),
    ComplicationCatalogEntry(
        9,
        "Glukosegraph 3h",
        ComplicationCategory.GLUCOSE,
        "IMAGE",
    ),
    ComplicationCatalogEntry(
        10,
        "Glukosegraph 6h",
        ComplicationCategory.GLUCOSE,
        "IMAGE",
    ),
    ComplicationCatalogEntry(
        11,
        "IOB",
        ComplicationCategory.THERAPY,
        "SHORT",
    ),
    ComplicationCatalogEntry(
        14,
        "COB",
        ComplicationCategory.THERAPY,
        "SHORT",
    ),
)

@Composable
internal fun ComplicationStudio(
    state: TherapyDisplayState?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(loadComplicationPreset(context)) }
    var expandedCategory by remember { mutableStateOf(ComplicationCategory.GLUCOSE) }
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
                        syncComplicationPreset(context, selected)
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

        ComplicationCategory.entries.forEach { category ->
            val expanded = expandedCategory == category
            CategoryHeader(
                category = category,
                expanded = expanded,
                onClick = {
                    expandedCategory =
                        if (expanded) category else category
                },
            )

            if (expanded) {
                SugarliciousComplicationCatalog
                    .filter { it.category == category }
                    .forEach { entry ->
                        ComplicationCatalogRow(
                            entry = entry,
                            state = state,
                            selected = entry.id in selected,
                            onToggle = {
                                val updated = togglePresetEntry(
                                    context = context,
                                    current = selected,
                                    entryId = entry.id,
                                )
                                if (updated == selected && entry.id !in selected && selected.size >= 4) {
                                    Toast.makeText(
                                        context,
                                        "Das Watch-Preset hat maximal 4 Plätze.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    selected = updated
                                    syncLabel = "Preset geändert · wird synchronisiert"
                                    scope.launch {
                                        runCatching {
                                            syncComplicationPreset(context, updated)
                                        }.onSuccess {
                                            syncLabel = "Preset an Watch synchronisiert"
                                        }.onFailure {
                                            syncLabel = "Lokal gespeichert · Watch-Sync ausstehend"
                                        }
                                    }
                                }
                            },
                        )
                    }
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
private fun MiniGlucosePreview(
    samples: List<GlucoseSample>,
    current: GlucoseSample?,
    hours: Int,
) {
    val now = System.currentTimeMillis()
    val cutoff = now - hours * 60L * 60_000L
    val fallback = demoHistory(now, hours)
    val merged = (samples + listOfNotNull(current))
        .filter { it.measuredAtEpochMs >= cutoff && it.valueMgDl in 20.0..1000.0 }
        .distinctBy { it.measuredAtEpochMs }
        .sortedBy { it.measuredAtEpochMs }
        .ifEmpty { fallback }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val lowY = size.height * ((260.0 - 80.0) / 220.0).toFloat()
        val highY = size.height * ((260.0 - 160.0) / 220.0).toFloat()

        drawRect(
            color = SugarliciousColors.TargetBand,
            topLeft = Offset(0f, highY),
            size = androidx.compose.ui.geometry.Size(
                size.width,
                (lowY - highY).coerceAtLeast(1f),
            ),
        )

        fun x(timestamp: Long): Float =
            (((timestamp - cutoff).toDouble() /
                (hours * 60L * 60_000L).toDouble())
                .coerceIn(0.0, 1.0) * size.width).toFloat()

        fun y(value: Double): Float =
            (((260.0 - value.coerceIn(40.0, 260.0)) / 220.0) *
                size.height).toFloat()

        merged.forEach { sample ->
            drawCircle(
                color = if (sample.valueMgDl in 80.0..160.0) {
                    SugarliciousColors.Green
                } else {
                    SugarliciousColors.Red
                },
                radius = 2.4.dp.toPx(),
                center = Offset(x(sample.measuredAtEpochMs), y(sample.valueMgDl)),
            )
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
        2 -> PhonePreview("$glucoseText$trend", "Glucose + Trend", glucoseColor)
        28 -> PhonePreview("$glucoseText$trend", "Text", glucoseColor)
        3 -> PhonePreview("$delta · $age", "")
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

private fun loadComplicationPreset(context: Context): List<Int> =
    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE)
        .getString(PRESET_KEY, null)
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?.filter { id -> SugarliciousComplicationCatalog.any { it.id == id } }
        ?.distinct()
        ?.take(4)
        .orEmpty()

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
) {
    val request = PutDataMapRequest.create(WearProtocol.COMPLICATION_PRESET_PATH).apply {
        dataMap.putIntegerArrayList("ids", ArrayList(ids))
        dataMap.putLong("updatedAt", System.currentTimeMillis())
    }.asPutDataRequest().setUrgent()

    Wearable.getDataClient(context).putDataItem(request).await()
}
