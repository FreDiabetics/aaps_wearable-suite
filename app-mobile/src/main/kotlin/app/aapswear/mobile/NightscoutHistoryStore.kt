package app.aapswear.mobile

import android.content.Context
import app.aapswear.model.GlucoseSample

internal object NightscoutHistoryStore {
    private const val PREFS = "nightscout_history"
    private const val KEY_POINTS = "glucose_points"

    fun load(context: Context, nowEpochMs: Long = System.currentTimeMillis()): List<GlucoseSample> {
        val earliest = nowEpochMs - DisplayHistoryAccumulator.WINDOW_MS
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_POINTS, "")
            .orEmpty()
            .lineSequence()
            .mapNotNull { row ->
                val parts = row.split('|', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val value = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                GlucoseSample(value, timestamp)
            }
            .filter { it.measuredAtEpochMs >= earliest && it.valueMgDl.isFinite() }
            .sortedBy { it.measuredAtEpochMs }
            .toList()
            .takeLast(DisplayHistoryAccumulator.MAX_POINTS)
    }

    fun mergeAndSave(
        context: Context,
        incoming: List<GlucoseSample>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<GlucoseSample> {
        val earliest = nowEpochMs - DisplayHistoryAccumulator.WINDOW_MS
        val merged = (load(context, nowEpochMs) + incoming)
            .asSequence()
            .filter { it.measuredAtEpochMs >= earliest && it.valueMgDl.isFinite() }
            .associateBy { it.measuredAtEpochMs }
            .values
            .sortedBy { it.measuredAtEpochMs }
            .takeLast(DisplayHistoryAccumulator.MAX_POINTS)

        val encoded = merged.joinToString("\n") { "${it.measuredAtEpochMs}|${it.valueMgDl}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POINTS, encoded)
            .apply()
        return merged
    }
}
