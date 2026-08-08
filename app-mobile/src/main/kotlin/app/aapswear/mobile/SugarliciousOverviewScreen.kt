package app.aapswear.mobile

import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SugarliciousOverviewScreen(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    now: Long,
    callbacks: DashboardCallbacks,
) {
    val unit = preferences.unitFor(state)
    val glucose = state?.glucose
    val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, now)
    val displayable = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
    val density = LocalDensity.current
    val screenHeightDp = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp().value.roundToInt()
    }
    val metrics = DashboardLayoutMetrics.forScreenHeight(screenHeightDp)
    val gap = if (preferences.compact) 6.dp else 9.dp

    val glucoseText = if (displayable && glucose != null) formatGlucose(glucose.valueMgDl, unit) else "—"
    val glucoseColor = when {
        !displayable || glucose == null -> SugarliciousColors.TextPrimary
        glucose.valueMgDl < 80.0 -> SugarliciousColors.GlucoseLow
        glucose.valueMgDl > 160.0 -> SugarliciousColors.GlucoseHigh
        else -> SugarliciousColors.GlucoseInRange
    }
    val delta = if (displayable) formatDelta(glucose?.deltaMgDl, unit) else "—"
    val age = glucose?.measuredAtEpochMs?.let { "${((now - it).coerceAtLeast(0L) / 60_000L)} min" } ?: "—"
    val tirStats = calculateTirStats(state, now)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        GlucoseHeroCard(
            glucoseText = glucoseText,
            glucoseColor = glucoseColor,
            trend = if (displayable) glucose?.trend ?: Trend.UNKNOWN else Trend.UNKNOWN,
            delta = delta,
            
            deltaMgDl = glucose?.deltaMgDl,age = age,
            unitLabel = unitLabel(unit),
            tirStats = tirStats,
            heightDp = maxOf(metrics.summaryTileHeight + 48, 136),
            onClick = callbacks.cycleUnit,
        )

        if (preferences.showDetails) {
            QuickStatsRow(state = state.takeIf { displayable }, heightDp = metrics.statTileHeight)
        }

        GlucoseGraphSurface(
            state = state,
            preferences = preferences,
            chartHeightDp = maxOf(metrics.glucoseChartHeight - 10, 108),
            onHoursClick = callbacks.cycleGraphHours,
        )

        if (preferences.showMetabolicGraph) {
            MetabolicGraphSurface(
                state = state,
                preferences = preferences,
                chartHeightDp = maxOf(
                    metrics.metabolicChartHeight - 18,
                    96,
                ),
            )
        }
    }
}

@Composable
private fun GlucoseHeroCard(
    glucoseText: String,
    glucoseColor: Color,
    trend: Trend,
    delta: String,
    deltaMgDl: Double?,
    age: String,
    unitLabel: String,
    tirStats: TirStats,
    heightDp: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        SugarliciousColors.SurfaceHigh,
                        SugarliciousColors.Surface,
                    ),
                ),
                shape = shape,
            )
            .border(
                1.dp,
                SugarliciousColors.Border.copy(alpha = 0.85f),
                shape,
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 18.dp,
                vertical = 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = glucoseText,
                            color = glucoseColor,
                            fontSize = 42.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.8).sp,
                        )

                        Spacer(Modifier.width(6.dp))

                        TrendIndicator(
                            correctedTrendForDisplay(
                                trend,
                                deltaMgDl,
                            ),
                        )
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "Δ $delta · $age · $unitLabel",
                        color = SugarliciousColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            TirProgressColumn(
                stats = tirStats,
                modifier = Modifier.width(194.dp),
            )
        }
    }
}
private data class TirStats(
    val inRange: Int?,
    val below: Int?,
    val above: Int?,
)

private fun calculateTirStats(
    state: TherapyDisplayState?,
    now: Long,
): TirStats {
    val start = now - 24L * 60L * 60_000L
    val samples = buildList {
        addAll(state?.glucoseHistory.orEmpty())
        state?.glucose?.let {
            add(
                app.aapswear.model.GlucoseSample(
                    valueMgDl = it.valueMgDl,
                    measuredAtEpochMs = it.measuredAtEpochMs,
                ),
            )
        }
    }
        .filter {
            it.measuredAtEpochMs in start..(now + 5 * 60_000L) &&
                it.valueMgDl in 20.0..1000.0
        }
        .associateBy { it.measuredAtEpochMs }
        .values

    if (samples.isEmpty()) {
        return TirStats(null, null, null)
    }

    val total = samples.size.toDouble()
    val below = samples.count { it.valueMgDl < 80.0 }
    val inRange = samples.count { it.valueMgDl in 80.0..160.0 }
    val above = samples.count { it.valueMgDl > 160.0 }

    return TirStats(
        inRange = (inRange / total * 100.0).roundToInt(),
        below = (below / total * 100.0).roundToInt(),
        above = (above / total * 100.0).roundToInt(),
    )
}

