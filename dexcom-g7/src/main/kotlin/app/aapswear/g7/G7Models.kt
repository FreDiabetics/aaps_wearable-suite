package app.aapswear.g7

import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import kotlinx.serialization.Serializable

@Serializable
data class CgmReading(
    val id: String,
    val source: DataSourceId,
    val sensorId: String,
    val sessionId: String,
    val glucoseMgDl: Double,
    val timestampEpochMs: Long,
    val receivedAtEpochMs: Long,
    val deltaMgDl: Double? = null,
    val trend: Trend = Trend.UNKNOWN,
    val trendRateMgDlPerMinute: Double? = null,
    val predictedMgDl: Double? = null,
    val sensorAgeSeconds: Long? = null,
    val status: CgmReadingStatus = CgmReadingStatus.VALID,
    val sequenceNumber: Long? = null,
    val displayOnly: Boolean = false,
    val rawSourceTimestamp: Long? = null,
    val sensorStartEpochMs: Long? = null,
    val sensorEndEpochMs: Long? = null,
    val graceEndEpochMs: Long? = null,
    val protocolStatusCode: Int? = null,
    val calibrationStateCode: Int? = null,
    val reservedField: Int? = null,
)

@Serializable enum class CgmReadingStatus { VALID, SENSOR_ERROR, INVALID }
@Serializable enum class G7Trend { DOUBLE_DOWN, SINGLE_DOWN, FORTY_FIVE_DOWN, FLAT, FORTY_FIVE_UP, SINGLE_UP, DOUBLE_UP, UNKNOWN }
@Serializable enum class G7SensorState { UNKNOWN, WARMUP, ACTIVE, GRACE_PERIOD, ENDED, ERROR }
@Serializable enum class G7ConnectionState { DISCONNECTED, SCANNING, CONNECTING, DISCOVERING, CONNECTED }
@Serializable
enum class G7ProtocolState {
    UNINITIALIZED,
    IDLE,
    SCANNING,
    SENSOR_FOUND,
    CONNECTING,
    DISCOVERING,
    DISCOVERING_SERVICES,
    ENABLING_NOTIFICATIONS,
    AUTHENTICATION_START,
    AUTHENTICATION_ROUND_1,
    AUTHENTICATION_ROUND_2,
    AUTHENTICATION_ROUND_3,
    CHALLENGE,
    CERTIFICATE_EXCHANGE,
    KEY_EXCHANGE,
    BONDING,
    AUTHENTICATING,
    AUTHENTICATED,
    REQUESTING_GLUCOSE,
    RECEIVING_GLUCOSE,
    BACKFILL,
    WAITING_FOR_NEXT_READING,
    DISCONNECTED,
    RECOVERING,
    ERROR,
}
@Serializable enum class G7AuthenticationState { UNKNOWN, REQUIRED, AUTHENTICATING, AUTHENTICATED, FAILED }

@Serializable
enum class G7SessionState {
    UNINITIALIZED,
    INITIAL_SETUP,
    AUTHENTICATING,
    AUTHENTICATED,
    READY_FOR_RECONNECT,
    REAUTHENTICATING,
    ACTIVE,
    WAITING_FOR_NEXT_READING,
    RECOVERING,
    REQUIRES_REBOND,
    REQUIRES_FULL_HANDSHAKE,
    USER_INTERVENTION_REQUIRED,
}

@Serializable enum class G7CollectorState { DISABLED, STARTING, SCANNING, CONNECTING, AUTHENTICATING, CONNECTED, RECEIVING, WAITING, RECOVERING, ERROR, USER_ACTION_REQUIRED }
@Serializable enum class CollectorOwner { PHONE, WATCH, TRANSITION_TO_PHONE, TRANSITION_TO_WATCH, UNKNOWN }
@Serializable enum class G7RecoveryStep { NORMAL_RECONNECT, AUTH_RETRY, SHORT_RETRY, BLE_RESCAN, DEVICE_ADDRESS_REFRESH, SESSION_REAUTH, SESSION_RESET, REBOND, FULL_HANDSHAKE, USER_INTERVENTION_REQUIRED }

