package app.aapswear.complications

import android.content.Context
import android.net.Uri
import app.aapswear.model.DataCapability
import app.aapswear.model.DataSourceId
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend

/**
 * Optional cross-app bridge to the standalone G7 collector.
 *
 * The provider is signature-protected and read-only. Absence, denial, stale data, or malformed
 * rows always falls back to the existing phone-fed state.
 */
object G7LocalReadingResolver {
    private val readingsUri = Uri.parse("content://app.aapswear.g7watch.readings/readings")
    private const val CURRENT_MAX_MS = 6 * 60_000L

    fun resolve(context: Context, fallback: TherapyDisplayState?, nowEpochMs: Long = System.currentTimeMillis()): TherapyDisplayState? {
        val rows = runCatching {
            context.contentResolver.query(readingsUri, null, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                        if (status != "VALID") continue
                        add(
                            LocalReading(
                                value = cursor.getDouble(cursor.getColumnIndexOrThrow("glucose")),
                                measuredAt = cursor.getLong(cursor.getColumnIndexOrThrow("measured_at")),
                                receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                                delta = cursor.getColumnIndexOrThrow("delta").let { if (cursor.isNull(it)) null else cursor.getDouble(it) },
                                trend = runCatching { Trend.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("trend"))) }.getOrDefault(Trend.UNKNOWN),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        val latest = rows.maxByOrNull(LocalReading::measuredAt) ?: return fallback
        if (latest.measuredAt > nowEpochMs + 5 * 60_000L || nowEpochMs - latest.measuredAt > CURRENT_MAX_MS) return fallback
        val history = rows.sortedBy(LocalReading::measuredAt).map { GlucoseSample(it.value, it.measuredAt, DataSourceId.DEXCOM_G7_WATCH) }
        return TherapyDisplayState(
            source = DataSourceId.DEXCOM_G7_WATCH,
            sourceVersion = "G7 Watch Collector",
            sourceContract = "LOCAL_G7_V1",
            receivedAtEpochMs = latest.receivedAt,
            glucose = GlucoseState(latest.value, GlucoseUnit.MG_DL, latest.trend, latest.measuredAt, latest.delta),
            glucoseHistory = history,
            capabilities = setOf(DataCapability.GLUCOSE, DataCapability.TREND, DataCapability.DELTA),
        )
    }

    private data class LocalReading(val value: Double, val measuredAt: Long, val receivedAt: Long, val delta: Double?, val trend: Trend)
}
