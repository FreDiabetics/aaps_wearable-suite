package app.aapswear.g7watch

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.Trend
import com.google.common.util.concurrent.Futures
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal data class G7TilePresentation(
    val value: String,
    val meta: String,
    val age: String,
    val background: Int,
    val foreground: Int,
)

internal fun g7TilePresentation(
    reading: CgmReading?,
    colors: app.aapswear.protocol.WatchGraphColors,
    nowEpochMs: Long,
): G7TilePresentation {
    if (reading == null) {
        return G7TilePresentation("—", "NO_DATA", "Noch kein lokaler Wert", colors.graphBackground, 0xFFF5F5F5.toInt())
    }
    val ageMs = (nowEpochMs - reading.timestampEpochMs).coerceAtLeast(0L)
    val ageMinutes = ageMs / 60_000L
    if (reading.status == CgmReadingStatus.SENSOR_ERROR) {
        return G7TilePresentation("—", "SENSORFEHLER", "vor $ageMinutes min", colors.graphBackground, colors.cgmLow)
    }
    if (
        reading.status != CgmReadingStatus.VALID ||
        !reading.glucoseMgDl.isFinite() ||
        reading.glucoseMgDl !in 20.0..1_000.0
    ) {
        return G7TilePresentation("—", "NO_DATA", "Ungültiger Sensorwert", colors.graphBackground, colors.cgmLow)
    }
    val freshness = FreshnessPolicy.classify(reading.timestampEpochMs, nowEpochMs)
    if (freshness !in setOf(Freshness.CURRENT, Freshness.DELAYED)) {
        val label = if (freshness == Freshness.STALE) "STALE" else "NO_DATA"
        return G7TilePresentation("—", label, "vor $ageMinutes min", colors.graphBackground, colors.cgmLow)
    }
    val value = reading.glucoseMgDl
    val extremeLow = value <= 40.0
    val extremeHigh = value >= 400.0
    val primary = when {
        extremeLow -> "NIEDRIG"
        extremeHigh -> "HOCH"
        else -> value.toInt().toString()
    }
    val trend = trendArrow(reading.trend)
    val delta = reading.deltaMgDl?.let { String.format(Locale.US, "%+.0f", it) } ?: "—"
    return G7TilePresentation(
        value = "$primary  $trend".trim(),
        meta = "Δ  $delta   mg/dL",
        age = if (ageMinutes == 0L) "gerade empfangen" else "vor $ageMinutes min",
        background = when {
            extremeLow -> colors.rangeLow
            extremeHigh -> colors.rangeHigh
            else -> colors.graphBackground
        },
        foreground = if (extremeHigh) 0xFF181818.toInt() else 0xFFF5F5F5.toInt(),
    )
}

class G7CollectorTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        Futures.immediateFuture(
            Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(60_000L)
                .setTileTimeline(Timeline.fromLayoutElement(layout()))
                .build(),
        )

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val reading =
            runBlocking(Dispatchers.IO) {
                G7ReadingDatabase(this@G7CollectorTileService).let { database ->
                    try {
                        database.getLatest()
                    } finally {
                        database.close()
                    }
                }
            }
        val presentation = g7TilePresentation(reading, G7GraphColorStore(this).read(), System.currentTimeMillis())
        val content =
            Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text("G7 WATCH COLLECTOR", 12f, presentation.foreground, bold = true))
                .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                .addContent(text(presentation.value, 36f, presentation.foreground, bold = true))
                .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                .addContent(text(presentation.meta, 17f, presentation.foreground, bold = true))
                .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                .addContent(text(presentation.age, 13f, presentation.foreground, bold = false))
                .build()
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(presentation.background)).build())
                    .setPadding(Padding.Builder().setAll(dp(18f)).build())
                    .build(),
            )
            .addContent(content)
            .build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): Text =
        Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }
                    .build(),
            )
            .build()

    companion object {
        private const val RESOURCES_VERSION = "g7-collector-1"

        fun requestUpdate(context: Context) {
            TileService.getUpdater(context).requestUpdate(G7CollectorTileService::class.java)
        }
    }
}

private fun trendArrow(trend: Trend): String = when (trend) {
    Trend.DOUBLE_DOWN -> "⇊"
    Trend.SINGLE_DOWN -> "↓"
    Trend.FORTY_FIVE_DOWN -> "↘"
    Trend.FLAT -> "→"
    Trend.FORTY_FIVE_UP -> "↗"
    Trend.SINGLE_UP -> "↑"
    Trend.DOUBLE_UP -> "⇈"
    Trend.UNKNOWN -> "·"
}