@Serializable
enum class CollectorDiagnosticStage {
    IDLE,
    WAITING_FOR_WINDOW,
    SCAN_START,
    SCANNING,
    ADVERTISEMENT_FOUND,
    CONNECT_REQUEST,
    GATT_CONNECTED,
    SERVICE_DISCOVERY,
    SERVICE_READY,
    AUTH_START,
    AUTH_CHALLENGE,
    AUTH_RESPONSE,
    AUTH_SUCCESS,
    AUTH_FAILURE,
    GLUCOSE_REQUEST,
    GLUCOSE_RECEIVED,
    VALIDATION,
    STORE,
    SYNC,
    GATT_CLOSE,
    RETRY,
    RECOVERY,
    COMPLETE,
    ERROR,
}

@Serializable
enum class CollectorDiagnosticResult {
    STARTED,
    INFO,
    SUCCESS,
    RECOVERABLE_ERROR,
    FATAL_ERROR,
    CANCELLED,
}

@Serializable
data class CollectorDiagnosticEvent(
    val timestampEpochMs: Long,
    val attemptId: Long,
    val stage: CollectorDiagnosticStage,
    val result: CollectorDiagnosticResult,
    val message: String,
    val errorCode: String? = null,
    val sensorId: String? = null,
    val sequence: Long? = null,
    val durationMs: Long? = null,
)

@Serializable
data class CollectorDiagnosticAttempt(
    val attemptId: Long,
    val startedAtEpochMs: Long,
    val manual: Boolean = false,
    val restart: Boolean = false,
    val completedAtEpochMs: Long? = null,
    val result: CollectorDiagnosticResult = CollectorDiagnosticResult.STARTED,
    val summary: String = "Collection-Versuch läuft",
    val events: List<CollectorDiagnosticEvent> = emptyList(),
)

@Serializable
data class G7Sensor(
    val sensorId: String,
    val sessionId: String? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val sensorStartEpochMs: Long? = null,
    val sensorEndEpochMs: Long? = null,
    val graceEndEpochMs: Long? = null,
    val state: G7SensorState = G7SensorState.UNKNOWN,
)

@Serializable
data class G7Reading(
    val sensorId: String,
    val sessionId: String,
    val sequenceNumber: Long?,
    val glucoseMgDl: Double,
    val sensorTimestampEpochMs: Long,
    val receivedAtEpochMs: Long,
    val trendRateMgDlPerMinute: Double? = null,
    val predictedMgDl: Double? = null,
    val sensorAgeSeconds: Long? = null,
    val sensorState: G7SensorState = G7SensorState.UNKNOWN,
    val displayOnly: Boolean = false,
    val sensorClockSeconds: Long? = null,
    val sensorStartEpochMs: Long? = null,
    val sensorEndEpochMs: Long? = null,
    val graceEndEpochMs: Long? = null,
    val protocolStatusCode: Int? = null,
    val calibrationStateCode: Int? = null,
    val reservedField: Int? = null,
)

@Serializable
data class G7CollectorError(
    val code: String,
    val recoverable: Boolean,
    val occurredAtEpochMs: Long,
    val safeMessage: String,
)

@Serializable
data class G7PersistedState(
    val sensor: G7Sensor? = null,
    val collectorEnabled: Boolean = false,
    val collectorOwner: CollectorOwner = CollectorOwner.UNKNOWN,
    val connectionState: G7ConnectionState = G7ConnectionState.DISCONNECTED,
    val protocolState: G7ProtocolState = G7ProtocolState.UNINITIALIZED,
    val authenticationState: G7AuthenticationState = G7AuthenticationState.UNKNOWN,
    val sessionState: G7SessionState = G7SessionState.UNINITIALIZED,
    val lastReading: CgmReading? = null,
    val lastSuccessfulConnectionEpochMs: Long? = null,
    val nextReconnectEpochMs: Long? = null,
    val retryCount: Int = 0,
    val lastError: G7CollectorError? = null,
    val activeAttemptId: Long? = null,
    val scanStartedAtEpochMs: Long? = null,
    val scanTimeoutAtEpochMs: Long? = null,
    val lastScanAtEpochMs: Long? = null,
    val lastAttemptCompletedAtEpochMs: Long? = null,
)
