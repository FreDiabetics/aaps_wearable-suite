package app.aapswear.mobile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.CgmCanonicalSource
import app.aapswear.model.CgmQuality
import app.aapswear.model.CgmResolverMemory
import app.aapswear.model.CgmSourceCandidate
import app.aapswear.model.CgmSourceMode
import app.aapswear.model.CgmSourceState
import app.aapswear.model.CanonicalCgmSourceResolver
import app.aapswear.model.CanonicalCgmHistory
import app.aapswear.model.DataCapability
import app.aapswear.model.DataSourceId
import app.aapswear.model.GlucoseSample
import app.aapswear.model.GlucoseState
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.Trend
import app.aapswear.model.TherapyDisplayState
import app.aapswear.storage.PhoneTherapyStateStore
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.mobileG7HistoryDataStore by preferencesDataStore("mobile_g7_backfill")

internal class MobileG7BackfillStore(private val context: Context) {
    private val key = stringPreferencesKey("readings_v1")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(CgmReading.serializer())

    suspend fun snapshot(): List<CgmReading> =
        decode(context.mobileG7HistoryDataStore.data.first()[key])

    /** Persists valid Watch readings before acknowledging them to the Watch. */
    suspend fun merge(
        incoming: List<CgmReading>,
        nowEpochMs: Long,
    ): Set<String> {
        val accepted = linkedSetOf<String>()
        context.mobileG7HistoryDataStore.edit { preferences ->
            val current = decode(preferences[key]).toMutableList()
            incoming.forEach { candidate ->
                if (!candidate.isValidBackfill(nowEpochMs)) return@forEach
                accepted += candidate.id

                val duplicate = current.indexOfFirst { stored ->
                    stored.id == candidate.id || stored.sameG7Identity(candidate)
                }
                if (duplicate < 0) {
                    current += candidate
                }
            }

            val normalized =
                current
                    .asSequence()
                    .filter { it.isValidBackfill(nowEpochMs) }
                    .filter { nowEpochMs - it.timestampEpochMs <= RETENTION_MS }
                    .distinctBy(CgmReading::id)
                    .sortedBy(CgmReading::timestampEpochMs)
                    .toList()
                    .takeLast(MAX_READINGS)
            preferences[key] = json.encodeToString(serializer, normalized)
        }
        return accepted
    }

    private fun decode(raw: String?): List<CgmReading> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun CgmReading.isValidBackfill(nowEpochMs: Long): Boolean =
        source == DataSourceId.DEXCOM_G7_WATCH &&
            status == CgmReadingStatus.VALID &&
            id.isNotBlank() && id.length <= 240 &&
            sensorId.isNotBlank() && sensorId.length <= 160 &&
            sessionId.isNotBlank() && sessionId.length <= 160 &&
            glucoseMgDl.isFinite() && glucoseMgDl in 20.0..1_000.0 &&
            timestampEpochMs <= nowEpochMs + FUTURE_TOLERANCE_MS &&
            receivedAtEpochMs >= timestampEpochMs - FUTURE_TOLERANCE_MS &&
            receivedAtEpochMs <= nowEpochMs + FUTURE_TOLERANCE_MS

    private fun CgmReading.sameG7Identity(other: CgmReading): Boolean =
        sensorId == other.sensorId &&
            sessionId == other.sessionId &&
            sequenceNumber != null &&
            sequenceNumber == other.sequenceNumber

    private companion object {
        const val MAX_READINGS = 600
        const val RETENTION_MS = 36L * 60L * 60_000L
        const val FUTURE_TOLERANCE_MS = 5L * 60_000L
    }
}

internal object MobileCanonicalCgmResolver {
    private const val MEMORY_PREFS = "mobile_canonical_cgm_resolver"

