package app.aapswear.model

import kotlinx.serialization.Serializable

@Serializable enum class DataSourceId { DEXCOM_G7_WATCH, ANDROID_APS, NIGHTSCOUT, XDRIP_PLUS, OTHER }
@Serializable enum class GlucoseUnit { MG_DL, MMOL_L }
@Serializable enum class Trend { DOUBLE_DOWN, SINGLE_DOWN, FORTY_FIVE_DOWN, FLAT, FORTY_FIVE_UP, SINGLE_UP, DOUBLE_UP, UNKNOWN }
@Serializable enum class Freshness { CURRENT, DELAYED, STALE, NO_DATA }
@Serializable enum class DataCapability { GLUCOSE, TREND, DELTA, AVERAGE_DELTA, TARGET, IOB, BOLUS_IOB, BASAL_IOB, SMB, COB, FUTURE_CARBS, BASAL, TEMP_BASAL, TEMP_TARGET, PROFILE, LOOP, PUMP, RESERVOIR, PUMP_BATTERY, PHONE_BATTERY, PREDICTIONS }
@Serializable enum class PredictionKind { IOB, COB, ACOB, UAM, ZERO_TEMP }

@Serializable data class GlucoseState(val valueMgDl: Double, val displayUnit: GlucoseUnit, val trend: Trend = Trend.UNKNOWN, val measuredAtEpochMs: Long, val deltaMgDl: Double? = null, val averageDeltaMgDl: Double? = null)
@Serializable data class GlucoseSample(
    val valueMgDl: Double,
    val measuredAtEpochMs: Long,
    val source: DataSourceId = DataSourceId.ANDROID_APS,
)
@Serializable data class GlucosePrediction(val kind: PredictionKind, val samples: List<GlucoseSample>)
@Serializable data class TherapyHistorySample(
    val measuredAtEpochMs: Long,
    val totalIob: Double? = null,
    val cobGrams: Double? = null,
    /** Effective basal value retained for protocol compatibility. */
    val basalUnitsPerHour: Double? = null,
    val baseBasalUnitsPerHour: Double? = null,
    val tempBasalUnitsPerHour: Double? = null,
    /** Display-only estimate derived from the recent IOB decay when AAPS exposes no activity. */
    val insulinActivityUnitsPerMinute: Double? = null,
    /** Read-only SMB marker normalized from the public AAPS enacted payload. */
    val smbUnits: Double? = null,
)
@Serializable data class InsulinState(val totalIob: Double? = null, val bolusIob: Double? = null, val basalIob: Double? = null)
@Serializable data class CarbState(val cobGrams: Double? = null, val futureCarbsGrams: Double? = null)
@Serializable data class BasalState(val currentUnitsPerHour: Double? = null, val tempAbsoluteUnitsPerHour: Double? = null, val tempPercent: Int? = null, val tempStartedAtEpochMs: Long? = null, val tempDurationMinutes: Long? = null, val tempEndsAtEpochMs: Long? = null, val displayText: String? = null)
@Serializable data class TargetState(val lowMgDl: Double? = null, val highMgDl: Double? = null, val temporary: Boolean = false)
@Serializable data class LoopState(val status: String? = null, val lastRunAtEpochMs: Long? = null, val suggestedAtEpochMs: Long? = null, val enactedAtEpochMs: Long? = null, val suggestedPayload: String? = null, val enactedPayload: String? = null, val smbUnits: Double? = null, val smbAtEpochMs: Long? = null)
@Serializable data class PumpState(val status: String? = null, val reservoirUnits: Double? = null, val batteryPercent: Int? = null)
@Serializable data class DeviceState(val phoneBatteryPercent: Int? = null, val rigBatteryPercent: Int? = null)
@Serializable data class ProfileState(val name: String? = null)

@Serializable data class TherapyDisplayState(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val source: DataSourceId = DataSourceId.ANDROID_APS,
    val sourceVersion: String? = null,
    val sourceContract: String? = null,
    val receivedAtEpochMs: Long,
    val glucose: GlucoseState? = null,
    val glucoseHistory: List<GlucoseSample> = emptyList(),
    val glucosePredictions: List<GlucosePrediction> = emptyList(),
    val therapyHistory: List<TherapyHistorySample> = emptyList(),
    val insulin: InsulinState? = null,
    val carbs: CarbState? = null,
    val basal: BasalState? = null,
    val target: TargetState? = null,
    val loop: LoopState? = null,
    val pump: PumpState? = null,
    val device: DeviceState? = null,
    val profile: ProfileState? = null,
    val capabilities: Set<DataCapability> = emptySet()
) { companion object { const val CURRENT_SCHEMA = 5 } }

object FreshnessPolicy {
    const val CURRENT_MAX_MS = 6 * 60_000L
    const val DELAYED_MAX_MS = 12 * 60_000L
    const val FUTURE_TOLERANCE_MS = 5 * 60_000L
    fun classify(measuredAtEpochMs: Long?, nowEpochMs: Long): Freshness = when {
        measuredAtEpochMs == null -> Freshness.NO_DATA
        measuredAtEpochMs > nowEpochMs + FUTURE_TOLERANCE_MS -> Freshness.NO_DATA
        nowEpochMs - measuredAtEpochMs <= CURRENT_MAX_MS -> Freshness.CURRENT
        nowEpochMs - measuredAtEpochMs <= DELAYED_MAX_MS -> Freshness.DELAYED
        else -> Freshness.STALE
    }
}
