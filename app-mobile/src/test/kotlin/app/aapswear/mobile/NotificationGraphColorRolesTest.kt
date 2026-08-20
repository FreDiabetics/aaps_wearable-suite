package app.aapswear.mobile

import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGraphColorRolesTest {
    @Test
    fun `notification editable roles do not duplicate shared in-range band`() {
        val source = NotificationGraphSettingsContract.editableRoles
        assertFalse(SugarliciousColorRole.RANGE_IN_RANGE in source)
        assertTrue(SugarliciousColorRole.CGM_DOT_IN_RANGE in source)
    }
}
