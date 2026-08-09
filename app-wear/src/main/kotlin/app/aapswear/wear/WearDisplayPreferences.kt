package app.aapswear.wear

import android.content.Context
import app.aapswear.protocol.WatchConfig
import app.aapswear.protocol.WatchGlucoseUnit
import app.aapswear.protocol.WatchGraphColors

internal data class WearDisplayPreferences(
    val graphHours: Int = 3,
    val showPredictions: Boolean = true,
    val glucoseUnit: WatchGlucoseUnit = WatchGlucoseUnit.AAPS,
    val showTherapyStats: Boolean = true,
    val syncedAtEpochMs: Long = 0L,
    val graphColors: WatchGraphColors = WatchGraphColors(),
) {
    companion object {
        const val PREFS = "watch_display"
        private const val KEY_GRAPH_HOURS = "graph_hours"
        private const val KEY_SHOW_PREDICTIONS = "show_predictions"
        private const val KEY_GLUCOSE_UNIT = "glucose_unit"
        private const val KEY_SHOW_THERAPY_STATS = "show_therapy_stats"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val COLOR_PREFIX = "graph_color_"

        fun read(context: Context): WearDisplayPreferences {
            val preferences =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE,
                )

            val unit =
                runCatching {
                    WatchGlucoseUnit.valueOf(
                        preferences.getString(
                            KEY_GLUCOSE_UNIT,
                            WatchGlucoseUnit.AAPS.name,
                        ) ?: WatchGlucoseUnit.AAPS.name,
                    )
                }.getOrDefault(WatchGlucoseUnit.AAPS)

            return WearDisplayPreferences(
                graphHours =
                    preferences
                        .getInt(KEY_GRAPH_HOURS, 3)
                        .takeIf { it in listOf(3, 6, 12, 24) }
                        ?: 3,
                showPredictions =
                    preferences.getBoolean(
                        KEY_SHOW_PREDICTIONS,
                        true,
                    ),
                glucoseUnit = unit,
                showTherapyStats =
                    preferences.getBoolean(
                        KEY_SHOW_THERAPY_STATS,
                        true,
                    ),
                syncedAtEpochMs =
                    preferences.getLong(
                        KEY_SYNCED_AT,
                        0L,
                    ),
                graphColors = WatchGraphColors(
                    graphBackground = preferences.getInt(COLOR_PREFIX + "background", WatchGraphColors().graphBackground),
                    rangeLow = preferences.getInt(COLOR_PREFIX + "range_low", WatchGraphColors().rangeLow),
                    rangeInRange = preferences.getInt(COLOR_PREFIX + "range_in", WatchGraphColors().rangeInRange),
                    rangeHigh = preferences.getInt(COLOR_PREFIX + "range_high", WatchGraphColors().rangeHigh),
                    cgmLow = preferences.getInt(COLOR_PREFIX + "cgm_low", WatchGraphColors().cgmLow),
                    cgmInRange = preferences.getInt(COLOR_PREFIX + "cgm_in", WatchGraphColors().cgmInRange),
                    cgmHigh = preferences.getInt(COLOR_PREFIX + "cgm_high", WatchGraphColors().cgmHigh),
                    divider = preferences.getInt(COLOR_PREFIX + "divider", WatchGraphColors().divider),
                    outline = preferences.getInt(COLOR_PREFIX + "outline", WatchGraphColors().outline),
                ),
            )
        }

        fun save(
            context: Context,
            config: WatchConfig,
        ) {
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE,
                )
                .edit()
                .putInt(
                    KEY_GRAPH_HOURS,
                    config.graphHours.takeIf {
                        it in listOf(3, 6, 12, 24)
                    } ?: 3,
                )
                .putBoolean(
                    KEY_SHOW_PREDICTIONS,
                    config.showPredictions,
                )
                .putString(
                    KEY_GLUCOSE_UNIT,
                    config.glucoseUnit.name,
                )
                .putBoolean(
                    KEY_SHOW_THERAPY_STATS,
                    config.showTherapyStats,
                )
                .putLong(
                    KEY_SYNCED_AT,
                    if (config.sentAtEpochMs > 0L) {
                        config.sentAtEpochMs
                    } else {
                        System.currentTimeMillis()
                    },
                )
                .putInt(COLOR_PREFIX + "background", config.graphColors.graphBackground)
                .putInt(COLOR_PREFIX + "range_low", config.graphColors.rangeLow)
                .putInt(COLOR_PREFIX + "range_in", config.graphColors.rangeInRange)
                .putInt(COLOR_PREFIX + "range_high", config.graphColors.rangeHigh)
                .putInt(COLOR_PREFIX + "cgm_low", config.graphColors.cgmLow)
                .putInt(COLOR_PREFIX + "cgm_in", config.graphColors.cgmInRange)
                .putInt(COLOR_PREFIX + "cgm_high", config.graphColors.cgmHigh)
                .putInt(COLOR_PREFIX + "divider", config.graphColors.divider)
                .putInt(COLOR_PREFIX + "outline", config.graphColors.outline)
                .apply()
        }
    }
}