    suspend fun resolve(
        context: Context,
        phoneState: TherapyDisplayState?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TherapyDisplayState? {
        val watchReadings = MobileG7BackfillStore(context).snapshot()
        val latestWatch = watchReadings.lastOrNull { it.status == CgmReadingStatus.VALID }
        if (phoneState == null && latestWatch == null) return null

        val mobileCandidate =
            phoneState
                ?.glucose
                ?.takeIf { it.quality == CgmQuality.VALID }
                ?.toCandidate(phoneState.receivedAtEpochMs)
        val watchCandidate = latestWatch?.toCandidate()
        val resolution =
            CanonicalCgmSourceResolver.resolve(
                mobile = mobileCandidate,
                watch = watchCandidate,
                nowEpochMs = nowEpochMs,
                previous = readMemory(context),
                mode = sourceMode(context),
            )
        writeMemory(context, resolution.memory)

        val chosenGlucose =
            when (resolution.canonicalSource) {
                CgmCanonicalSource.MOBILE_AAPS -> phoneState?.glucose
                CgmCanonicalSource.WATCH_G7_DIRECT -> latestWatch?.toGlucoseState()
                CgmCanonicalSource.NONE ->
                    phoneState?.glucose?.takeIf { it.quality == CgmQuality.SENSOR_ERROR }
            }
        val chosenSource =
            when (resolution.canonicalSource) {
                CgmCanonicalSource.MOBILE_AAPS -> phoneState?.source ?: DataSourceId.OTHER
                CgmCanonicalSource.WATCH_G7_DIRECT -> DataSourceId.DEXCOM_G7_WATCH
                CgmCanonicalSource.NONE -> phoneState?.source ?: DataSourceId.OTHER
            }
        val capabilities =
            if (chosenGlucose?.quality != CgmQuality.VALID) {
                phoneState?.capabilities.orEmpty() -
                    setOf(
                        DataCapability.GLUCOSE,
                        DataCapability.TREND,
                        DataCapability.DELTA,
                        DataCapability.AVERAGE_DELTA,
                    )
            } else {
                phoneState?.capabilities.orEmpty() +
                    setOf(DataCapability.GLUCOSE, DataCapability.TREND, DataCapability.DELTA)
            }
        val history =
            mergeHistory(
                phoneState,
                watchReadings,
                nowEpochMs,
                phoneState?.source ?: chosenSource,
            )
        val base = phoneState

        return TherapyDisplayState(
            source = chosenSource,
            sourceVersion =
                if (resolution.canonicalSource == CgmCanonicalSource.WATCH_G7_DIRECT) {
                    "G7 Watch Collector"
                } else {
                    base?.sourceVersion
                },
            sourceContract = "CANONICAL_CGM_V3:${resolution.state.name}:${resolution.reason}",
            receivedAtEpochMs = resolution.reading?.receivedAtEpochMs ?: base?.receivedAtEpochMs ?: nowEpochMs,
            glucose = chosenGlucose,
            glucoseHistory = history,
            glucosePredictions = base?.glucosePredictions.orEmpty(),
            therapyHistory = base?.therapyHistory.orEmpty(),
            targetHistory = base?.targetHistory.orEmpty(),
            insulin = base?.insulin,
            carbs = base?.carbs,
            basal = base?.basal,
            target = base?.target,
            loop = base?.loop,
            pump = base?.pump,
            device = base?.device,
            profile = base?.profile,
            capabilities = capabilities,
        )
    }

    private fun sourceMode(context: Context): CgmSourceMode {
        val raw =
            context
                .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
                .getString("dataSource", DataSourcePreference.AUTOMATIC.name)
        return when (runCatching { DataSourcePreference.valueOf(raw!!) }.getOrDefault(DataSourcePreference.AUTOMATIC)) {
            DataSourcePreference.DEXCOM_G7_WATCH -> CgmSourceMode.WATCH_ONLY
            DataSourcePreference.ANDROID_APS,
            DataSourcePreference.XDRIP_PLUS,
            -> CgmSourceMode.MOBILE_ONLY
            DataSourcePreference.AUTOMATIC -> CgmSourceMode.AUTOMATIC
        }
    }

    private fun mergeHistory(
        phoneState: TherapyDisplayState?,
        watchReadings: List<CgmReading>,
        nowEpochMs: Long,
        preferredSource: DataSourceId,
    ): List<GlucoseSample> {
        val phone = buildList {
            addAll(phoneState?.glucoseHistory.orEmpty())
            phoneState?.glucose?.let { add(it.toSample(phoneState.source)) }
        }
        val watch = watchReadings.map { reading -> reading.toSample() }
        return CanonicalCgmHistory.merge(watch + phone, nowEpochMs, preferredSource)
    }

    private fun GlucoseState.toCandidate(fallbackReceivedAt: Long): CgmSourceCandidate =
        CgmSourceCandidate(
            source = CgmCanonicalSource.MOBILE_AAPS,
            glucoseMgDl = valueMgDl,
            measuredAtEpochMs = measuredAtEpochMs,
            receivedAtEpochMs = receivedAtEpochMs ?: fallbackReceivedAt,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
        )

    private fun CgmReading.toCandidate(): CgmSourceCandidate =
        CgmSourceCandidate(
            source = CgmCanonicalSource.WATCH_G7_DIRECT,
            glucoseMgDl = glucoseMgDl,
            measuredAtEpochMs = timestampEpochMs,
            receivedAtEpochMs = receivedAtEpochMs,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
        )

    private fun CgmReading.toGlucoseState(): GlucoseState =
        GlucoseState(
            valueMgDl = glucoseMgDl,
            displayUnit = GlucoseUnit.MG_DL,
            trend = trend,
            measuredAtEpochMs = timestampEpochMs,
            deltaMgDl = deltaMgDl,
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
            receivedAtEpochMs = receivedAtEpochMs,
            quality = status.toQuality(),
        )

    private fun CgmReading.toSample(): GlucoseSample =
        GlucoseSample(
            valueMgDl = glucoseMgDl,
            measuredAtEpochMs = timestampEpochMs,
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
            receivedAtEpochMs = receivedAtEpochMs,
            quality = status.toQuality(),
        )

    private fun GlucoseState.toSample(fallbackSource: DataSourceId): GlucoseSample =
        GlucoseSample(
            valueMgDl = valueMgDl,
            measuredAtEpochMs = measuredAtEpochMs,
            source = fallbackSource,
            sensorId = sensorId,
            sessionId = sessionId,
            sequenceNumber = sequenceNumber,
            receivedAtEpochMs = receivedAtEpochMs,
            quality = quality,
        )

    private fun CgmReadingStatus.toQuality(): CgmQuality =
        when (this) {
            CgmReadingStatus.VALID -> CgmQuality.VALID
            CgmReadingStatus.SENSOR_ERROR -> CgmQuality.SENSOR_ERROR
            CgmReadingStatus.INVALID -> CgmQuality.INVALID
        }

    private fun readMemory(context: Context): CgmResolverMemory {
        val prefs = context.getSharedPreferences(MEMORY_PREFS, Context.MODE_PRIVATE)
        return CgmResolverMemory(
            state =
                prefs.getString("state", CgmSourceState.NO_SOURCE.name)
                    ?.let { runCatching { CgmSourceState.valueOf(it) }.getOrNull() }
                    ?: CgmSourceState.NO_SOURCE,
            recoveryReadingCount = prefs.getInt("recovery_count", 0),
            lastRecoveryMobileTimestampEpochMs =
                prefs.getLong("recovery_timestamp", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
        )
    }

    private fun writeMemory(context: Context, memory: CgmResolverMemory) {
        context.getSharedPreferences(MEMORY_PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", memory.state.name)
            .putInt("recovery_count", memory.recoveryReadingCount)
            .apply {
                val timestamp = memory.lastRecoveryMobileTimestampEpochMs
                if (timestamp == null) remove("recovery_timestamp") else putLong("recovery_timestamp", timestamp)
            }
            .apply()
    }
}

internal object MobileCanonicalStateCoordinator {
    suspend fun savePhoneInput(
        context: Context,
        incoming: TherapyDisplayState,
        nowEpochMs: Long,
    ): Pair<TherapyDisplayState, TherapyDisplayState> {
        val phoneStore = PhoneTherapyStateStore(context)
        val priorPhone =
            phoneStore.state.first()
                ?: TherapyStateStore(context).state.first()?.asPhoneInputFallback()
        var mergedPhone = DisplayHistoryAccumulator.merge(priorPhone, incoming, nowEpochMs)
        val glucose = mergedPhone.glucose
        if (glucose != null && glucose.trend == Trend.UNKNOWN) {
            mergedPhone =
                mergedPhone.copy(
                    glucose =
                        glucose.copy(
                            trend =
                                TrendArrowResolver.resolve(
                                    glucose.trend,
                                    mergedPhone.glucoseHistory,
                                    glucose.measuredAtEpochMs,
                                ),
                        ),
                )
        }
        phoneStore.save(mergedPhone)
        val canonical = requireNotNull(MobileCanonicalCgmResolver.resolve(context, mergedPhone, nowEpochMs))
        TherapyStateStore(context).save(canonical)
        return mergedPhone to canonical
    }

    suspend fun refreshFromWatchBackfill(
        context: Context,
        nowEpochMs: Long,
    ): TherapyDisplayState? {
        val phone =
            PhoneTherapyStateStore(context).state.first()
                ?: TherapyStateStore(context).state.first()?.asPhoneInputFallback()
        val canonical = MobileCanonicalCgmResolver.resolve(context, phone, nowEpochMs) ?: return null
        TherapyStateStore(context).save(canonical)
        return canonical
    }

    private fun TherapyDisplayState.asPhoneInputFallback(): TherapyDisplayState? =
        takeUnless { it.source == DataSourceId.DEXCOM_G7_WATCH }
            ?.copy(
                glucoseHistory =
                    glucoseHistory.filter { sample ->
                        sample.source != DataSourceId.DEXCOM_G7_WATCH
                    },
            )
}
