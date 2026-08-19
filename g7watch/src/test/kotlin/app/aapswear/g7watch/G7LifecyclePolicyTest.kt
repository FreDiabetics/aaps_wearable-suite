package app.aapswear.g7watch

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7LifecyclePolicyTest {
    @Test fun `enabled collector restores after boot`() {
        assertTrue(shouldRestoreG7Collector(Intent.ACTION_BOOT_COMPLETED, collectorEnabled = true))
    }

    @Test fun `enabled collector restores after app update`() {
        assertTrue(shouldRestoreG7Collector(Intent.ACTION_MY_PACKAGE_REPLACED, collectorEnabled = true))
    }

    @Test fun `disabled collector stays disabled after lifecycle broadcasts`() {
        assertFalse(shouldRestoreG7Collector(Intent.ACTION_BOOT_COMPLETED, collectorEnabled = false))
        assertFalse(shouldRestoreG7Collector(Intent.ACTION_MY_PACKAGE_REPLACED, collectorEnabled = false))
    }

    @Test fun `unrelated broadcasts never restore collector`() {
        assertFalse(shouldRestoreG7Collector(Intent.ACTION_SCREEN_ON, collectorEnabled = true))
        assertFalse(shouldRestoreG7Collector(null, collectorEnabled = true))
    }

    @Test fun `source selection cannot enable a user-disabled collector`() {
        assertFalse(
            shouldResumeEnabledCollectorForSourceSignal(
                g7Selected = true,
                collectorEnabled = false,
            ),
        )
    }

    @Test fun `leaving direct source never stops or restarts collector`() {
        assertFalse(
            shouldResumeEnabledCollectorForSourceSignal(
                g7Selected = false,
                collectorEnabled = true,
            ),
        )
    }

    @Test fun `direct source signal may only resume an already enabled collector`() {
        assertTrue(
            shouldResumeEnabledCollectorForSourceSignal(
                g7Selected = true,
                collectorEnabled = true,
            ),
        )
    }
}
