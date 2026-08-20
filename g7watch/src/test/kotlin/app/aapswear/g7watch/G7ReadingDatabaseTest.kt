package app.aapswear.g7watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import app.aapswear.model.Trend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7ReadingDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: G7ReadingDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = G7ReadingDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `retains all decoded sensor documentation fields`() = runBlocking {
        val reading =
            CgmReading(
                id = "reading-14",
                source = DataSourceId.DEXCOM_G7_WATCH,
                sensorId = "sensor-id",
                sessionId = "session-id",
                glucoseMgDl = 123.0,
                timestampEpochMs = 1_000_000L,
                receivedAtEpochMs = 1_015_000L,
                deltaMgDl = 2.0,
                trend = Trend.FLAT,
                trendRateMgDlPerMinute = 0.4,
                predictedMgDl = 126.0,
                sensorAgeSeconds = 15L,
                status = CgmReadingStatus.VALID,
                sequenceNumber = 14L,
                displayOnly = true,
                rawSourceTimestamp = 123_456L,
                sensorStartEpochMs = 900_000L,
                sensorEndEpochMs = 865_900_000L,
                graceEndEpochMs = 909_100_000L,
                protocolStatusCode = 1,
                calibrationStateCode = 6,
                reservedField = 42,
            )

        database.insert(reading)

        assertEquals(reading, database.getLatest())
    }

    private companion object {
        const val DATABASE_NAME = "g7_readings.db"
    }
}
