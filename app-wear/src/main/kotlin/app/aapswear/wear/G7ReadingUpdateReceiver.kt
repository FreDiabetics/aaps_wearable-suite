package app.aapswear.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aapswear.complications.AllProviders

/** Refreshes every local CGM consumer immediately after the standalone G7 app stores a reading. */
class G7ReadingUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_G7_READING_UPDATED) return
        AllProviders.classes.forEach { provider ->
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, provider))
                .requestUpdateAll()
        }
        requestSugarliciousTileUpdates(context)
    }

    private companion object {
        const val ACTION_G7_READING_UPDATED = "app.aapswear.g7watch.READING_UPDATED"
    }
}
