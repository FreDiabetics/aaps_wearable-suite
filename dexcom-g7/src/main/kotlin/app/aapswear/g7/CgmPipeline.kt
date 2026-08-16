package app.aapswear.g7

import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

interface CgmReadingRepository {
    val latestReading: StateFlow<CgmReading?>
    suspend fun insert(reading: CgmReading): Boolean
    suspend fun insertOrIgnore(reading: CgmReading): Boolean = insert(reading)
    suspend fun getLatest(): CgmReading?
    suspend fun getPrevious(): CgmReading?
    suspend fun getRecent(sinceEpochMs: Long): List<CgmReading>
    suspend fun getRange(fromEpochMs: Long, toEpochMs: Long): List<CgmReading>
    suspend fun getUnsynced(limit: Int = 100): List<CgmReading>
    suspend fun markSynced(ids: Set<String>)
}

object CgmReadingIdentity {
    fun create(sensorId: String, sessionId: String, sequenceNumber: Long?, timestampEpochMs: Long): String =
        listOf(sensorId, sessionId, sequenceNumber?.toString() ?: "time", timestampEpochMs.toString()).joinToString(":")
}

object CgmDeltaCalculator {
    private const val MIN_INTERVAL_MS = 2 * 60_000L
    private const val MAX_INTERVAL_MS = 8 * 60_000L

    fun calculate(current: CgmReading, previous: CgmReading?): Double? {
        if (previous == null || current.sensorId != previous.sensorId || current.sessionId != previous.sessionId) return null
        val interval = current.timestampEpochMs - previous.timestampEpochMs
        if (interval !in MIN_INTERVAL_MS..MAX_INTERVAL_MS) return null
        return current.glucoseMgDl - previous.glucoseMgDl
    }
}

object CgmTrendMapper {
    fun fromRate(rateMgDlPerMinute: Double?): Trend = when {
        rateMgDlPerMinute == null -> Trend.UNKNOWN
        rateMgDlPerMinute <= -3.0 -> Trend.DOUBLE_DOWN
        rateMgDlPerMinute <= -2.0 -> Trend.SINGLE_DOWN
        rateMgDlPerMinute <= -1.0 -> Trend.FORTY_FIVE_DOWN
        rateMgDlPerMinute < 1.0 -> Trend.FLAT
        rateMgDlPerMinute < 2.0 -> Trend.FORTY_FIVE_UP
        rateMgDlPerMinute < 3.0 -> Trend.SINGLE_UP
        else -> Trend.DOUBLE_UP
    }

    fun fromG7(value: G7Trend): Trend = Trend.valueOf(value.name)
}

enum class CgmFreshness { CURRENT, STALE, NO_DATA, SIGNAL_LOSS, SENSOR_ERROR }

object CgmFreshnessEvaluator {
    fun evaluate(reading: CgmReading?, nowEpochMs: Long, staleAfterMs: Long = 12 * 60_000L): CgmFreshness = when {
        reading == null -> CgmFreshness.NO_DATA
        reading.status == CgmReadingStatus.SENSOR_ERROR -> CgmFreshness.SENSOR_ERROR
        reading.status != CgmReadingStatus.VALID -> CgmFreshness.NO_DATA
        nowEpochMs - reading.timestampEpochMs > staleAfterMs -> CgmFreshness.SIGNAL_LOSS
        nowEpochMs - reading.timestampEpochMs > 6 * 60_000L -> CgmFreshness.STALE
        else -> CgmFreshness.CURRENT
    }
}

data class CgmGap(val afterReadingId: String, val beforeReadingId: String, val durationMs: Long)

object CgmGapDetector {
    fun detect(readings: List<CgmReading>, expectedIntervalMs: Long = 5 * 60_000L, toleranceMs: Long = 90_000L): List<CgmGap> =
        readings.sortedBy(CgmReading::timestampEpochMs).zipWithNext().mapNotNull { (before, after) ->
            val duration = after.timestampEpochMs - before.timestampEpochMs
            duration.takeIf { it > expectedIntervalMs + toleranceMs }?.let { CgmGap(before.id, after.id, it) }
        }
}

