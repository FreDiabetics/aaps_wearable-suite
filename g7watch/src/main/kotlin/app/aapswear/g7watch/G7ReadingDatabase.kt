package app.aapswear.g7watch

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingRepository
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class G7ReadingDatabase(context: Context) : SQLiteOpenHelper(context, "g7_readings.db", null, 1), CgmReadingRepository {
    private val appContext = context.applicationContext
    private val mutableLatest = MutableStateFlow<CgmReading?>(null)
    override val latestReading: StateFlow<CgmReading?> = mutableLatest

    init { mutableLatest.value = query(limit = 1).firstOrNull() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE readings (id TEXT PRIMARY KEY, sensor_id TEXT NOT NULL, session_id TEXT NOT NULL, glucose REAL NOT NULL, measured_at INTEGER NOT NULL, received_at INTEGER NOT NULL, delta REAL, trend TEXT NOT NULL, trend_rate REAL, predicted REAL, sensor_age INTEGER, status TEXT NOT NULL, sequence_number INTEGER, synced INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX readings_measured_at ON readings(measured_at DESC)")
        db.execSQL("CREATE INDEX readings_pending ON readings(synced, measured_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override suspend fun insert(reading: CgmReading): Boolean {
        val values = ContentValues().apply {
            put("id", reading.id); put("sensor_id", reading.sensorId); put("session_id", reading.sessionId)
            put("glucose", reading.glucoseMgDl); put("measured_at", reading.timestampEpochMs); put("received_at", reading.receivedAtEpochMs)
            reading.deltaMgDl?.let { put("delta", it) }; put("trend", reading.trend.name); reading.trendRateMgDlPerMinute?.let { put("trend_rate", it) }
            reading.predictedMgDl?.let { put("predicted", it) }; reading.sensorAgeSeconds?.let { put("sensor_age", it) }
            put("status", reading.status.name); reading.sequenceNumber?.let { put("sequence_number", it) }
        }
        val inserted = writableDatabase.insertWithOnConflict("readings", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if (inserted) {
            mutableLatest.value = reading
            appContext.contentResolver.notifyChange(G7ReadingProvider.CONTENT_URI, null)
            appContext.sendBroadcast(
                Intent(ACTION_G7_READING_UPDATED).setPackage(SUGARLICIOUS_PACKAGE),
                READ_G7_PERMISSION,
            )
        }
        return inserted
    }
    override suspend fun getLatest(): CgmReading? = query(limit = 1).firstOrNull()
    override suspend fun getPrevious(): CgmReading? = query(limit = 2).getOrNull(1)
    override suspend fun getRecent(sinceEpochMs: Long): List<CgmReading> = query("measured_at>=?", arrayOf(sinceEpochMs.toString()))
    override suspend fun getRange(fromEpochMs: Long, toEpochMs: Long): List<CgmReading> = query("measured_at BETWEEN ? AND ?", arrayOf(fromEpochMs.toString(), toEpochMs.toString()))
    override suspend fun getUnsynced(limit: Int): List<CgmReading> = query("synced=0", limit = limit)
    override suspend fun markSynced(ids: Set<String>) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try { ids.forEach { writableDatabase.update("readings", ContentValues().apply { put("synced", 1) }, "id=?", arrayOf(it)) }; writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }
    }

    fun query(selection: String? = null, args: Array<String>? = null, limit: Int = 300): List<CgmReading> =
        readableDatabase.query("readings", null, selection, args, null, null, "measured_at DESC", limit.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(CgmReading(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")), source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = cursor.getString(cursor.getColumnIndexOrThrow("sensor_id")), sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
                    glucoseMgDl = cursor.getDouble(cursor.getColumnIndexOrThrow("glucose")), timestampEpochMs = cursor.getLong(cursor.getColumnIndexOrThrow("measured_at")), receivedAtEpochMs = cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                    deltaMgDl = cursor.doubleOrNull("delta"), trend = runCatching { Trend.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("trend"))) }.getOrDefault(Trend.UNKNOWN),
                    trendRateMgDlPerMinute = cursor.doubleOrNull("trend_rate"), predictedMgDl = cursor.doubleOrNull("predicted"), sensorAgeSeconds = cursor.longOrNull("sensor_age"),
                    status = runCatching { CgmReadingStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))) }.getOrDefault(CgmReadingStatus.INVALID), sequenceNumber = cursor.longOrNull("sequence_number"),
                ))
            }
        }

    private fun android.database.Cursor.doubleOrNull(name: String): Double? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getDouble(it) }
    private fun android.database.Cursor.longOrNull(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }

    private companion object {
        const val ACTION_G7_READING_UPDATED = "app.aapswear.g7watch.READING_UPDATED"
        const val SUGARLICIOUS_PACKAGE = "app.aapswear"
        const val READ_G7_PERMISSION = "app.aapswear.g7watch.permission.READ_G7_DATA"
    }
}
