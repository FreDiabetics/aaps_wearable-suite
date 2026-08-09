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
 @Test fun graphColorsRoundTripWithWatchConfigSchemaTwo() {
  val colors=WatchGraphColors(
   graphBackground=0xFF101010.toInt(),
   rangeLow=0xFFAA0000.toInt(),
   rangeInRange=0xFF00AA00.toInt(),
   rangeHigh=0xFFAAAA00.toInt(),
   cgmLow=0xFFBB0000.toInt(),
   cgmInRange=0xFF00BB00.toInt(),
   cgmHigh=0xFFBBBB00.toInt(),
   divider=0xFF888888.toInt(),
   outline=0xFF121212.toInt(),
  )
  val config=WatchConfig(graphColors=colors,sentAtEpochMs=123)
  assertEquals(config,WearProtocol.decodeConfig(WearProtocol.encodeConfig(config)))
 }
}
