package app.aapswear.protocol
import app.aapswear.model.*
import kotlin.test.*
class WearProtocolTest {
 @Test fun roundTrip() { val s=TherapyDisplayState(receivedAtEpochMs=2,glucose=GlucoseState(100.0,GlucoseUnit.MG_DL,measuredAtEpochMs=1)); assertEquals(s,WearProtocol.decode(WearProtocol.encode(s))) }
 @Test fun migratesProtocolOneContractStoredInVersionField() {
  val legacy="""{"protocolVersion":1,"state":{"schemaVersion":1,"source":"ANDROID_APS","sourceVersion":"AAPS_EXTENDED_STATUS_V1","receivedAtEpochMs":2}}"""
  val migrated=WearProtocol.decode(legacy.encodeToByteArray())
  assertEquals(TherapyDisplayState.CURRENT_SCHEMA,migrated.schemaVersion)
  assertEquals("AAPS_EXTENDED_STATUS_V1",migrated.sourceContract)
  assertNull(migrated.sourceVersion)
 }
 @Test fun rejectsFutureProtocol() {
  val future="""{"protocolVersion":999,"state":{"receivedAtEpochMs":2}}"""
  assertFailsWith<IllegalArgumentException>{WearProtocol.decode(future.encodeToByteArray())}
 }
}
