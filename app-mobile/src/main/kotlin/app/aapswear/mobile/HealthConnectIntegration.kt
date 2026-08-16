package app.aapswear.mobile

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private suspend inline fun <reified T : Record> readHealthRecords(
    client: HealthConnectClient,
    granted: Set<String>,
    start: Instant,
    end: Instant,
): List<T> {
    if (HealthPermission.getReadPermission(T::class) !in granted) return emptyList()
    val result = mutableListOf<T>()
    var token: String? = null
    do {
        val response = client.readRecords(ReadRecordsRequest(T::class, TimeRangeFilter.between(start, end), pageToken = token))
        result += response.records
        token = response.pageToken
    } while (token != null)
    return result
}

@Serializable
internal data class HealthConnectSnapshot(
    val syncedAtEpochMs: Long,
    val steps: Long = 0,
    val latestHeartRate: Long? = null,
    val averageHeartRate: Double? = null,
    val restingHeartRate: Long? = null,
    val activeCaloriesKcal: Double = 0.0,
    val totalCaloriesKcal: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val activeMinutes: Long = 0,
    val sleepMinutes: Long = 0,
    val weightKg: Double? = null,
    val bloodGlucoseMgDl: Double? = null,
    val oxygenSaturationPercent: Double? = null,
    val respiratoryRate: Double? = null,
    val vo2Max: Double? = null,
)

internal object HealthConnectIntegration {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    private const val PREFS = "health_connect"
    private const val SNAPSHOT = "snapshot"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val readableRecordTypes = setOf(
        HeartRateRecord::class, RestingHeartRateRecord::class, StepsRecord::class,
        ActiveCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord::class, DistanceRecord::class,
        ExerciseSessionRecord::class, SleepSessionRecord::class, WeightRecord::class,
        BloodGlucoseRecord::class, OxygenSaturationRecord::class, RespiratoryRateRecord::class, Vo2MaxRecord::class,
    )
    val recordPermissions: Set<String> =
        readableRecordTypes.map(HealthPermission::getReadPermission).toSet() +
            HealthPermission.getWritePermission(BloodGlucoseRecord::class)

    val permissions: Set<String> = recordPermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    val permissionContract get() = PermissionController.createRequestPermissionResultContract()

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)

    suspend fun grantedPermissions(context: Context): Set<String> =
        if (availability(context) == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() else emptySet()

    suspend fun sync(context: Context): HealthConnectSnapshot? {
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return null
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val end = Instant.now()
        val start = end.minus(Duration.ofHours(24))
        val heartRates = readHealthRecords<HeartRateRecord>(client, granted, start, end).flatMap(HeartRateRecord::samples).sortedBy(HeartRateRecord.Sample::time)
        val exercises = readHealthRecords<ExerciseSessionRecord>(client, granted, start, end)
        val sleep = readHealthRecords<SleepSessionRecord>(client, granted, start, end)
        val snapshot = HealthConnectSnapshot(
            syncedAtEpochMs = System.currentTimeMillis(),
            steps = readHealthRecords<StepsRecord>(client, granted, start, end).sumOf(StepsRecord::count),
            latestHeartRate = heartRates.lastOrNull()?.beatsPerMinute,
            averageHeartRate = heartRates.map(HeartRateRecord.Sample::beatsPerMinute).average().takeUnless(Double::isNaN),
            restingHeartRate = readHealthRecords<RestingHeartRateRecord>(client, granted, start, end).maxByOrNull(RestingHeartRateRecord::time)?.beatsPerMinute,
            activeCaloriesKcal = readHealthRecords<ActiveCaloriesBurnedRecord>(client, granted, start, end).sumOf { it.energy.inKilocalories },
            totalCaloriesKcal = readHealthRecords<TotalCaloriesBurnedRecord>(client, granted, start, end).sumOf { it.energy.inKilocalories },
            distanceMeters = readHealthRecords<DistanceRecord>(client, granted, start, end).sumOf { it.distance.inMeters },
            activeMinutes = exercises.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) },
            sleepMinutes = sleep.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) },
            weightKg = readHealthRecords<WeightRecord>(client, granted, start, end).maxByOrNull(WeightRecord::time)?.weight?.inKilograms,
            bloodGlucoseMgDl = readHealthRecords<BloodGlucoseRecord>(client, granted, start, end).maxByOrNull(BloodGlucoseRecord::time)?.level?.inMilligramsPerDeciliter,
            oxygenSaturationPercent = readHealthRecords<OxygenSaturationRecord>(client, granted, start, end).maxByOrNull(OxygenSaturationRecord::time)?.percentage?.value,
            respiratoryRate = readHealthRecords<RespiratoryRateRecord>(client, granted, start, end).maxByOrNull(RespiratoryRateRecord::time)?.rate,
            vo2Max = readHealthRecords<Vo2MaxRecord>(client, granted, start, end).maxByOrNull(Vo2MaxRecord::time)?.vo2MillilitersPerMinuteKilogram,
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SNAPSHOT, json.encodeToString(HealthConnectSnapshot.serializer(), snapshot)).apply()
        return snapshot
    }

    /** Writes only records actually created by Sugarlicious; imported records are never mirrored back. */
    suspend fun writeOwnedRecords(context: Context, records: List<Record>): List<String> {
        if (records.isEmpty() || availability(context) != HealthConnectClient.SDK_AVAILABLE) return emptyList()
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val allowed = records.filter { HealthPermission.getWritePermission(it::class) in granted }
        return if (allowed.isEmpty()) emptyList() else client.insertRecords(allowed).recordIdsList
    }

    suspend fun exportCgmReading(context: Context, state: app.aapswear.model.TherapyDisplayState): Boolean {
        val glucose = state.glucose ?: return false
        if (availability(context) != HealthConnectClient.SDK_AVAILABLE) return false
        val clientRecordId = "sugarlicious:cgm:${state.source.name}:${glucose.measuredAtEpochMs}"
        val record = BloodGlucoseRecord(
            time = Instant.ofEpochMilli(glucose.measuredAtEpochMs),
            zoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(glucose.measuredAtEpochMs)),
            metadata = Metadata.autoRecorded(
                device = Device(type = Device.TYPE_PHONE, manufacturer = android.os.Build.MANUFACTURER, model = android.os.Build.MODEL),
                clientRecordId = clientRecordId,
                clientRecordVersion = 1L,
            ),
            level = BloodGlucose.milligramsPerDeciliter(glucose.valueMgDl),
            specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
            mealType = MealType.MEAL_TYPE_UNKNOWN,
            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
        )
        return writeOwnedRecords(context, listOf(record)).isNotEmpty()
    }

    fun snapshot(context: Context): HealthConnectSnapshot? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SNAPSHOT, null)
        ?.let { runCatching { json.decodeFromString<HealthConnectSnapshot>(it) }.getOrNull() }

    fun statusLabel(context: Context): String = when (availability(context)) {
        HealthConnectClient.SDK_AVAILABLE -> snapshot(context)?.let { "Verbunden · ${it.steps} Schritte" } ?: "Bereit zum Verbinden"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect aktualisieren"
        else -> "Nicht verfügbar"
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("health_connect_sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class HealthConnectSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        HealthConnectIntegration.sync(applicationContext)
        SugarliciousWidgets.update(applicationContext)
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
