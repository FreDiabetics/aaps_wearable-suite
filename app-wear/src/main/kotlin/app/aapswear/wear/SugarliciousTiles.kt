package app.aapswear.wear

import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.Typography.BODY_LARGE
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.DISPLAY_MEDIUM
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import app.aapswear.complications.G7LocalReadingResolver
import app.aapswear.model.Freshness
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

private const val TILE_RESOURCES_VERSION = "sugarlicious-2"

abstract class SugarliciousTileService : TileService() {
    protected abstract fun title(): String
    protected abstract fun primary(state: TherapyDisplayState?): String
    protected abstract fun secondary(state: TherapyDisplayState?): String
    protected abstract fun footer(state: TherapyDisplayState?): String

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) = Futures.immediateFuture(
        Tile.Builder()
            .setResourcesVersion(TILE_RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    materialScope(this, requestParams.deviceConfiguration) {
                        val state = runBlocking(Dispatchers.IO) {
                            val phoneState = TherapyStateStore(this@SugarliciousTileService).state.first()
                            G7LocalReadingResolver.resolve(this@SugarliciousTileService, phoneState)
                        }
                        primaryLayout(
                            titleSlot = { text(title().layoutString, typography = BODY_MEDIUM) },
                            mainSlot = {
                                Column.Builder()
                                    .addContent(text(primary(state).layoutString, typography = DISPLAY_MEDIUM))
                                    .addContent(text(secondary(state).layoutString, typography = BODY_LARGE))
                                    .build()
                            },
                            bottomSlot = { text(footer(state).layoutString, typography = BODY_MEDIUM) },
                        )
                    },
                ),
            )
            .build(),
    )

    override fun onTileResourcesRequest(requestParams: ResourcesRequest) =
        Futures.immediateFuture(Resources.Builder().setVersion(TILE_RESOURCES_VERSION).build())
}

class GlucoseTileService : SugarliciousTileService() {
    override fun title() = "SUGARLICIOUS"

    override fun primary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        return if (TherapyDisplayFormatter.isGlucoseDisplayable(state, now)) {
            glucose?.valueMgDl?.let { String.format(Locale.US, "%.0f", it) } ?: "–"
        } else {
            "–"
        }
    }

    override fun secondary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        val glucose = state?.glucose
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now) || glucose == null) {
            return freshness.statusLabel
        }

        val arrow = when (glucose.trend) {
            Trend.DOUBLE_DOWN -> "⇊"
            Trend.SINGLE_DOWN -> "↓"
            Trend.FORTY_FIVE_DOWN -> "↘"
            Trend.FLAT -> "→"
            Trend.FORTY_FIVE_UP -> "↗"
            Trend.SINGLE_UP -> "↑"
            Trend.DOUBLE_UP -> "⇈"
            Trend.UNKNOWN -> ""
        }
        val delta = glucose.deltaMgDl?.let { String.format(Locale.US, "%+.0f", it) }.orEmpty()
        val reading = listOf(arrow, delta, "mg/dL").filter(String::isNotBlank).joinToString("  ")
        return if (freshness == Freshness.DELAYED) "$reading  ·  VERZÖGERT" else reading
    }

    override fun footer(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        val source = TherapyDisplayFormatter.sourceName(state?.source)
        val age = TherapyDisplayFormatter.ageMinutesValue(state?.glucose?.measuredAtEpochMs, now)?.let { "vor $it min" }.orEmpty()
        return when (freshness) {
            Freshness.CURRENT -> listOf(source, age).filter(String::isNotBlank).joinToString(" · ")
            Freshness.DELAYED -> listOf(source, age, "verzögert").filter(String::isNotBlank).joinToString(" · ")
            Freshness.STALE -> listOf(source, age, "veraltet").filter(String::isNotBlank).joinToString(" · ")
            Freshness.NO_DATA -> "NO SOURCE"
        }
    }
}

class TherapyTileService : SugarliciousTileService() {
    override fun title() = "THERAPIEÜBERSICHT"

    override fun primary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        return if (TherapyDisplayFormatter.isGlucoseDisplayable(state, now)) {
            state?.insulin?.totalIob?.let { String.format(Locale.US, "%.1f U", it) } ?: "– U"
        } else {
            "– U"
        }
    }

    override fun secondary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now)) return freshness.statusLabel

        val cob = state?.carbs?.cobGrams?.let { String.format(Locale.US, "COB %.0f g", it) } ?: "COB –"
        val basal = state?.basal?.currentUnitsPerHour?.let { String.format(Locale.US, "Basal %.2f U/h", it) } ?: "Basal –"
        return "$cob  ·  $basal"
    }

    override fun footer(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        val source = TherapyDisplayFormatter.sourceName(state?.source)
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now)) {
            return listOf(source, freshness.statusLabel.lowercase()).filter(String::isNotBlank).joinToString(" · ")
        }
        val loop = state?.loop?.status?.takeIf(String::isNotBlank) ?: "Nur Anzeige"
        return listOf(source, loop).filter(String::isNotBlank).joinToString(" · ")
    }
}

private val Freshness.statusLabel: String
    get() = when (this) {
        Freshness.CURRENT -> "AKTUELL"
        Freshness.DELAYED -> "VERZÖGERT"
        Freshness.STALE -> "VERALTET · KEINE AKTUELLEN DATEN"
        Freshness.NO_DATA -> "KEINE DATEN"
    }

internal fun requestSugarliciousTileUpdates(context: android.content.Context) {
    val updater = TileService.getUpdater(context)
    updater.requestUpdate(GlucoseTileService::class.java)
    updater.requestUpdate(TherapyTileService::class.java)
}
