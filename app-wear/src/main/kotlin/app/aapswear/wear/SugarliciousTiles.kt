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
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.storage.TherapyStateStore
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

private const val TILE_RESOURCES_VERSION = "sugarlicious-3"

abstract class SugarliciousTileService : TileService() {
    protected abstract fun title(state: TherapyDisplayState?): String
    protected abstract fun primary(state: TherapyDisplayState?): String
    protected abstract fun secondary(state: TherapyDisplayState?): String
    protected abstract fun footer(state: TherapyDisplayState?): String

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) = Futures.immediateFuture(
        Tile.Builder()
            .setResourcesVersion(TILE_RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    materialScope(
                        this,
                        requestParams.deviceConfiguration,
                        allowDynamicTheme = false,
                    ) {
                        val state = runBlocking(Dispatchers.IO) {
                            val phoneState = TherapyStateStore(this@SugarliciousTileService).state.first()
                            G7LocalReadingResolver.resolve(this@SugarliciousTileService, phoneState)
                        }
                        primaryLayout(
                            titleSlot = {
                                text(title(state).layoutString, typography = BODY_MEDIUM)
                            },
                            mainSlot = {
                                Column.Builder()
                                    .addContent(text(primary(state).layoutString, typography = DISPLAY_MEDIUM))
                                    .addContent(text(secondary(state).layoutString, typography = BODY_LARGE))
                                    .build()
                            },
                            bottomSlot = {
                                text(footer(state).layoutString, typography = BODY_MEDIUM)
                            },
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
    override fun title(state: TherapyDisplayState?): String {
        val freshness = TherapyDisplayFormatter.freshness(state, System.currentTimeMillis())
        return "SUGARLICIOUS  ·  ${TherapyDisplayFormatter.freshnessLabel(freshness)}"
    }

    override fun primary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        return if (TherapyDisplayFormatter.isGlucoseDisplayable(state, now) && glucose != null) {
            TherapyDisplayFormatter.glucose(glucose)
        } else {
            "–"
        }
    }

    override fun secondary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now) || glucose == null) {
            return when (freshness) {
                Freshness.STALE -> "Keine aktuellen CGM-Daten"
                Freshness.NO_DATA -> "Keine CGM-Daten"
                else -> TherapyDisplayFormatter.freshnessLabel(freshness)
            }
        }
        val arrow = TherapyDisplayFormatter.trendArrow(glucose.trend)
        val delta = TherapyDisplayFormatter.signedDelta(glucose.deltaMgDl, glucose.displayUnit)
        val unit = when (glucose.displayUnit) {
            GlucoseUnit.MMOL_L -> "mmol/L"
            GlucoseUnit.MG_DL -> "mg/dL"
        }
        return listOf(arrow, delta, unit).filter(String::isNotBlank).joinToString("  ")
    }

    override fun footer(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        val source = TherapyDisplayFormatter.sourceName(state?.source)
        val age = TherapyDisplayFormatter.ageMinutesValue(state?.glucose?.measuredAtEpochMs, now)?.let { "vor $it min" }
        val status = if (freshness == Freshness.CURRENT) "" else TherapyDisplayFormatter.freshnessLabel(freshness)
        return listOf(source, age.orEmpty(), status).filter(String::isNotBlank).joinToString("  ·  ")
    }
}

class TherapyTileService : SugarliciousTileService() {
    override fun title(state: TherapyDisplayState?): String {
        val freshness = TherapyDisplayFormatter.freshness(state, System.currentTimeMillis())
        return "SUGARLICIOUS  ·  THERAPIE  ·  ${TherapyDisplayFormatter.freshnessLabel(freshness)}"
    }

    override fun primary(state: TherapyDisplayState?): String {
        val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, System.currentTimeMillis())
        return if (displayable) {
            state?.insulin?.totalIob?.let { String.format(Locale.US, "%.1f U", it) } ?: "– U"
        } else {
            "– U"
        }
    }

    override fun secondary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val freshness = TherapyDisplayFormatter.freshness(state, now)
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now)) {
            return when (freshness) {
                Freshness.STALE -> "IOB · COB · Basal ausgeblendet"
                Freshness.NO_DATA -> "Keine aktuellen Therapiedaten"
                else -> TherapyDisplayFormatter.freshnessLabel(freshness)
            }
        }
        val cob = state?.carbs?.cobGrams?.let { String.format(Locale.US, "COB %.0f g", it) } ?: "COB –"
        val basal = state?.basal?.currentUnitsPerHour?.let { String.format(Locale.US, "Basal %.2f U/h", it) } ?: "Basal –"
        return "$cob  ·  $basal"
    }

    override fun footer(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val source = TherapyDisplayFormatter.sourceName(state?.source)
        val status = TherapyDisplayFormatter.freshnessLabel(TherapyDisplayFormatter.freshness(state, now))
        val loop = state?.loop?.status?.takeIf(String::isNotBlank)
        return listOf(source, status, loop.orEmpty()).filter(String::isNotBlank).joinToString("  ·  ")
    }
}

internal fun requestSugarliciousTileUpdates(context: android.content.Context) {
    val updater = TileService.getUpdater(context)
    updater.requestUpdate(GlucoseTileService::class.java)
    updater.requestUpdate(TherapyTileService::class.java)
}