data class CgmSourceCandidate(val source: DataSourceId, val reading: CgmReading?, val enabled: Boolean = true)

object CgmSourceResolver {
    fun resolve(candidates: List<CgmSourceCandidate>, nowEpochMs: Long): CgmReading? {
        val valid = candidates.filter { it.enabled }.mapNotNull(CgmSourceCandidate::reading)
        return valid.firstOrNull {
            it.source == DataSourceId.DEXCOM_G7_WATCH && CgmFreshnessEvaluator.evaluate(it, nowEpochMs) == CgmFreshness.CURRENT
        } ?: valid.filter { CgmFreshnessEvaluator.evaluate(it, nowEpochMs) in setOf(CgmFreshness.CURRENT, CgmFreshness.STALE) }
            .maxByOrNull(CgmReading::timestampEpochMs)
    }
}

fun G7Reading.toCgm(previous: CgmReading? = null): CgmReading {
    val base = CgmReading(
        id = CgmReadingIdentity.create(sensorId, sessionId, sequenceNumber, sensorTimestampEpochMs),
        source = DataSourceId.DEXCOM_G7_WATCH,
        sensorId = sensorId,
        sessionId = sessionId,
        glucoseMgDl = glucoseMgDl,
        timestampEpochMs = sensorTimestampEpochMs,
        receivedAtEpochMs = receivedAtEpochMs,
        trendRateMgDlPerMinute = trendRateMgDlPerMinute,
        predictedMgDl = predictedMgDl,
        sensorAgeSeconds = sensorAgeSeconds,
        sequenceNumber = sequenceNumber,
        status = if (sensorState == G7SensorState.ERROR) CgmReadingStatus.SENSOR_ERROR else CgmReadingStatus.VALID,
    )
    return base.copy(
        deltaMgDl = CgmDeltaCalculator.calculate(base, previous),
        trend = CgmTrendMapper.fromRate(trendRateMgDlPerMinute),
    )
}

enum class CgmAlarmType { VERY_HIGH, HIGH, LOW, VERY_LOW, RAPID_RISE, RAPID_FALL, SIGNAL_LOSS, SENSOR_ERROR }
enum class CgmAlarmState { INACTIVE, ACTIVE, ACKNOWLEDGED, SNOOZED, RESOLVED }
data class CgmAlarm(val type: CgmAlarmType, val state: CgmAlarmState, val triggeredAtEpochMs: Long, val readingId: String?, val snoozedUntilEpochMs: Long? = null)
data class CgmAlarmSettings(
    val veryHighThreshold: Double,
    val highThreshold: Double,
    val lowThreshold: Double,
    val veryLowThreshold: Double,
    val rapidRiseThreshold: Double,
    val rapidFallThreshold: Double,
    val signalLossMinutes: Int,
    val hysteresisMgDl: Double = 5.0,
    val veryHighEnabled: Boolean = true,
    val highEnabled: Boolean = true,
    val lowEnabled: Boolean = true,
    val veryLowEnabled: Boolean = true,
    val rapidRiseEnabled: Boolean = true,
    val rapidFallEnabled: Boolean = true,
    val signalLossEnabled: Boolean = true,
    val sensorErrorEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val repeatEnabled: Boolean = true,
    val repeatIntervalMinutes: Int = 15,
) {
    init {
        require(veryHighThreshold > highThreshold)
        require(lowThreshold > veryLowThreshold)
        require(hysteresisMgDl >= 0.0)
        require(signalLossMinutes > 0)
        require(repeatIntervalMinutes > 0)
    }
}

