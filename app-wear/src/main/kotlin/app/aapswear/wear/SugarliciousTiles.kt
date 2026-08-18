package app.aapswear.wear

import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.Typography.BODY_LARGE
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.DISPLAY_MEDIUM
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.argb
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
private const val TILE_GREEN = 0xFF6DE892.toInt()
private const val TILE_CYAN = 0xFF19D7E8.toInt()
private const val TILE_BLUE = 0xFF64BFFF.toInt()
private const val TILE_ORANGE = 0xFFFF9D18.toInt()
private const val TILE_AMBER = 0xFFFFD040.toInt()
private const val TILE_RED = 0xFFFF5C69.toInt()
private const val TILE_BACKGROUND = 0xFF181818.toInt()
private const val TILE_SURFACE = 0xFF242424.toInt()
private const val TILE_SURFACE_HIGH = 0xFF303030.toInt()
private const val TILE_TEXT = 0xFFF5F5F5.toInt()
private const val TILE_TEXT_SECONDARY = 0xFFB5B5B5.toInt()
private const val TILE_BORDER = 0xFF404040.toInt()

private val SugarliciousTileScheme = ColorScheme(
    primary = TILE_GREEN.argb,
    primaryDim = 0xFF54DF30.toInt().argb,
    secondary = TILE_CYAN.argb,
    tertiary = TILE_AMBER.argb,
    surfaceContainerLow = TILE_BACKGROUND.argb,
    surfaceContainer = TILE_SURFACE.argb,
    surfaceContainerHigh = TILE_SURFACE_HIGH.argb,
    onSurface = TILE_TEXT.argb,
    onSurfaceVariant = TILE_TEXT_SECONDARY.argb,
    outline = TILE_BORDER.argb,
    background = TILE_BACKGROUND.argb,
    onBackground = TILE_TEXT.argb,
    error = TILE_RED.argb,
    errorDim = TILE_RED.argb,
)

abstract class SugarliciousTileService : TileService() {
    protected abstract fun title(state: TherapyDisplayState?): String
    protected abstract fun primary(state: TherapyDisplayState?): String
    protected abstract fun primaryColor(state: TherapyDisplayState?): Int
    protected abstract fun secondary(state: TherapyDisplayState?): String
    protected open fun secondaryColor(state: TherapyDisplayState?): Int = TILE_TEXT
    protected abstract fun footer(state: TherapyDisplayState?): String
    protected open fun footerColor(state: TherapyDisplayState?): Int = TILE_TEXT_SECONDARY

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
                        defaultColorScheme = SugarliciousTileScheme,
                    ) {
                        val state = runBlocking(Dispatchers.IO) {
                            val phoneState = TherapyStateStore(this@SugarliciousTileService).state.first()
                            G7LocalReadingResolver.resolve(this@SugarliciousTileService, phoneState)
                        }
                        primaryLayout(
                            titleSlot = {
                                text(
                                    title(state).layoutString,
                                    typography = BODY_MEDIUM,
                                    color = statusColor(TherapyDisplayFormatter.freshness(state, System.currentTimeMillis())).argb,
                                )
                            },
                            mainSlot = {
                                Column.Builder()
                                    .addContent(
                                        text(
                                            primary(state).layoutString,
                                            typography = DISPLAY_MEDIUM,
                                            color = primaryColor(state).argb,
                                        ),
                                    )
                                    .addContent(
                                        text(
                                            secondary(state).layoutString,
                                            typography = BODY_LARGE,
                                            color = secondaryColor(state).argb,
                                        ),
                                    )
                                    .build()
                            },
                            bottomSlot = {
                                text(
                                    footer(state).layoutString,
                                    typography = BODY_MEDIUM,
                                    color = footerColor(state).argb,
                                    maxLines = 2,
                                )
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
    override fun title(state: TherapyDisplayState?): String =
        "SUGARLICIOUS  ·  ${TherapyDisplayFormatter.freshness(state, System.currentTimeMillis()).statusLabel}"

    override fun primary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        return if (TherapyDisplayFormatter.isGlucoseDisplayable(state, now) && glucose != null) {
            TherapyDisplayFormatter.glucose(glucose)
        } else "–"
    }

    override fun primaryColor(state: TherapyDisplayState?): Int {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now) || glucose == null) return TILE_RED
        val low = state.target?.lowMgDl ?: 80.0
        val high = state.target?.highMgDl ?: 160.0
        return when {
            glucose.valueMgDl < low -> TILE_RED
            glucose.valueMgDl > high -> TILE_AMBER
            else -> TILE_GREEN
        }
    }

    override fun secondary(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val glucose = state?.glucose
        if (!TherapyDisplayFormatter.isGlucoseDisplayable(state, now) || glucose == null) {
            return "Keine aktuellen CGM-Daten"
        }
        val arrow = TherapyDisplayFormatter.trendArrow(glucose.trend)
        val delta = TherapyDisplayFormatter.signedDelta(glucose.deltaMgDl, glucose.displayUnit)
        val unit = if (glucose.displayUnit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
        return listOf(arrow, delta, unit).filter(String::isNotBlank).joinToString("  ")
    }

    override fun secondaryColor(state: TherapyDisplayState?): Int =
        if (TherapyDisplayFormatter.isGlucoseDisplayable(state, System.currentTimeMillis())) TILE_TEXT else TILE_TEXT_SECONDARY

    override fun footer(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val source = TherapyDisplayFormatter.sourceName(state?.source)
        val age = TherapyDisplayFormatter.ageMinutesValue(state?.glucose?.measuredAtEpochMs, now)?.let { "vor $it min" }
        return listOf(source, age.orEmpty()).filter(String::isNotBlank).joinToString("  ·  ").ifBlank { "Keine Quelle" }
    }

    override fun footerColor(state: TherapyDisplayState?): Int = TILE_CYAN
}

