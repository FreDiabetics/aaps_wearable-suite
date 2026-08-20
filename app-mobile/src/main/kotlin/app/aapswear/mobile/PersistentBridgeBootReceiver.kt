package app.aapswear.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal fun shouldRestorePersistentBridge(action: String?): Boolean =
    action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

class PersistentBridgeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (shouldRestorePersistentBridge(intent.action)) {
            PersistentBridgeService.start(context)
        }
    }
}
