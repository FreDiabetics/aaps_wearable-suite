package app.aapswear.mobile

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousIconSize
import app.aapswear.mobile.ui.theme.SugarliciousRadius
import app.aapswear.mobile.ui.theme.SugarliciousSpacing
import app.aapswear.model.Freshness
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import java.util.Locale

private fun widgetColor(role: SugarliciousColorRole): ColorProvider =
    DayNightColorProvider(
        day = Color(role.lightArgb),
        night = Color(role.defaultArgb),
    )

private fun blendedWidgetColor(
    surface: SugarliciousColorRole,
    accent: SugarliciousColorRole,
    fraction: Float,
): ColorProvider =
    DayNightColorProvider(
        day = Color(blendArgb(surface.lightArgb, accent.lightArgb, fraction)),
        night = Color(blendArgb(surface.defaultArgb, accent.defaultArgb, fraction)),
    )

private fun blendArgb(base: Int, overlay: Int, fraction: Float): Int {
    val amount = fraction.coerceIn(0f, 1f)
    fun channel(baseChannel: Int, overlayChannel: Int): Int =
        (baseChannel + (overlayChannel - baseChannel) * amount).toInt().coerceIn(0, 255)
    return AndroidColor.argb(
        channel(AndroidColor.alpha(base), AndroidColor.alpha(overlay)),
        channel(AndroidColor.red(base), AndroidColor.red(overlay)),
        channel(AndroidColor.green(base), AndroidColor.green(overlay)),
        channel(AndroidColor.blue(base), AndroidColor.blue(overlay)),
    )
}

private val WidgetSurface = widgetColor(SugarliciousColorRole.SURFACE)
private val WidgetSurfaceHigh = widgetColor(SugarliciousColorRole.SURFACE_HIGH)
private val WidgetPrimary = widgetColor(SugarliciousColorRole.TEXT_PRIMARY)
private val WidgetSecondary = widgetColor(SugarliciousColorRole.TEXT_SECONDARY)
private val WidgetAccent = widgetColor(SugarliciousColorRole.PRIMARY)
private val WidgetCyan = widgetColor(SugarliciousColorRole.SECONDARY)
private val WidgetWarning = widgetColor(SugarliciousColorRole.YELLOW)
private val WidgetError = widgetColor(SugarliciousColorRole.RED)
private val WidgetIob = widgetColor(SugarliciousColorRole.BLUE)
private val WidgetCob = widgetColor(SugarliciousColorRole.ORANGE)
private val WidgetBasal = widgetColor(SugarliciousColorRole.GREEN)
private val WidgetHeartRate = widgetColor(SugarliciousColorRole.RED)

private abstract class SugarliciousWidget : GlanceAppWidget() {
    protected abstract val kind: WidgetKind

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = TherapyStateStore(context).state.first()
        provideContent { WidgetShell(kind, state) }
    }
}

private enum class WidgetKind { GLUCOSE, METABOLIC, ACTIVITY }

private class GlucoseWidget : SugarliciousWidget() { override val kind = WidgetKind.GLUCOSE }
private class MetabolicWidget : SugarliciousWidget() { override val kind = WidgetKind.METABOLIC }
private class ActivityWidget : SugarliciousWidget() { override val kind = WidgetKind.ACTIVITY }

class GlucoseWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GlucoseWidget() }
class MetabolicWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MetabolicWidget() }
class ActivityWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ActivityWidget() }

internal object SugarliciousWidgets {
    suspend fun update(context: Context) {
        GlucoseWidget().updateAll(context)
        MetabolicWidget().updateAll(context)
        ActivityWidget().updateAll(context)
    }
}

@Composable
private fun WidgetShell(kind: WidgetKind, state: TherapyDisplayState?) {
    val size = LocalSize.current
    val compact = size.width < 210.dp || size.height < 130.dp
    val background = if (kind == WidgetKind.GLUCOSE) glucoseSurface(state) else WidgetSurface

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(SugarliciousRadius.Navigation)
                .padding(if (compact) SugarliciousSpacing.Md else SugarliciousSpacing.Lg)
                .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetHeader(kind, state, compact)
        Spacer(GlanceModifier.height(if (compact) SugarliciousSpacing.Sm else SugarliciousSpacing.Md))
        when (kind) {
            WidgetKind.GLUCOSE -> GlucoseWidgetContent(state, compact)
            WidgetKind.METABOLIC -> MetabolicWidgetContent(state, compact)
            WidgetKind.ACTIVITY -> ActivityWidgetContent(HealthConnectIntegration.snapshot(LocalContext.current), compact)
        }
    }
}