class TherapyTileService : SugarliciousTileService() {
    override fun title(state: TherapyDisplayState?): String =
        "SUGARLICIOUS  ·  THERAPIE"

    override fun primary(state: TherapyDisplayState?): String {
        val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, System.currentTimeMillis())
        return if (displayable) state?.insulin?.totalIob?.let { String.format(Locale.US, "%.1f U", it) } ?: "– U" else "– U"
    }

    override fun primaryColor(state: TherapyDisplayState?): Int = TILE_BLUE

    override fun secondary(state: TherapyDisplayState?): String {
        val displayable = TherapyDisplayFormatter.isGlucoseDisplayable(state, System.currentTimeMillis())
        if (!displayable) return TherapyDisplayFormatter.freshness(state, System.currentTimeMillis()).statusLabel
        val cob = state?.carbs?.cobGrams?.let { String.format(Locale.US, "COB %.0f g", it) } ?: "COB –"
        val basal = state?.basal?.currentUnitsPerHour?.let { String.format(Locale.US, "Basal %.2f U/h", it) } ?: "Basal –"
        return "$cob  ·  $basal"
    }

    override fun secondaryColor(state: TherapyDisplayState?): Int =
        if (TherapyDisplayFormatter.isGlucoseDisplayable(state, System.currentTimeMillis())) TILE_ORANGE else TILE_RED

    override fun footer(state: TherapyDisplayState?): String {
        val now = System.currentTimeMillis()
        val source = TherapyDisplayFormatter.sourceName(state?.source)
        val status = TherapyDisplayFormatter.freshness(state, now).statusLabel
        val loop = state?.loop?.status?.takeIf(String::isNotBlank)
        return listOf(source, status, loop.orEmpty()).filter(String::isNotBlank).joinToString("  ·  ")
    }

    override fun footerColor(state: TherapyDisplayState?): Int = TILE_TEXT_SECONDARY
}

private fun statusColor(freshness: Freshness): Int = when (freshness) {
    Freshness.CURRENT -> TILE_GREEN
    Freshness.DELAYED -> TILE_AMBER
    Freshness.STALE, Freshness.NO_DATA -> TILE_RED
}

private val Freshness.statusLabel: String
    get() = when (this) {
        Freshness.CURRENT -> "AKTUELL"
        Freshness.DELAYED -> "VERZÖGERT"
        Freshness.STALE -> "VERALTET"
        Freshness.NO_DATA -> "KEINE DATEN"
    }

internal fun requestSugarliciousTileUpdates(context: android.content.Context) {
    val updater = TileService.getUpdater(context)
    updater.requestUpdate(GlucoseTileService::class.java)
    updater.requestUpdate(TherapyTileService::class.java)
}