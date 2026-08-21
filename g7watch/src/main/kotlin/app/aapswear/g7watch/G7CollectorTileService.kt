package app.aapswear.g7watch

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ModifiersBuilders.Transformation
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.ArgbContrast
import app.aapswear.model.Trend
import app.aapswear.model.TrendVisuals
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
    val trend: Trend? = null,
)

internal fun g7TilePresentation(
    reading: CgmReading?,
    colors: app.aapswear.protocol.WatchGraphColors,
    nowEpochMs: Long,
): G7TilePresentation {
    if (reading == null) {
        return G7TilePresentation(
            "—",
            "NO_DATA",
            "Noch kein lokaler Wert",
            colors.graphBackground,
            tileForegroundFor(colors.graphBackground),
        )
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
    val delta = reading.deltaMgDl?.let { String.format(Locale.US, "%+.0f", it) } ?: "—"
    val resolvedBackground = when {
        extremeLow -> EXTREME_LOW_BACKGROUND
        extremeHigh -> EXTREME_HIGH_BACKGROUND
        else -> colors.graphBackground
    }
    return G7TilePresentation(
        value = primary,
        meta = "Δ  $delta   mg/dL",
        age = if (ageMinutes == 0L) "gerade empfangen" else "vor $ageMinutes min",
        background = resolvedBackground,
        foreground = tileForegroundFor(resolvedBackground),
        trend = reading.trend.takeUnless { it == Trend.UNKNOWN },
    )
}

internal fun tileForegroundFor(backgroundArgb: Int): Int =
    if (ArgbContrast.isLight(backgroundArgb, threshold = 0.50)) 0xFF181818.toInt() else 0xFFF5F5F5.toInt()

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
        Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    TREND_RESOURCE_ID,
                    ImageResource.Builder()
                        .setAndroidResourceByResId(
                            AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_trend_arrow)
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )

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
        val primaryRow =
            Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(text(presentation.value, 38f, presentation.foreground, bold = true))
                .apply {
                    val spec = presentation.trend?.let(TrendVisuals::spec)
                    if (spec != null) {
                        addContent(Spacer.Builder().setWidth(dp(8f)).build())
                        repeat(spec.arrowCount) { index ->
                            if (index > 0) addContent(Spacer.Builder().setWidth(dp(2f)).build())
                            addContent(trendImage(spec.rotationDegrees, presentation.foreground))
                        }
                    }
                }
                .build()
        val valueCard =
            Box.Builder()
                .setWidth(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    Modifiers.Builder()
                        .setBackground(
                            Background.Builder()
                                .setColor(argb(0x26000000))
                                .setCorner(Corner.Builder().setRadius(dp(24f)).build())
                                .build(),
                        )
                        .setPadding(Padding.Builder().setAll(dp(12f)).build())
                        .build(),
                )
                .addContent(primaryRow)
                .build()
        val content =
            Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text("G7  ·  WATCH DIRECT", 12f, presentation.foreground, bold = true))
                .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                .addContent(valueCard)
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

    private fun trendImage(rotationDegrees: Float, color: Int): Image =
        Image.Builder()
            .setResourceId(TREND_RESOURCE_ID)
            .setWidth(dp(27f))
            .setHeight(dp(25f))
            .setColorFilter(ColorFilter.Builder().setTint(argb(color)).build())
            .setModifiers(
                Modifiers.Builder()
                    .setTransformation(
                        Transformation.Builder()
                            .setRotation(degrees(rotationDegrees))
                            .build(),
                    )
                    .build(),
            )
            .build()

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
        private const val RESOURCES_VERSION = "g7-collector-2"
        private const val TREND_RESOURCE_ID = "ic_trend"

        fun requestUpdate(context: Context) {
            TileService.getUpdater(context).requestUpdate(G7CollectorTileService::class.java)
        }
    }
}

internal const val EXTREME_LOW_BACKGROUND = 0xFFFF1744.toInt()
internal const val EXTREME_HIGH_BACKGROUND = 0xFFFFD040.toInt()