@Composable
private fun WidgetHeader(kind: WidgetKind, state: TherapyDisplayState?, compact: Boolean) {
    val now = System.currentTimeMillis()
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            ImageProvider(R.mipmap.ic_launcher),
            null,
            GlanceModifier.size(if (compact) SugarliciousIconSize.Small else SugarliciousIconSize.Default),
        )
        Spacer(GlanceModifier.width(SugarliciousSpacing.Sm))
        Column {
            Text(
                "Sugarlicious",
                style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp),
            )
            if (!compact && kind != WidgetKind.ACTIVITY) {
                Text(
                    widgetStatusLabel(freshness),
                    style = TextStyle(color = statusColor(freshness), fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun GlucoseWidgetContent(state: TherapyDisplayState?, compact: Boolean) {
    val now = System.currentTimeMillis()
    val glucose = state?.glucose
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, now)
    val value = if (displayable && glucose != null) TherapyDisplayFormatter.glucose(glucose) else "–"
    val arrow = if (displayable && glucose != null) TherapyDisplayFormatter.trendArrow(glucose.trend) else ""
    val delta = if (displayable && glucose != null) {
        TherapyDisplayFormatter.signedDelta(glucose.deltaMgDl, glucose.displayUnit).ifBlank { "–" }
    } else "–"
    val unit = when (glucose?.displayUnit) {
        GlucoseUnit.MMOL_L -> "mmol/L"
        GlucoseUnit.MG_DL -> "mg/dL"
        null -> ""
    }

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            value,
            style = TextStyle(
                color = WidgetPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 38.sp else 48.sp,
            ),
        )
        Spacer(GlanceModifier.width(if (compact) SugarliciousSpacing.Sm else SugarliciousSpacing.Md))
        Column {
            Text(
                listOf(arrow, delta).filter(String::isNotBlank).joinToString("  "),
                style = TextStyle(color = if (displayable) WidgetPrimary else WidgetSecondary, fontWeight = FontWeight.Bold, fontSize = if (compact) 17.sp else 21.sp),
            )
            if (unit.isNotBlank()) Text(unit, style = TextStyle(color = WidgetSecondary, fontSize = 12.sp))
            Text(
                widgetStatusLine(state, freshness, now, compact),
                style = TextStyle(color = statusColor(freshness), fontWeight = FontWeight.Medium, fontSize = if (compact) 9.sp else 11.sp),
            )
        }
    }
}

@Composable
private fun MetabolicWidgetContent(state: TherapyDisplayState?, compact: Boolean) {
    val now = System.currentTimeMillis()
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, now)
    val width = ((LocalSize.current.width - SugarliciousSpacing.Xxl) / 3).coerceAtLeast(56.dp)

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        FlatMetric("IOB", state?.insulin?.totalIob?.takeIf { displayable }?.let { String.format(Locale.US, "%.1f U", it) } ?: "–", WidgetIob, GlanceModifier.width(width), compact)
        FlatMetric("COB", state?.carbs?.cobGrams?.takeIf { displayable }?.let { String.format(Locale.US, "%.0f g", it) } ?: "–", WidgetCob, GlanceModifier.width(width), compact)
        FlatMetric("BASAL", state?.basal?.currentUnitsPerHour?.takeIf { displayable }?.let { String.format(Locale.US, "%.2f", it) } ?: "–", WidgetBasal, GlanceModifier.width(width), compact)
    }
    Spacer(GlanceModifier.height(SugarliciousSpacing.Sm))
    Text(
        if (displayable) widgetStatusLine(state, freshness, now, compact) else widgetStatusLabel(freshness),
        style = TextStyle(color = statusColor(freshness), fontWeight = FontWeight.Medium, fontSize = if (compact) 9.sp else 11.sp),
    )
}

@Composable
private fun FlatMetric(label: String, value: String, accent: ColorProvider, modifier: GlanceModifier, compact: Boolean) {
    Column(modifier = modifier.padding(horizontal = SugarliciousSpacing.Xs)) {
        Text(label, style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 11.sp))
        Text(value, style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = if (compact) 16.sp else 19.sp))
    }
}

@Composable
private fun ActivityWidgetContent(snapshot: HealthConnectSnapshot?, compact: Boolean) {
    val width = ((LocalSize.current.width - SugarliciousSpacing.Xxl) / 2).coerceAtLeast(72.dp)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        FlatMetric("SCHRITTE", snapshot?.steps?.toString() ?: "–", WidgetCyan, GlanceModifier.width(width), compact)
        FlatMetric("PULS", snapshot?.latestHeartRate?.let { "$it bpm" } ?: "–", WidgetHeartRate, GlanceModifier.width(width), compact)
    }
    Spacer(GlanceModifier.height(SugarliciousSpacing.Md))
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        FlatMetric("AKTIV", snapshot?.activeMinutes?.let { "$it min" } ?: "–", WidgetBasal, GlanceModifier.width(width), compact)
        FlatMetric("KCAL", snapshot?.activeCaloriesKcal?.let { String.format(Locale.US, "%.0f", it) } ?: "–", WidgetCob, GlanceModifier.width(width), compact)
    }
}

private fun glucoseSurface(state: TherapyDisplayState?): ColorProvider {
    val now = System.currentTimeMillis()
    val glucose = state?.glucose
    if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now) || glucose == null) return WidgetSurface
    val low = state.target?.lowMgDl ?: 80.0
    val high = state.target?.highMgDl ?: 160.0
    val role = when {
        glucose.valueMgDl < low -> SugarliciousColorRole.RANGE_LOW
        glucose.valueMgDl > high -> SugarliciousColorRole.RANGE_HIGH
        else -> SugarliciousColorRole.RANGE_IN_RANGE
    }
    return blendedWidgetColor(SugarliciousColorRole.SURFACE, role, 0.24f)
}

private fun widgetStatusLine(state: TherapyDisplayState?, freshness: Freshness, now: Long, compact: Boolean): String {
    val source = TherapyDisplayFormatter.sourceName(state?.source)
    val age = TherapyDisplayFormatter.ageMinutesValue(state?.glucose?.measuredAtEpochMs, now)?.let { "$it min" }
    val parts = if (compact) listOf(source, age.orEmpty()) else listOf(source, age.orEmpty(), widgetStatusLabel(freshness))
    return parts.filter(String::isNotBlank).joinToString(" · ")
}

private fun statusColor(freshness: Freshness): ColorProvider = when (freshness) {
    Freshness.CURRENT -> WidgetAccent
    Freshness.DELAYED -> WidgetWarning
    Freshness.STALE, Freshness.NO_DATA -> WidgetError
}

private fun widgetStatusLabel(freshness: Freshness): String = when (freshness) {
    Freshness.CURRENT -> "AKTUELL"
    Freshness.DELAYED -> "VERZÖGERT"
    Freshness.STALE -> "VERALTET"
    Freshness.NO_DATA -> "KEINE DATEN"
}