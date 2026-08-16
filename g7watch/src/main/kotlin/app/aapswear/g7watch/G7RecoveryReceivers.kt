package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class G7BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && G7SensorStateStore(context).read().collectorEnabled) {
            G7CollectorService.start(context)
        }
    }
}

class G7ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (G7SensorStateStore(context).read().collectorEnabled) G7CollectorService.start(context)
    }
}
