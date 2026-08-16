package app.aapswear.g7

import java.util.UUID

object G7GattProfile {
    /** RESEARCH_REFERENCE only; the remaining characteristic map is not yet validated. */
    val researchServiceUuid: UUID = UUID.fromString("f8083532-849e-531c-c594-30f1f86a4ea5")
}

class G7ProtocolResearchRequired(message: String) : UnsupportedOperationException(message)

interface G7PacketParser {
    fun parse(packet: ByteArray, sensor: G7Sensor, receivedAtEpochMs: Long): G7Reading
}

class ResearchG7PacketParser : G7PacketParser {
    override fun parse(packet: ByteArray, sensor: G7Sensor, receivedAtEpochMs: Long): G7Reading {
        throw G7ProtocolResearchRequired("TODO(G7-PROTOCOL): validated G7 glucose packet framing and fields are not implemented")
    }
}

interface G7CryptoEngine {
    fun generateRandom(size: Int): ByteArray
    fun processAuthenticationRound(round: Int, packet: ByteArray): ByteArray
    fun calculateSharedSecret(peerData: ByteArray): ByteArray
    fun aesChallenge(challenge: ByteArray): ByteArray
    fun createCertificateResponse(request: ByteArray): ByteArray
    fun validateCertificate(certificate: ByteArray): Boolean
    fun restoreSession(protectedSessionData: ByteArray): Boolean
    fun resetSession()
}

class ResearchG7CryptoEngine : G7CryptoEngine {
    private fun missing(): Nothing = throw G7ProtocolResearchRequired("TODO(G7-AUTH/G7-CRYPTO): the proprietary G7 authentication exchange is not implemented")
    override fun generateRandom(size: Int): ByteArray = java.security.SecureRandom().let { random -> ByteArray(size).also(random::nextBytes) }
    override fun processAuthenticationRound(round: Int, packet: ByteArray): ByteArray = missing()
    override fun calculateSharedSecret(peerData: ByteArray): ByteArray = missing()
    override fun aesChallenge(challenge: ByteArray): ByteArray = missing()
    override fun createCertificateResponse(request: ByteArray): ByteArray = missing()
    override fun validateCertificate(certificate: ByteArray): Boolean = missing()
    override fun restoreSession(protectedSessionData: ByteArray): Boolean = missing()
    override fun resetSession() = Unit
}

interface G7Scanner { suspend fun findKnownSensor(sensor: G7Sensor?, timeoutMs: Long): G7Sensor? }
interface G7GattClient {
    suspend fun connect(sensor: G7Sensor)
    suspend fun discoverServices()
    suspend fun enableNotifications()
    suspend fun disconnect()
}
interface G7ConnectionManager { suspend fun collectNextReading(sensor: G7Sensor): G7Reading }
interface G7BackfillManager { suspend fun requestBackfill(sensor: G7Sensor, gaps: List<CgmGap>): List<G7Reading> }

interface G7WatchSyncTransport {
    suspend fun sendReadings(readings: List<CgmReading>): Set<String>
}

class G7ReadingSyncManager(private val repository: CgmReadingRepository, private val transport: G7WatchSyncTransport) {
    suspend fun sendPending(batchSize: Int = 100): Int {
        val pending = repository.getUnsynced(batchSize)
        if (pending.isEmpty()) return 0
        val acknowledged = transport.sendReadings(pending)
        repository.markSynced(acknowledged)
        return acknowledged.size
    }
}
