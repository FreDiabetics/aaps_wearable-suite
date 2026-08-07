package app.aapswear.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SugarliciousOverviewScreen(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    preferences: DashboardUiPreferences,
    now: Long,
    callbacks: DashboardCallbacks,
) {
    val unit = preferences.unitFor(state)
    val glucose = state?.glucose
    val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, now)
    val displayable = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
    val metrics = DashboardLayoutMetrics.forScreenHeight(LocalConfiguration.current.screenHeightDp)
    val gap = if (preferences.compact) 6.dp else 9.dp

    val glucoseText = if (displayable && glucose != null) formatGlucose(glucose.valueMgDl, unit) else "—"
    val glucoseColor = when {
        !displayable || glucose == null -> SugarliciousColors.TextPrimary
        glucose.valueMgDl in 80.0..160.0 -> SugarliciousColors.TextPrimary
        else -> SugarliciousColors.Red
    }
    val delta = if (displayable) formatDelta(glucose?.deltaMgDl, unit) else "—"
    val age = glucose?.measuredAtEpochMs?.let { "${((now - it).coerceAtLeast(0L) / 60_000L)} min" } ?: "—"
    val target = TherapyDisplayFormatter.target(state?.target, unit)
    val loopText = when (state?.loop?.status) {
        "enacted" -> "Loop aktiv"
        "suggested" -> "Loop Vorschlag"
        else -> "Loop —"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        GlucoseHeroCard(
            glucoseText = glucoseText,
            glucoseColor = glucoseColor,
            trend = if (displayable) glucose?.trend ?: Trend.UNKNOWN else Trend.UNKNOWN,
            delta = delta,
            age = age,
            unitLabel = unitLabel(unit),
            target = target,
            loopText = loopText,
            freshness = freshness,
            sourceAvailable = diagnostics.sourceVersion != null,
            heightDp = maxOf(metrics.summaryTileHeight + 28, 108),
            onClick = callbacks.cycleUnit,
        )

        if (preferences.showDetails) {
            QuickStatsRow(state = state.takeIf { displayable }, heightDp = metrics.statTileHeight)
        }

        GlucoseGraphSurface(
            state = state,
            preferences = preferences,
            chartHeightDp = maxOf(metrics.glucoseChartHeight - 42, 86),
            onHoursClick = callbacks.cycleGraphHours,
        )

        MetabolicGraphSurface(
            state = state,
            preferences = preferences,
            chartHeightDp = maxOf(metrics.metabolicChartHeight - 32, 82),
        )
    }
}

