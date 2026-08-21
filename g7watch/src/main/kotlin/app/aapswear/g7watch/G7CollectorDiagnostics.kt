package app.aapswear.g7watch

import android.content.Context
import app.aapswear.g7.CollectorDiagnosticAttempt
import app.aapswear.g7.CollectorDiagnosticEvent
import app.aapswear.g7.CollectorDiagnosticResult
import app.aapswear.g7.CollectorDiagnosticStage
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import java.util.Locale
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal class G7CollectorDiagnosticStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(CollectorDiagnosticAttempt.serializer())

    fun begin(
        manual: Boolean,
        restart: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CollectorDiagnosticAttempt = synchronized(lock) {
        val attemptId = preferences.getLong(KEY_COUNTER, 0L) + 1L
        val attempt =
            CollectorDiagnosticAttempt(
                attemptId = attemptId,
                startedAtEpochMs = nowEpochMs,
                manual = manual,
                restart = restart,
                events =
                    listOf(
                        CollectorDiagnosticEvent(
                            timestampEpochMs = nowEpochMs,
                            attemptId = attemptId,
                            stage = CollectorDiagnosticStage.IDLE,
                            result = CollectorDiagnosticResult.STARTED,
                            message =
                                when {
                                    restart -> "Collector-Neustart gestartet"
                                    manual -> "Manuelle Sensorsuche gestartet"
                                    else -> "Automatischer Collection-Versuch gestartet"
                                },
                        ),
                    ),
            )
        save((load() + attempt).takeLast(MAX_ATTEMPTS), attemptId)
        attempt
    }

    fun record(
        attemptId: Long,
        stage: CollectorDiagnosticStage,
        result: CollectorDiagnosticResult = CollectorDiagnosticResult.INFO,
        message: String,
        errorCode: String? = null,
        sensorId: String? = null,
        sequence: Long? = null,
        durationMs: Long? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ) = synchronized(lock) {
        val attempts = load().toMutableList()
        val index = attempts.indexOfFirst { it.attemptId == attemptId }
        if (index < 0) return@synchronized
        val current = attempts[index]
        val event =
            CollectorDiagnosticEvent(
                timestampEpochMs = nowEpochMs,
                attemptId = attemptId,
                stage = stage,
                result = result,
                message = sanitizeDiagnosticText(message),
                errorCode = errorCode?.take(40),
                sensorId = sensorId?.take(80),
                sequence = sequence,
                durationMs = durationMs,
            )
        val terminal = stage == CollectorDiagnosticStage.COMPLETE || stage == CollectorDiagnosticStage.ERROR
        attempts[index] =
            current.copy(
                completedAtEpochMs = if (terminal) nowEpochMs else current.completedAtEpochMs,
                result = if (terminal) result else current.result,
                summary = if (terminal) event.message else current.summary,
                events = (current.events + event).takeLast(MAX_EVENTS_PER_ATTEMPT),
            )
        save(attempts.takeLast(MAX_ATTEMPTS), preferences.getLong(KEY_COUNTER, attemptId))
    }

    fun snapshot(): List<CollectorDiagnosticAttempt> = synchronized(lock) {
        load().sortedByDescending(CollectorDiagnosticAttempt::attemptId)
    }

    fun attemptsBetween(fromEpochMs: Long, toEpochMs: Long): List<CollectorDiagnosticAttempt> =
        snapshot()
            .filter { attempt ->
                attempt.startedAtEpochMs <= toEpochMs &&
                    (attempt.completedAtEpochMs ?: attempt.startedAtEpochMs) >= fromEpochMs
            }
            .sortedBy(CollectorDiagnosticAttempt::startedAtEpochMs)

    private fun load(): List<CollectorDiagnosticAttempt> =
        preferences.getString(KEY_ATTEMPTS, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun save(attempts: List<CollectorDiagnosticAttempt>, counter: Long) {
        preferences.edit()
            .putLong(KEY_COUNTER, counter)
            .putString(KEY_ATTEMPTS, json.encodeToString(serializer, attempts))
            .apply()
    }

    private companion object {
        const val PREFERENCES = "g7_collector_attempts"
        const val KEY_COUNTER = "attempt_counter"
        const val KEY_ATTEMPTS = "attempts_v1"
        const val MAX_ATTEMPTS = 50
        const val MAX_EVENTS_PER_ATTEMPT = 100
        val lock = Any()
    }
}

internal data class G7ReadingHistorySummary(
    val count: Int,
    val todayCount: Int,
    val oldestEpochMs: Long?,
    val latestEpochMs: Long?,
    val missedExpectedWindows: Int,
)

internal fun summarizeG7Readings(
    readings: List<CgmReading>,
    startOfDayEpochMs: Long,
): G7ReadingHistorySummary {
    val ordered =
        readings
            .asSequence()
            .filter {
                it.status == CgmReadingStatus.VALID &&
                    it.glucoseMgDl.isFinite() &&
                    it.glucoseMgDl in 20.0..1_000.0
            }
            .sortedBy(CgmReading::timestampEpochMs)
            .toList()
    val missed =
        ordered.groupBy { it.sensorId to it.sessionId }.values.sumOf { stream ->
            stream.zipWithNext().sumOf { (before, after) ->
                val interval = after.timestampEpochMs - before.timestampEpochMs
                if (interval <= EXPECTED_INTERVAL_MS + WINDOW_TOLERANCE_MS) {
                    0
                } else {
                    (interval / EXPECTED_INTERVAL_MS - 1L).coerceAtLeast(1L).toInt()
                }
            }
        }
    return G7ReadingHistorySummary(
        count = ordered.size,
        todayCount = ordered.count { it.timestampEpochMs >= startOfDayEpochMs },
        oldestEpochMs = ordered.firstOrNull()?.timestampEpochMs,
        latestEpochMs = ordered.lastOrNull()?.timestampEpochMs,
        missedExpectedWindows = missed,
    )
}

internal fun maskBluetoothAddress(address: String?): String {
    if (address.isNullOrBlank()) return "—"
    val parts = address.uppercase(Locale.US).split(':')
    return if (parts.size == 6) "••:••:••:••:${parts[4]}:${parts[5]}" else "••••${address.takeLast(4)}"
}

internal fun sanitizeDiagnosticText(value: String): String =
    value
        .replace(
            Regex("(?i)\\b(shared[_ -]?key|pairing[_ -]?secret|auth(?:entication)?[_ -]?(?:key|credential)|pairing[_ -]?code|sensor[_ -]?code)\\s*[:=]\\s*\\S+"),
        ) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        .replace(Regex("(?i)(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")) { matchBluetooth ->
            maskBluetoothAddress(matchBluetooth.value)
        }
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .trim()
        .take(240)

private const val EXPECTED_INTERVAL_MS = 5L * 60_000L
private const val WINDOW_TOLERANCE_MS = 90_000L