@Composable
private fun TirProgressColumn(
    stats: TirStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TirProgress(
            modifier = Modifier.fillMaxWidth(),
            percent = stats.above,
            accent = SugarliciousColors.GlucoseHigh,
        )

        TirProgress(
            modifier = Modifier.fillMaxWidth(),
            percent = stats.inRange,
            accent = SugarliciousColors.GlucoseInRange,
        )

        TirProgress(
            modifier = Modifier.fillMaxWidth(),
            percent = stats.below,
            accent = SugarliciousColors.GlucoseLow,
        )
    }
}

@Composable
private fun TirProgress(
    modifier: Modifier,
    percent: Int?,
    accent: Color,
) {
    val safePercent = (percent ?: 0).coerceIn(0, 100)
    val progress = safePercent / 100f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    SugarliciousColors.SurfaceRaised,
                    RoundedCornerShape(999.dp),
                ),
        ) {
            if (progress > 0f) {
                val fillWidth =
                    (maxWidth * progress)
                        .coerceAtLeast(12.dp)
                        .coerceAtMost(maxWidth)

                Box(
                    modifier = Modifier
                        .width(fillWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            accent,
                            RoundedCornerShape(999.dp),
                        ),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = percent?.let { "$it%" } ?: "—",
            modifier = Modifier.width(42.dp),
            color = SugarliciousColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
private fun correctedTrendForDisplay(
    sourceTrend: Trend,
    deltaMgDl: Double?,
): Trend {
    val delta = deltaMgDl?.takeIf { it.isFinite() } ?: return sourceTrend

    return when {
        delta >= 14.0 -> Trend.DOUBLE_UP
        delta >= 7.0 -> Trend.SINGLE_UP
        delta >= 2.5 -> Trend.FORTY_FIVE_UP
        delta > -2.5 -> Trend.FLAT
        delta > -7.0 -> Trend.FORTY_FIVE_DOWN
        delta > -14.0 -> Trend.SINGLE_DOWN
        else -> Trend.DOUBLE_DOWN
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

    val copies =
        if (
            trend == Trend.DOUBLE_UP ||
            trend == Trend.DOUBLE_DOWN
        ) {
            2
        } else {
            1
        }

    Box(
        modifier = Modifier.size(
            width = 34.dp,
            height = 44.dp,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-4).dp),
        ) {
            repeat(copies) {
                Image(
                    painter = painterResource(
                        R.drawable.ic_trend_arrow,
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(25.dp)
                        .graphicsLayer(
                            rotationZ = rotation,
                            translationX = 0f,
                            translationY = 0f,
                        ),
                )
            }
        }
    }
}
@Composable
private fun QuickStatsRow(
    state: TherapyDisplayState?,
    heightDp: Int,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickStatCard(Modifier.weight(1f), "IOB", formatNumber(state?.insulin?.totalIob, 2), "IE", SugarliciousColors.Blue, heightDp)
        QuickStatCard(Modifier.weight(1f), "COB", formatNumber(state?.carbs?.cobGrams, 0), "g", SugarliciousColors.Orange, heightDp)
        QuickStatCard(Modifier.weight(1f), "BASAL", formatNumber(state?.basal?.currentUnitsPerHour, 2), "IE/h", SugarliciousColors.Secondary, heightDp)
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
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = SugarliciousColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Text(
            text = value,
            color = SugarliciousColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = suffix,
            color = SugarliciousColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
        )
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
            Text(
                "Glukoseverlauf",
                color = SugarliciousColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.clickable(onClick = onHoursClick),
                shape = RoundedCornerShape(999.dp),
                color = SugarliciousColors.Surface,
            ) {
                Text(
                    "${preferences.graphHours} h",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(5.dp))

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
            Text(
                "Insulin & Kohlenhydrate",
                color = SugarliciousColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text("${preferences.graphHours} h", color = SugarliciousColors.TextSecondary, fontSize = 9.sp)
        }

        Spacer(Modifier.height(2.dp))

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
