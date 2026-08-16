package app.aapswear.protocol
import app.aapswear.model.*
import kotlin.test.*
class WearProtocolTest {
 @Test fun diagnosticBatchRoundTripsStableErrorCodes() {
  val event=DiagnosticEvent("id",123L,"WATCH","PREDICTION","PRED-201",DiagnosticSeverity.WARNING,"Cache retained")
  val decoded=WearProtocol.decodeDiagnostics(WearProtocol.encodeDiagnostics(DiagnosticBatch(listOf(event),124L)))
  assertEquals(listOf(event),decoded.events)
 }
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
 @Test fun legacyWatchConfigUsesDefaultBasalColor() {
  val legacy="""{"schemaVersion":4,"uiColors":{}}"""
  val decoded=WearProtocol.decodeConfig(legacy.encodeToByteArray())
  assertEquals(WatchUiColors().basal,decoded.uiColors.basal)
 }
 @Test fun g7SetupAndDataSourceRoundTrip() {
  val command=G7SetupCommand("1234","SERIAL","00386270000000")
  assertEquals(command,WearProtocol.decodeG7Setup(WearProtocol.encodeG7Setup(command)))
  val config=WatchConfig(dataSource=WatchDataSource.DEXCOM_G7_WATCH)
  assertEquals(WatchDataSource.DEXCOM_G7_WATCH,WearProtocol.decodeConfig(WearProtocol.encodeConfig(config)).dataSource)
  assertFailsWith<IllegalArgumentException>{G7SetupCommand("12")}
 }
}
