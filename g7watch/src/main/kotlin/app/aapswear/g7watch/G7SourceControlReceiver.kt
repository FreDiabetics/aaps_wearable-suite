package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Legacy source-selection signal from Sugarlicious Wear.
 *
 * Source selection and collector lifecycle are deliberately separate. Changing the canonical
 * display source must never persist collectorEnabled=false. Entering the direct-G7 source may
 * only resume a collector that the user has already enabled explicitly.
 */
class G7SourceControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_SOURCE) return

        val g7Selected = intent.getBooleanExtra(EXTRA_G7_SELECTED, false)
        val collectorEnabled = G7SensorStateStore(context).read().collectorEnabled
        if (!shouldResumeEnabledCollectorForSourceSignal(g7Selected, collectorEnabled)) return

        runCatching { G7CollectorService.start(context) }
    }

    companion object {
        const val ACTION_SET_SOURCE = "app.aapswear.g7watch.SET_SOURCE"
        const val EXTRA_G7_SELECTED = "g7_selected"
    }
}

internal fun shouldResumeEnabledCollectorForSourceSignal(
    g7Selected: Boolean,
    collectorEnabled: Boolean,
): Boolean = g7Selected && collectorEnabled
