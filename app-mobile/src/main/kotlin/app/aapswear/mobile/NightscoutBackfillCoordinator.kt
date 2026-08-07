package app.aapswear.mobile

import android.content.Context
import androidx.core.content.edit
import app.aapswear.model.GlucoseSample
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class NightscoutBackfillResult(
    val status: Status,
    val pointCount: Int = 0,
    val message: String? = null,
) {
    enum class Status { OK, SKIPPED, NOT_CONFIGURED, NO_AAPS_STATE, ERROR }
}

internal object NightscoutBackfillCoordinator {
    private const val DIAGNOSTICS = "diagnostics"
    private const val MIN_RETRY_INTERVAL_MS = 5 * 60_000L
    private const val FRESH_SUCCESS_INTERVAL_MS = 30 * 60_000L
    private val mutex = Mutex()

    suspend fun syncIfNeeded(
        context: Context,
        force: Boolean = false,
    ): NightscoutBackfillResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val diagnostics = app.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE)
            val config = NightscoutConfigStore.load(app)
            if (config == null) {
                diagnostics.edit {
                    putString("historyBackfillStatus", "not_configured")
                    putInt("historyBackfillPointCount", NightscoutHistoryStore.load(app).size)
                }
                return@withContext NightscoutBackfillResult(NightscoutBackfillResult.Status.NOT_CONFIGURED)
            }

            val now = System.currentTimeMillis()
            val lastAttempt = diagnostics.getLong("historyBackfillRequestedAt", 0L)
            val lastSuccess = diagnostics.getLong("historyBackfillReceivedAt", 0L)
            val store = TherapyStateStore(app)
            val currentState = store.state.first()
            val cached = NightscoutHistoryStore.load(app, now)
            val combinedHistory = buildList {
                addAll(currentState?.glucoseHistory.orEmpty())
                currentState?.glucose?.let { add(GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)) }
                addAll(cached)
            }.associateBy { it.measuredAtEpochMs }.values.sortedBy { it.measuredAtEpochMs }

            val hasGap = DisplayHistoryAccumulator.hasGap(combinedHistory)
            val hasNear24hCoverage = combinedHistory.let {
                it.size >= 2 && (it.last().measuredAtEpochMs - it.first().measuredAtEpochMs) >= 23 * 60 * 60_000L
            }

            if (!force) {
                if (now - lastAttempt < MIN_RETRY_INTERVAL_MS) {
                    return@withContext NightscoutBackfillResult(
                        NightscoutBackfillResult.Status.SKIPPED,
                        cached.size,
                    )
                }
                if (!hasGap && hasNear24hCoverage && now - lastSuccess < FRESH_SUCCESS_INTERVAL_MS) {
                    return@withContext NightscoutBackfillResult(
                        NightscoutBackfillResult.Status.SKIPPED,
                        cached.size,
                    )
                }
            }

            diagnostics.edit {
                putLong("historyBackfillRequestedAt", now)
                putString("historyBackfillStatus", "requested")
                remove("historyBackfillError")
            }

            runCatching {
                NightscoutClient().fetchLast24Hours(config, now)
            }.fold(
                onSuccess = { entries ->
                    val history = NightscoutHistoryStore.mergeAndSave(
                        app,
                        entries.map { it.sample },
                        now,
                    )

                    val latestState = store.state.first()
                    if (latestState != null) {
                        var merged = DisplayHistoryAccumulator.mergeExternalHistory(
                            latestState,
                            history,
                            now,
                        )
                        val currentGlucose = merged.glucose
                        if (currentGlucose != null && currentGlucose.trend == Trend.UNKNOWN) {
                            val nsDirection = entries
                                .minByOrNull { kotlin.math.abs(it.sample.measuredAtEpochMs - currentGlucose.measuredAtEpochMs) }
                                ?.takeIf {
                                    kotlin.math.abs(it.sample.measuredAtEpochMs - currentGlucose.measuredAtEpochMs) <= 6 * 60_000L
                                }
                                ?.direction
                            val resolved = TrendArrowResolver.resolve(
                                currentGlucose.trend,
                                merged.glucoseHistory,
                                currentGlucose.measuredAtEpochMs,
                                nsDirection,
                            )
                            merged = merged.copy(glucose = currentGlucose.copy(trend = resolved))
                        }
                        store.save(merged)
                    }

                    diagnostics.edit {
                        putLong("historyBackfillReceivedAt", System.currentTimeMillis())
                        putString("historyBackfillStatus", "ok")
                        putInt("historyBackfillPointCount", history.size)
                        remove("historyBackfillError")
                    }
                    NightscoutBackfillResult(
                        NightscoutBackfillResult.Status.OK,
                        history.size,
                    )
                },
                onFailure = { error ->
                    diagnostics.edit {
                        putString("historyBackfillStatus", "error")
                        putString("historyBackfillError", error.javaClass.simpleName)
                        putInt("historyBackfillPointCount", cached.size)
                    }
                    NightscoutBackfillResult(
                        NightscoutBackfillResult.Status.ERROR,
                        cached.size,
                        error.message,
                    )
                },
            )
        }
    }
}
