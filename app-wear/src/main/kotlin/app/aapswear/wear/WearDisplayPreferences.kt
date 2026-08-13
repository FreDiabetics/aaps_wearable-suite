package app.aapswear.wear

import android.content.Context
import app.aapswear.protocol.WatchConfig
import app.aapswear.protocol.WatchGlucoseUnit
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.protocol.WatchGraphStyle

internal data class WearDisplayPreferences(
    val graphHours: Int = 3,
    val showPredictions: Boolean = false,
    val glucoseUnit: WatchGlucoseUnit = WatchGlucoseUnit.AAPS,
    val showTherapyStats: Boolean = true,
    val syncedAtEpochMs: Long = 0L,
    val graphColors: WatchGraphColors = WatchGraphColors(),
    val graphStyle: WatchGraphStyle = WatchGraphStyle(),
) {
    companion object {
        const val PREFS = "watch_display"
        private const val KEY_GRAPH_HOURS = "graph_hours"
        private const val KEY_SHOW_PREDICTIONS = "show_predictions"
        private const val KEY_GLUCOSE_UNIT = "glucose_unit"
        private const val KEY_SHOW_THERAPY_STATS = "show_therapy_stats"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val COLOR_PREFIX = "graph_color_"
        private const val STYLE_DOT_RADIUS = "cgm_dot_radius_dp"
        private const val STYLE_OUTLINE_ENABLED = "cgm_dot_outline_enabled"
        private const val STYLE_OUTLINE_WIDTH = "cgm_dot_outline_width_dp"

        fun read(context: Context): WearDisplayPreferences {
            val preferences =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE,
                )
            val defaults = WatchGraphColors()
            val styleDefaults = WatchGraphStyle()

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
                    preferences.getBoolean(KEY_SHOW_PREDICTIONS, false),
                glucoseUnit = unit,
                showTherapyStats =
                    preferences.getBoolean(KEY_SHOW_THERAPY_STATS, true),
                syncedAtEpochMs =
                    preferences.getLong(KEY_SYNCED_AT, 0L),
                graphColors = WatchGraphColors(
                    graphBackground = preferences.getInt(COLOR_PREFIX + "background", defaults.graphBackground),
                    rangeLow = preferences.getInt(COLOR_PREFIX + "range_low", defaults.rangeLow),
                    rangeInRange = preferences.getInt(COLOR_PREFIX + "range_in", defaults.rangeInRange),
                    rangeHigh = preferences.getInt(COLOR_PREFIX + "range_high", defaults.rangeHigh),
                    cgmLow = preferences.getInt(COLOR_PREFIX + "cgm_low", defaults.cgmLow),
                    cgmInRange = preferences.getInt(COLOR_PREFIX + "cgm_in", defaults.cgmInRange),
                    cgmHigh = preferences.getInt(COLOR_PREFIX + "cgm_high", defaults.cgmHigh),
                    divider = preferences.getInt(COLOR_PREFIX + "divider", defaults.divider),
                    outline = preferences.getInt(COLOR_PREFIX + "outline", defaults.outline),
                    predictionIob = preferences.getInt(COLOR_PREFIX + "prediction_iob", defaults.predictionIob),
                    predictionCob = preferences.getInt(COLOR_PREFIX + "prediction_cob", defaults.predictionCob),
                    predictionUam = preferences.getInt(COLOR_PREFIX + "prediction_uam", defaults.predictionUam),
                    predictionZeroTemp = preferences.getInt(COLOR_PREFIX + "prediction_zero_temp", defaults.predictionZeroTemp),
                ),
                graphStyle = WatchGraphStyle(
                    cgmDotRadiusDp =
                        preferences.getFloat(STYLE_DOT_RADIUS, styleDefaults.cgmDotRadiusDp)
                            .coerceIn(1.5f, 6.0f),
                    cgmDotOutlineEnabled =
                        preferences.getBoolean(STYLE_OUTLINE_ENABLED, styleDefaults.cgmDotOutlineEnabled),
                    cgmDotOutlineWidthDp =
                        preferences.getFloat(STYLE_OUTLINE_WIDTH, styleDefaults.cgmDotOutlineWidthDp)
                            .coerceIn(0.25f, 3.0f),
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
                    config.graphHours.takeIf { it in listOf(3, 6, 12, 24) } ?: 3,
                )
                .putBoolean(KEY_SHOW_PREDICTIONS, config.showPredictions)
                .putString(KEY_GLUCOSE_UNIT, config.glucoseUnit.name)
                .putBoolean(KEY_SHOW_THERAPY_STATS, config.showTherapyStats)
                .putLong(
                    KEY_SYNCED_AT,
                    if (config.sentAtEpochMs > 0L) config.sentAtEpochMs else System.currentTimeMillis(),
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
                .putInt(COLOR_PREFIX + "prediction_iob", config.graphColors.predictionIob)
                .putInt(COLOR_PREFIX + "prediction_cob", config.graphColors.predictionCob)
                .putInt(COLOR_PREFIX + "prediction_uam", config.graphColors.predictionUam)
                .putInt(COLOR_PREFIX + "prediction_zero_temp", config.graphColors.predictionZeroTemp)
                .putFloat(STYLE_DOT_RADIUS, config.graphStyle.cgmDotRadiusDp.coerceIn(1.5f, 6.0f))
                .putBoolean(STYLE_OUTLINE_ENABLED, config.graphStyle.cgmDotOutlineEnabled)
                .putFloat(STYLE_OUTLINE_WIDTH, config.graphStyle.cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f))
                .apply()
        }
    }
}
