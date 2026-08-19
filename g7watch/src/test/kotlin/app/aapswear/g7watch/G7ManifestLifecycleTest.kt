package app.aapswear.g7watch

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class G7ManifestLifecycleTest {
    @Test fun `package replacement can restore collector`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(context.packageName)

        val matches = context.packageManager.queryBroadcastReceivers(intent, 0)

        assertTrue(
            matches.any {
                it.activityInfo.name == "app.aapswear.g7watch.G7BootReceiver"
            },
        )
    }

    @Test fun `connected device foreground service permissions are declared`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions.orEmpty().toSet()

        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" in permissions)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in permissions)
        assertTrue("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in permissions)
    }
}
