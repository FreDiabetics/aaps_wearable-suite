package app.aapswear.wear

import android.content.Context
import app.aapswear.protocol.WatchConfig
import app.aapswear.protocol.WatchGlucoseUnit

internal data class WearDisplayPreferences(
    val graphHours: Int = 3,
    val showPredictions: Boolean = true,
    val glucoseUnit: WatchGlucoseUnit = WatchGlucoseUnit.AAPS,
    val showTherapyStats: Boolean = true,
    val syncedAtEpochMs: Long = 0L,
) {
    companion object {
        const val PREFS = "watch_display"
        private const val KEY_GRAPH_HOURS = "graph_hours"
        private const val KEY_SHOW_PREDICTIONS = "show_predictions"
        private const val KEY_GLUCOSE_UNIT = "glucose_unit"
        private const val KEY_SHOW_THERAPY_STATS = "show_therapy_stats"
        private const val KEY_SYNCED_AT = "synced_at"

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
                .apply()
        }
    }
}
