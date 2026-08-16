package app.aapswear.mobile

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
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
import androidx.glance.appwidget.cornerRadius
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import java.util.Locale

private val WidgetBackground = ColorProvider(0xFF15191D.toInt())
private val WidgetCard = ColorProvider(0xFF22282D.toInt())
private val WidgetPrimary = ColorProvider(0xFFF5F5F5.toInt())
private val WidgetSecondary = ColorProvider(0xFFB8C0C4.toInt())
private val WidgetAccent = ColorProvider(0xFF19D7E8.toInt())

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
        modifier = GlanceModifier.fillMaxSize().background(WidgetBackground).cornerRadius(28.dp).padding(16.dp).clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(ImageProvider(R.mipmap.ic_launcher), null, GlanceModifier.size(24.dp))
            Spacer(GlanceModifier.width(8.dp))
            Text("Sugarlicious", style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp))
        }
        Spacer(GlanceModifier.height(10.dp))
        when (kind) {
            WidgetKind.GLUCOSE -> GlucoseWidgetContent(state)
            WidgetKind.METABOLIC -> MetabolicWidgetContent(state)
            WidgetKind.ACTIVITY -> ActivityWidgetContent(HealthConnectIntegration.snapshot(LocalContext.current))
        }
    }
}

@Composable
private fun GlucoseWidgetContent(state: TherapyDisplayState?) {
    val glucose = state?.glucose
    val value = glucose?.valueMgDl?.let { String.format(Locale.US, "%.0f", it) } ?: "–"
    val arrow = when (glucose?.trend) {
        Trend.DOUBLE_DOWN -> "⇊"; Trend.SINGLE_DOWN -> "↓"; Trend.FORTY_FIVE_DOWN -> "↘"; Trend.FLAT -> "→"
        Trend.FORTY_FIVE_UP -> "↗"; Trend.SINGLE_UP -> "↑"; Trend.DOUBLE_UP -> "⇈"; else -> ""
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(value, style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = 42.sp))
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Text("$arrow  ${glucose?.deltaMgDl?.let { String.format(Locale.US, "%+.0f", it) } ?: "–"}", style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp))
            Text("mg/dL", style = TextStyle(color = WidgetSecondary, fontSize = 13.sp))
        }
    }
}

@Composable
private fun MetabolicWidgetContent(state: TherapyDisplayState?) {
    val pillWidth = ((LocalSize.current.width - 40.dp) / 2).coerceAtLeast(64.dp)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MetricPill("IOB", state?.insulin?.totalIob?.let { String.format(Locale.US, "%.1f U", it) } ?: "–", ColorProvider(0xFF64BFFF.toInt()), GlanceModifier.width(pillWidth))
        Spacer(GlanceModifier.width(8.dp))
        MetricPill("COB", state?.carbs?.cobGrams?.let { String.format(Locale.US, "%.0f g", it) } ?: "–", ColorProvider(0xFFFF9D18.toInt()), GlanceModifier.width(pillWidth))
    }
    Spacer(GlanceModifier.height(8.dp))
    MetricPill("Basal", state?.basal?.currentUnitsPerHour?.let { String.format(Locale.US, "%.2f U/h", it) } ?: "–", ColorProvider(0xFF54DF30.toInt()), GlanceModifier.fillMaxWidth())
}

@Composable
private fun MetricPill(label: String, value: String, color: ColorProvider, modifier: GlanceModifier) {
    Column(modifier = modifier.background(WidgetCard).cornerRadius(18.dp).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp))
        Text(value, style = TextStyle(color = WidgetPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp))
    }
}

@Composable
private fun ActivityWidgetContent(snapshot: HealthConnectSnapshot?) {
    val pillWidth = ((LocalSize.current.width - 40.dp) / 2).coerceAtLeast(64.dp)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MetricPill("Schritte", snapshot?.steps?.toString() ?: "–", WidgetAccent, GlanceModifier.width(pillWidth))
        Spacer(GlanceModifier.width(8.dp))
        MetricPill("Puls", snapshot?.latestHeartRate?.let { "$it bpm" } ?: "–", ColorProvider(0xFFFF6B7A.toInt()), GlanceModifier.width(pillWidth))
    }
    Spacer(GlanceModifier.height(8.dp))
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        MetricPill("Aktiv", snapshot?.activeMinutes?.let { "$it min" } ?: "–", ColorProvider(0xFF54DF30.toInt()), GlanceModifier.width(pillWidth))
        Spacer(GlanceModifier.width(8.dp))
        MetricPill("Kalorien", snapshot?.activeCaloriesKcal?.let { String.format(Locale.US, "%.0f kcal", it) } ?: "–", ColorProvider(0xFFFF9D18.toInt()), GlanceModifier.width(pillWidth))
    }
}