object CgmAlarmEngine {
    fun evaluate(reading: CgmReading?, current: Map<CgmAlarmType, CgmAlarm>, settings: CgmAlarmSettings, nowEpochMs: Long): Map<CgmAlarmType, CgmAlarm> {
        val next = current.toMutableMap()
        fun update(type: CgmAlarmType, active: Boolean) {
            val old = next[type]
            if (active && old?.state !in setOf(CgmAlarmState.ACTIVE, CgmAlarmState.ACKNOWLEDGED, CgmAlarmState.SNOOZED)) {
                next[type] = CgmAlarm(type, CgmAlarmState.ACTIVE, nowEpochMs, reading?.id)
            } else if (!active && old != null && old.state != CgmAlarmState.RESOLVED) {
                next[type] = old.copy(state = CgmAlarmState.RESOLVED)
            }
        }
        fun wasActive(type: CgmAlarmType): Boolean =
            next[type]?.state in setOf(CgmAlarmState.ACTIVE, CgmAlarmState.ACKNOWLEDGED, CgmAlarmState.SNOOZED)

        val value = reading?.glucoseMgDl
        val veryHigh = settings.veryHighEnabled && value != null &&
            (value >= settings.veryHighThreshold || (wasActive(CgmAlarmType.VERY_HIGH) && value >= settings.veryHighThreshold - settings.hysteresisMgDl))
        val high = settings.highEnabled && !veryHigh && value != null &&
            (value >= settings.highThreshold || (wasActive(CgmAlarmType.HIGH) && value >= settings.highThreshold - settings.hysteresisMgDl))
        val veryLow = settings.veryLowEnabled && value != null &&
            (value <= settings.veryLowThreshold || (wasActive(CgmAlarmType.VERY_LOW) && value <= settings.veryLowThreshold + settings.hysteresisMgDl))
        val low = settings.lowEnabled && !veryLow && value != null &&
            (value <= settings.lowThreshold || (wasActive(CgmAlarmType.LOW) && value <= settings.lowThreshold + settings.hysteresisMgDl))

        update(CgmAlarmType.VERY_HIGH, veryHigh)
        update(CgmAlarmType.HIGH, high)
        update(CgmAlarmType.VERY_LOW, veryLow)
        update(CgmAlarmType.LOW, low)
        update(CgmAlarmType.RAPID_RISE, settings.rapidRiseEnabled && reading?.trendRateMgDlPerMinute?.let { it >= settings.rapidRiseThreshold } == true)
        update(CgmAlarmType.RAPID_FALL, settings.rapidFallEnabled && reading?.trendRateMgDlPerMinute?.let { it <= -abs(settings.rapidFallThreshold) } == true)
        update(CgmAlarmType.SIGNAL_LOSS, settings.signalLossEnabled && (reading == null || nowEpochMs - reading.timestampEpochMs >= settings.signalLossMinutes * 60_000L))
        update(CgmAlarmType.SENSOR_ERROR, settings.sensorErrorEnabled && reading?.status == CgmReadingStatus.SENSOR_ERROR)
        return next
    }

    fun acknowledge(alarm: CgmAlarm): CgmAlarm = alarm.copy(state = CgmAlarmState.ACKNOWLEDGED)

    fun snooze(alarm: CgmAlarm, untilEpochMs: Long): CgmAlarm {
        require(untilEpochMs > alarm.triggeredAtEpochMs)
        return alarm.copy(state = CgmAlarmState.SNOOZED, snoozedUntilEpochMs = untilEpochMs)
    }

    fun shouldRepeat(alarm: CgmAlarm, settings: CgmAlarmSettings, nowEpochMs: Long): Boolean =
        settings.repeatEnabled &&
            alarm.state == CgmAlarmState.ACTIVE &&
            nowEpochMs - alarm.triggeredAtEpochMs >= settings.repeatIntervalMinutes * 60_000L
}

interface CgmAlarmNotifier {
    fun showNotification(alarm: CgmAlarm)
    fun startVibration(alarm: CgmAlarm)
    fun stopVibration()
    fun playSound(alarm: CgmAlarm) = Unit // TODO(ALARM-SOUNDS)
    fun stopSound() = Unit
}
