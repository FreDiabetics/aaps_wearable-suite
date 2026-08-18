package app.aapswear.mobile

import android.content.Context
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
import app.aapswear.mobile.ui.theme.SugarliciousComponentSize
import app.aapswear.mobile.ui.theme.SugarliciousIconSize
import app.aapswear.mobile.ui.theme.SugarliciousRadius
import app.aapswear.mobile.ui.theme.SugarliciousSpacing
import app.aapswear.model.Freshness
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import java.util.Locale

private fun widgetColor(role: SugarliciousColorRole): ColorProvider =
    DayNightColorProvider(
        day = Color(role.lightArgb),
        night = Color(role.defaultArgb),
    )

private val WidgetBackground = widgetColor(SugarliciousColorRole.BACKGROUND)
private val WidgetCard = widgetColor(SugarliciousColorRole.SURFACE)
private val WidgetPrimary = widgetColor(SugarliciousColorRole.TEXT_PRIMARY)
private val WidgetSecondary = widgetColor(SugarliciousColorRole.TEXT_SECONDARY)
private val WidgetAccent = widgetColor(SugarliciousColorRole.SECONDARY)
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
        provideContent { WidgetSurface(kind, state) }
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
private fun WidgetSurface(kind: WidgetKind, state: TherapyDisplayState?) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(WidgetBackground)
                .cornerRadius(SugarliciousRadius.Navigation)
                .padding(SugarliciousSpacing.Lg)
                .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(
                ImageProvider(R.mipmap.ic_launcher),
                null,
                GlanceModifier.size(SugarliciousIconSize.Default),
            )
            Spacer(GlanceModifier.width(SugarliciousSpacing.Sm))
            Text(
                "Sugarlicious",
                style =
                    TextStyle(
                        color = WidgetPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
            )
        }
        Spacer(GlanceModifier.height(SugarliciousSpacing.Md))
        when (kind) {
            WidgetKind.GLUCOSE -> GlucoseWidgetContent(state)
            WidgetKind.METABOLIC -> MetabolicWidgetContent(state)
            WidgetKind.ACTIVITY -> ActivityWidgetContent(HealthConnectIntegration.snapshot(LocalContext.current))
        }
    }
}

@Composable
private fun GlucoseWidgetContent(state: TherapyDisplayState?) {
    val now = System.currentTimeMillis()
    val glucose = state?.glucose
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, now)
    val value = if (displayable) glucose?.valueMgDl?.let { String.format(Locale.US, "%.0f", it) } ?: "–" else "–"
    val arrow = if (displayable) {
        when (glucose?.trend) {
            Trend.DOUBLE_DOWN -> "⇊"
            Trend.SINGLE_DOWN -> "↓"
            Trend.FORTY_FIVE_DOWN -> "↘"
            Trend.FLAT -> "→"
            Trend.FORTY_FIVE_UP -> "↗"
            Trend.SINGLE_UP -> "↑"
            Trend.DOUBLE_UP -> "⇈"
            else -> ""
        }
    } else {
        ""
    }
    val delta = if (displayable) glucose?.deltaMgDl?.let { String.format(Locale.US, "%+.0f", it) } ?: "–" else "–"
    val status = widgetStatusLine(state, freshness, now)
    val statusColor = when (freshness) {
        Freshness.CURRENT -> WidgetSecondary
        Freshness.DELAYED -> WidgetWarning
        Freshness.STALE, Freshness.NO_DATA -> WidgetError
    }

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(value, style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = 42.sp))
        Spacer(GlanceModifier.width(SugarliciousSpacing.Md))
        Column {
            Text(
                listOf(arrow, delta).filter(String::isNotBlank).joinToString("  "),
                style = TextStyle(color = if (displayable) WidgetAccent else WidgetSecondary, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            )
            Text("mg/dL", style = TextStyle(color = WidgetSecondary, fontSize = 13.sp))
            Text(status, style = TextStyle(color = statusColor, fontSize = 11.sp))
        }
    }
}

