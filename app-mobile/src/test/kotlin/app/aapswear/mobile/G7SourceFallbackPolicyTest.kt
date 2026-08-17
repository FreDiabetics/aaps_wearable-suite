package app.aapswear.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class G7SourceFallbackPolicyTest {
    @Test fun `legacy forced G7 source migrates to automatic`() {
        assertEquals(
            DataSourcePreference.AUTOMATIC,
            migrateLegacyForcedG7Source(
                DataSourcePreference.DEXCOM_G7_WATCH,
                migrationDone = false,
            ),
        )
    }

    @Test fun `explicit G7 source remains strict after migration has run`() {
        assertEquals(
            DataSourcePreference.DEXCOM_G7_WATCH,
            migrateLegacyForcedG7Source(
                DataSourcePreference.DEXCOM_G7_WATCH,
                migrationDone = true,
            ),
        )
    }

    @Test fun `existing AAPS source is not changed`() {
        assertEquals(
            DataSourcePreference.ANDROID_APS,
            migrateLegacyForcedG7Source(
                DataSourcePreference.ANDROID_APS,
                migrationDone = false,
            ),
        )
    }
}
