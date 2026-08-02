package app.aapswear.protocol
import app.aapswear.model.TherapyDisplayState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
@Serializable data class WearEnvelope(val protocolVersion:Int=CURRENT, val state:TherapyDisplayState) { companion object { const val CURRENT=2 } }
object WearProtocol {
    const val STATE_PATH = "/aaps-display/v1/state"
    const val REQUEST_PATH = "/aaps-display/v1/request"
    const val CAPABILITY = "aaps_display"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(state: TherapyDisplayState) =
        json.encodeToString(WearEnvelope(state = state)).encodeToByteArray()

    fun decode(bytes: ByteArray): TherapyDisplayState {
        val envelope = json.decodeFromString<WearEnvelope>(bytes.decodeToString())
        require(envelope.protocolVersion in 1..WearEnvelope.CURRENT)
        return migrate(envelope.state)
    }

    private fun migrate(state: TherapyDisplayState): TherapyDisplayState {
        if (state.schemaVersion >= TherapyDisplayState.CURRENT_SCHEMA) return state
        val legacyContract = state.sourceContract ?: state.sourceVersion?.takeIf { it.startsWith("AAPS_") }
        val actualSourceVersion = state.sourceVersion?.takeUnless { it.startsWith("AAPS_") }
        return state.copy(
            schemaVersion = TherapyDisplayState.CURRENT_SCHEMA,
            sourceVersion = actualSourceVersion,
            sourceContract = legacyContract,
        )
    }
}
