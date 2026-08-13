package app.aapswear.protocol

import app.aapswear.model.TherapyDisplayState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WearEnvelope(
    val protocolVersion: Int = CURRENT,
    val state: TherapyDisplayState,
) {
    companion object {
        const val CURRENT = 6
    }
}

@Serializable
enum class WatchGlucoseUnit {
    AAPS,
    MG_DL,
    MMOL_L,
}

@Serializable
data class WatchGraphColors(
    val graphBackground: Int = 0xFF202020.toInt(),
    val rangeLow: Int = 0xFFFF5C69.toInt(),
    val rangeInRange: Int = 0xFF54DF30.toInt(),
    val rangeHigh: Int = 0xFFFFD040.toInt(),
    val cgmLow: Int = 0xFFFF5C69.toInt(),
    val cgmInRange: Int = 0xFF54DF30.toInt(),
    val cgmHigh: Int = 0xFFFFD040.toInt(),
    val divider: Int = 0xFF969696.toInt(),
    val outline: Int = 0xFF000000.toInt(),
    val predictionIob: Int = 0xFF52C1FF.toInt(),
    val predictionCob: Int = 0xFFF4DE00.toInt(),
    val predictionUam: Int = 0xFFFFAE1F.toInt(),
    val predictionZeroTemp: Int = 0xFF30DBDE.toInt(),
)

@Serializable
data class WatchGraphStyle(
    val cgmDotRadiusDp: Float = 2.4f,
    val cgmDotOutlineEnabled: Boolean = true,
    val cgmDotOutlineWidthDp: Float = 0.95f,
)

@Serializable
data class WatchConfig(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val graphHours: Int = 3,
    val showPredictions: Boolean = false,
    val glucoseUnit: WatchGlucoseUnit = WatchGlucoseUnit.AAPS,
    val showTherapyStats: Boolean = true,
    val graphColors: WatchGraphColors = WatchGraphColors(),
    val graphStyle: WatchGraphStyle = WatchGraphStyle(),
    val sentAtEpochMs: Long = 0L,
) {
    companion object {
        const val CURRENT_SCHEMA = 3
    }
}

object WearProtocol {
    const val STATE_PATH = "/aaps-display/v1/state"
    const val REQUEST_PATH = "/aaps-display/v1/request"
    const val WATCH_CONFIG_PATH = "/aaps-display/v1/watch-config"
    const val WATCH_CONFIG_REQUEST_PATH = "/aaps-display/v1/watch-config-request"
    const val COMPLICATION_PRESET_PATH = "/aaps-display/v1/complication-preset"
    const val WATCH_FACE_APPLY_PATH = "/aaps-display/v1/watchface-apply"
    const val WATCH_FACE_STATUS_PATH = "/aaps-display/v1/watchface-status"
    const val CAPABILITY = "aaps_display"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(state: TherapyDisplayState): ByteArray =
        json.encodeToString(WearEnvelope(state = state)).encodeToByteArray()

    fun decode(bytes: ByteArray): TherapyDisplayState {
        val envelope = json.decodeFromString<WearEnvelope>(bytes.decodeToString())
        require(envelope.protocolVersion in 1..WearEnvelope.CURRENT)
        return migrate(envelope.state)
    }

    fun encodeConfig(config: WatchConfig): ByteArray =
        json.encodeToString(config).encodeToByteArray()

    fun decodeConfig(bytes: ByteArray): WatchConfig {
        val decoded = json.decodeFromString<WatchConfig>(bytes.decodeToString())
        return decoded.copy(
            graphHours = decoded.graphHours.takeIf { it in listOf(3, 6, 12, 24) } ?: 3,
            graphStyle = decoded.graphStyle.copy(
                cgmDotRadiusDp = decoded.graphStyle.cgmDotRadiusDp.coerceIn(1.5f, 6.0f),
                cgmDotOutlineWidthDp = decoded.graphStyle.cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f),
            ),
        )
    }

    private fun migrate(state: TherapyDisplayState): TherapyDisplayState {
        if (state.schemaVersion >= TherapyDisplayState.CURRENT_SCHEMA) return state

        val legacyContract =
            state.sourceContract
                ?: state.sourceVersion?.takeIf { it.startsWith("AAPS_") }
        val actualSourceVersion =
            state.sourceVersion?.takeUnless { it.startsWith("AAPS_") }

        return state.copy(
            schemaVersion = TherapyDisplayState.CURRENT_SCHEMA,
            sourceVersion = actualSourceVersion,
            sourceContract = legacyContract,
        )
    }
}
