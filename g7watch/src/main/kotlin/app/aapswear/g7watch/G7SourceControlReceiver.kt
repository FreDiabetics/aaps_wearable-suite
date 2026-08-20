package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Source-selection signal from Sugarlicious Wear.
 *
 * Source selection and collector lifecycle remain separate. Changing the canonical display source
 * must never persist collectorEnabled=false. The signal additionally controls whether the standalone
 * collector is allowed to raise user-facing connection alarms: only explicit Watch Collector Only
 * mode enables those alarms. Automatic/Phone modes keep recoverable collector problems diagnostic.
 */
class G7SourceControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_SOURCE) return

        val g7Selected = intent.getBooleanExtra(EXTRA_G7_SELECTED, false)
        G7AlertPolicyStore.setWatchOnly(context, g7Selected)
        val state = G7SensorStateStore(context).read()

        if (!g7Selected) {
            G7ErrorNotifier.clearActive(context)
            return
        }

        G7SignalLossMonitor.scheduleFromState(context, state)
        if (!shouldResumeEnabledCollectorForSourceSignal(g7Selected, state.collectorEnabled)) return
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