@Composable
private fun GlucoseHeroCard(
    glucoseText: String,
    glucoseColor: Color,
    trend: Trend,
    delta: String,
    age: String,
    unitLabel: String,
    target: String,
    loopText: String,
    freshness: Freshness,
    sourceAvailable: Boolean,
    heightDp: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val freshnessLabel = when (freshness) {
        Freshness.CURRENT -> "LIVE"
        Freshness.DELAYED -> "VERZÖGERT"
        Freshness.STALE -> "ALT"
        Freshness.NO_DATA -> "KEINE DATEN"
    }
    val freshnessColor = when (freshness) {
        Freshness.CURRENT -> SugarliciousColors.Primary
        Freshness.DELAYED -> SugarliciousColors.Yellow
        Freshness.STALE, Freshness.NO_DATA -> SugarliciousColors.Red
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(SugarliciousColors.SurfaceHigh, SugarliciousColors.Surface),
                ),
                shape = shape,
            )
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.85f), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "GLUKOSE",
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.weight(1f))
                StatusPill(
                    text = if (sourceAvailable) "AAPS" else "AAPS —",
                    foreground = if (sourceAvailable) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
                )
                Spacer(Modifier.width(6.dp))
                StatusPill(text = freshnessLabel, foreground = freshnessColor)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = glucoseText,
                    color = glucoseColor,
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.8).sp,
                )
                Spacer(Modifier.width(5.dp))
                TrendIndicator(trend)
                Spacer(Modifier.weight(1f))
                DeltaPill(delta)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$age · $unitLabel",
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                MetaPill("Ziel $target")
                Spacer(Modifier.width(5.dp))
                MetaPill(loopText)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, foreground: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = SugarliciousColors.SurfaceRaised.copy(alpha = 0.92f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(foreground, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(text = text, color = foreground, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DeltaPill(delta: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = SugarliciousColors.SurfaceRaised) {
        Text(
            text = delta,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = SugarliciousColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = SugarliciousColors.SurfaceHigh) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = SugarliciousColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrendIndicator(trend: Trend) {
    val rotation = when (trend) {
        Trend.DOUBLE_UP, Trend.SINGLE_UP -> -90f
        Trend.FORTY_FIVE_UP -> -45f
        Trend.FLAT -> 0f
        Trend.FORTY_FIVE_DOWN -> 45f
        Trend.SINGLE_DOWN, Trend.DOUBLE_DOWN -> 90f
        Trend.UNKNOWN -> return
    }
    val copies = if (trend == Trend.DOUBLE_UP || trend == Trend.DOUBLE_DOWN) 2 else 1

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        repeat(copies) {
            Image(
                painter = painterResource(R.drawable.ic_trend_arrow),
                contentDescription = null,
                modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = rotation),
            )
        }
    }
}

@Composable
private fun QuickStatsRow(state: TherapyDisplayState?, heightDp: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QuickStatCard(Modifier.weight(1f), "IOB", formatNumber(state?.insulin?.totalIob, 2), "IE", SugarliciousColors.Blue, heightDp)
        QuickStatCard(Modifier.weight(1f), "COB", formatNumber(state?.carbs?.cobGrams, 0), "g", SugarliciousColors.Orange, heightDp)
        QuickStatCard(Modifier.weight(1f), "BASAL", formatNumber(state?.basal?.currentUnitsPerHour, 2), "IE/h", SugarliciousColors.Secondary, heightDp)
        QuickStatCard(Modifier.weight(1f), "PROFIL", state?.profile?.name ?: "—", "Aktuell", SugarliciousColors.Purple, heightDp)
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    suffix: String,
    accent: Color,
    heightDp: Int,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .height(heightDp.dp)
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.72f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(text = title, color = SugarliciousColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(
            text = value,
            color = SugarliciousColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = suffix, color = SugarliciousColors.TextSecondary, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun GlucoseGraphSurface(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    chartHeightDp: Int,
    onHoursClick: () -> Unit,
) {
    OverviewSurface {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Glukoseverlauf", color = SugarliciousColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("CGM · Nightscout Backfill · AAPS Prognose", color = SugarliciousColors.TextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.clickable(onClick = onHoursClick),
                shape = RoundedCornerShape(999.dp),
                color = SugarliciousColors.SurfaceRaised,
            ) {
                Text(
                    "${preferences.graphHours} h",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = SugarliciousColors.Primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(5.dp))
        GraphLegend()
        Spacer(Modifier.height(2.dp))

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(chartHeightDp.dp).clip(RoundedCornerShape(16.dp)),
            factory = { GlucoseDashboardChart(it) },
            update = { it.bind(state, preferences.unitFor(state), preferences.showPredictions, preferences.graphHours) },
        )
    }
}

@Composable
private fun MetabolicGraphSurface(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    chartHeightDp: Int,
) {
    OverviewSurface {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Insulin & Kohlenhydrate", color = SugarliciousColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("IOB und COB im selben Zeitfenster", color = SugarliciousColors.TextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("${preferences.graphHours} h", color = SugarliciousColors.TextSecondary, fontSize = 10.sp)
        }

        Spacer(Modifier.height(4.dp))

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(chartHeightDp.dp).clip(RoundedCornerShape(16.dp)),
            factory = { MetabolicDashboardChart(it) },
            update = { it.bind(state, preferences.graphHours) },
        )
    }
}

@Composable
private fun OverviewSurface(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.72f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
private fun GraphLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendItem("CGM", SugarliciousColors.Green)
        LegendItem("Prognose", SugarliciousColors.Yellow)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 12.dp, height = 6.dp).background(Color(0xFF0A391C), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(5.dp))
            Text("80–160", color = SugarliciousColors.TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, color = SugarliciousColors.TextSecondary, fontSize = 9.sp)
    }
}

private fun formatGlucose(valueMgDl: Double, unit: GlucoseUnit): String =
    if (unit == GlucoseUnit.MMOL_L) String.format(Locale.getDefault(), "%.1f", valueMgDl / 18.0)
    else valueMgDl.roundToInt().toString()

private fun formatDelta(valueMgDl: Double?, unit: GlucoseUnit): String {
    if (valueMgDl == null) return "—"
    val converted = if (unit == GlucoseUnit.MMOL_L) valueMgDl / 18.0 else valueMgDl
    val prefix = if (converted >= 0.0) "+" else ""
    val body = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.getDefault(), "%.1f", converted)
    else converted.roundToInt().toString()
    return prefix + body
}

private fun formatNumber(value: Double?, digits: Int): String =
    value?.let { String.format(Locale.getDefault(), "%.${digits}f", it) } ?: "—"

private fun unitLabel(unit: GlucoseUnit): String =
    if (unit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
