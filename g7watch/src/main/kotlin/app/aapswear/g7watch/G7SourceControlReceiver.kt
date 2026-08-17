package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives explicit data-source transitions from the Sugarlicious Wear app.
 * Selecting the direct G7 source is an explicit request to run the collector; selecting any
 * other source stops it. Repeated configuration syncs with the same source are filtered on the
 * Wear side and therefore do not restart scanning.
 */
class G7SourceControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_SOURCE) return
        val selected = intent.getBooleanExtra(EXTRA_G7_SELECTED, false)
        if (selected) {
            G7CollectorService.start(context)
        } else {
            G7CollectorService.stop(context)
        }
    }

    companion object {
        const val ACTION_SET_SOURCE = "app.aapswear.g7watch.SET_SOURCE"
        const val EXTRA_G7_SELECTED = "g7_selected"
    }
}
