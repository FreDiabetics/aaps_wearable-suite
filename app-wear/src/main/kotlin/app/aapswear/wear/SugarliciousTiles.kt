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
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import app.aapswear.complications.G7LocalReadingResolver
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

private const val TILE_RESOURCES_VERSION = "sugarlicious-1"

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
    override fun primary(state: TherapyDisplayState?): String = state?.glucose?.valueMgDl?.let { String.format(Locale.US, "%.0f", it) } ?: "–"
    override fun secondary(state: TherapyDisplayState?): String {
        val glucose = state?.glucose ?: return "Keine Daten"
        val arrow = when (glucose.trend) {
            Trend.DOUBLE_DOWN -> "⇊"; Trend.SINGLE_DOWN -> "↓"; Trend.FORTY_FIVE_DOWN -> "↘"; Trend.FLAT -> "→"
            Trend.FORTY_FIVE_UP -> "↗"; Trend.SINGLE_UP -> "↑"; Trend.DOUBLE_UP -> "⇈"; Trend.UNKNOWN -> ""
        }
        val delta = glucose.deltaMgDl?.let { String.format(Locale.US, "%+.0f", it) }.orEmpty()
        return listOf(arrow, delta, "mg/dL").filter(String::isNotBlank).joinToString("  ")
    }
    override fun footer(state: TherapyDisplayState?): String = state?.glucose?.measuredAtEpochMs?.let { measured ->
        val minutes = ((System.currentTimeMillis() - measured).coerceAtLeast(0L) / 60_000L)
        "vor $minutes min"
    } ?: "Watch lokal"
}

class TherapyTileService : SugarliciousTileService() {
    override fun title() = "THERAPIEÜBERSICHT"
    override fun primary(state: TherapyDisplayState?): String = state?.insulin?.totalIob?.let { String.format(Locale.US, "%.1f U", it) } ?: "– U"
    override fun secondary(state: TherapyDisplayState?): String {
        val cob = state?.carbs?.cobGrams?.let { String.format(Locale.US, "COB %.0f g", it) } ?: "COB –"
        val basal = state?.basal?.currentUnitsPerHour?.let { String.format(Locale.US, "Basal %.2f U/h", it) } ?: "Basal –"
        return "$cob  ·  $basal"
    }
    override fun footer(state: TherapyDisplayState?): String = state?.loop?.status?.takeIf(String::isNotBlank) ?: "Nur Anzeige"
}

internal fun requestSugarliciousTileUpdates(context: android.content.Context) {
    val updater = TileService.getUpdater(context)
    updater.requestUpdate(GlucoseTileService::class.java)
    updater.requestUpdate(TherapyTileService::class.java)
}
