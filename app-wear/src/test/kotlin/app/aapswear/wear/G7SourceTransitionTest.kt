package app.aapswear.wear

import app.aapswear.protocol.WatchDataSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7SourceTransitionTest {
    @Test fun `repeated configuration sync does not retrigger collector`() {
        assertFalse(
            shouldApplyG7CollectorSourceTransition(
                WatchDataSource.DEXCOM_G7_WATCH,
                WatchDataSource.DEXCOM_G7_WATCH,
            ),
        )
        assertFalse(
            shouldApplyG7CollectorSourceTransition(
                WatchDataSource.PHONE,
                WatchDataSource.PHONE,
            ),
        )
    }

    @Test fun `changing selected source triggers collector source control once`() {
        assertTrue(
            shouldApplyG7CollectorSourceTransition(
                WatchDataSource.PHONE,
                WatchDataSource.DEXCOM_G7_WATCH,
            ),
        )
        assertTrue(
            shouldApplyG7CollectorSourceTransition(
                WatchDataSource.DEXCOM_G7_WATCH,
                WatchDataSource.PHONE,
            ),
        )
        assertTrue(
            shouldApplyG7CollectorSourceTransition(
                WatchDataSource.DEXCOM_G7_WATCH,
                WatchDataSource.AUTOMATIC,
            ),
        )
    }
}