@Composable
private fun MetabolicWidgetContent(state: TherapyDisplayState?) {
    val now = System.currentTimeMillis()
    val freshness = TherapyDisplayFormatter.freshness(state, now)
    val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, now)
    val pillWidth =
        ((LocalSize.current.width - SugarliciousSpacing.Xxl - SugarliciousSpacing.Sm) / 2)
            .coerceAtLeast(SugarliciousComponentSize.CompactMetricMinWidth)

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MetricPill(
            "IOB",
            state?.insulin?.totalIob?.takeIf { displayable }?.let { String.format(Locale.US, "%.1f U", it) } ?: "–",
            WidgetIob,
            GlanceModifier.width(pillWidth),
        )
        Spacer(GlanceModifier.width(SugarliciousSpacing.Sm))
        MetricPill(
            "COB",
            state?.carbs?.cobGrams?.takeIf { displayable }?.let { String.format(Locale.US, "%.0f g", it) } ?: "–",
            WidgetCob,
            GlanceModifier.width(pillWidth),
        )
    }
    Spacer(GlanceModifier.height(SugarliciousSpacing.Sm))
    MetricPill(
        if (displayable) "Basal" else widgetStatusLabel(freshness),
        state?.basal?.currentUnitsPerHour?.takeIf { displayable }?.let { String.format(Locale.US, "%.2f U/h", it) } ?: "–",
        if (displayable) WidgetBasal else WidgetError,
        GlanceModifier.fillMaxWidth(),
    )
}

@Composable
private fun MetricPill(label: String, value: String, color: ColorProvider, modifier: GlanceModifier) {
    Column(
        modifier =
            modifier
                .background(WidgetCard)
                .cornerRadius(SugarliciousRadius.Metric)
                .padding(horizontal = SugarliciousSpacing.Md, vertical = 9.dp),
    ) {
        Text(label, style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp))
        Text(value, style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp))
    }
}

@Composable
private fun ActivityWidgetContent(snapshot: HealthConnectSnapshot?) {
    val pillWidth =
        ((LocalSize.current.width - SugarliciousSpacing.Xxl - SugarliciousSpacing.Sm) / 2)
            .coerceAtLeast(SugarliciousComponentSize.CompactMetricMinWidth)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MetricPill("Schritte", snapshot?.steps?.toString() ?: "–", WidgetAccent, GlanceModifier.width(pillWidth))
        Spacer(GlanceModifier.width(SugarliciousSpacing.Sm))
        MetricPill("Puls", snapshot?.latestHeartRate?.let { "$it bpm" } ?: "–", WidgetHeartRate, GlanceModifier.width(pillWidth))
    }
    Spacer(GlanceModifier.height(SugarliciousSpacing.Sm))
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MetricPill("Aktiv", snapshot?.activeMinutes?.let { "$it min" } ?: "–", WidgetBasal, GlanceModifier.width(pillWidth))
        Spacer(GlanceModifier.width(SugarliciousSpacing.Sm))
        MetricPill("Kalorien", snapshot?.activeCaloriesKcal?.let { String.format(Locale.US, "%.0f kcal", it) } ?: "–", WidgetCob, GlanceModifier.width(pillWidth))
    }
}

private fun widgetStatusLine(state: TherapyDisplayState?, freshness: Freshness, now: Long): String {
    val source = TherapyDisplayFormatter.sourceName(state?.source)
    val age = TherapyDisplayFormatter.ageMinutesValue(state?.glucose?.measuredAtEpochMs, now)?.let { "$it min" }
    return listOf(widgetStatusLabel(freshness), source, age.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun widgetStatusLabel(freshness: Freshness): String = when (freshness) {
    Freshness.CURRENT -> "AKTUELL"
    Freshness.DELAYED -> "VERZÖGERT"
    Freshness.STALE -> "VERALTET"
    Freshness.NO_DATA -> "KEINE DATEN"
}
