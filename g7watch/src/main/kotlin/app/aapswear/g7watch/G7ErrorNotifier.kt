package app.aapswear.g7watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.aapswear.g7.G7CollectorError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

internal data class G7AcknowledgedError(
    val signature: String,
    val acknowledgedAtEpochMs: Long,
)

internal fun g7ErrorSignature(error: G7CollectorError): String =
    "${error.code}|${error.safeMessage}"

/**
 * Urgent collector error surface for Wear OS.
 *
 * Android 14+ reserves true full-screen intents for calling/alarm apps. Collector failures use a
 * high-importance heads-up notification instead, remain posted until explicit acknowledgement,
 * and update in place when automatic recovery succeeds.
 */
internal object G7ErrorNotifier {
    private const val CHANNEL_ID = "g7_collector_errors_v1"
    private const val CHANNEL_NAME = "G7 Collector-Fehler"
    private const val NOTIFICATION_ID = 7002
    private const val PREFS = "g7_error_notifications"
    private const val KEY_ACTIVE_SIGNATURE = "active_signature"
    private const val KEY_ACTIVE_CODE = "active_code"
    private const val KEY_ACTIVE_MESSAGE = "active_message"
    private const val KEY_FIRST_OCCURRED_AT = "first_occurred_at"
    private const val KEY_LAST_POSTED_AT = "last_posted_at"
    private const val KEY_RECOVERED_AT = "recovered_at"
    private const val KEY_LAST_ACK_SIGNATURE = "last_ack_signature"
    private const val KEY_LAST_ACK_AT = "last_ack_at"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Dringende Fehler des direkten Dexcom-G7-Watch-Collectors"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun show(context: Context, error: G7CollectorError) {
        ensureChannel(context)
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = g7ErrorSignature(error)
        val previousSignature = prefs.getString(KEY_ACTIVE_SIGNATURE, null)
        val sameActiveError = signature == previousSignature
        val firstOccurredAt = if (sameActiveError) {
            prefs.getLong(KEY_FIRST_OCCURRED_AT, error.occurredAtEpochMs)
        } else {
            error.occurredAtEpochMs
        }
        prefs.edit()
            .putString(KEY_ACTIVE_SIGNATURE, signature)
            .putString(KEY_ACTIVE_CODE, error.code)
            .putString(KEY_ACTIVE_MESSAGE, error.safeMessage)
            .putLong(KEY_FIRST_OCCURRED_AT, firstOccurredAt)
            .putLong(KEY_LAST_POSTED_AT, System.currentTimeMillis())
            .remove(KEY_RECOVERED_AT)
            .apply()

        app.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(
                context = app,
                title = "G7-Fehler ${error.code}",
                body = error.safeMessage,
                occurredAtEpochMs = firstOccurredAt,
                recoveredAtEpochMs = null,
                onlyAlertOnce = sameActiveError,
            ),
        )
    }

    fun markRecovered(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = prefs.getString(KEY_ACTIVE_SIGNATURE, null) ?: return
        val code = prefs.getString(KEY_ACTIVE_CODE, null) ?: return
        val message = prefs.getString(KEY_ACTIVE_MESSAGE, null) ?: return
        val firstOccurredAt = prefs.getLong(KEY_FIRST_OCCURRED_AT, 0L).takeIf { it > 0L } ?: return
        val recoveredAt = System.currentTimeMillis()
        prefs.edit().putLong(KEY_RECOVERED_AT, recoveredAt).apply()
        ensureChannel(app)
        app.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(
                context = app,
                title = "G7-Verbindung wiederhergestellt",
                body = "$code: $message",
                occurredAtEpochMs = firstOccurredAt,
                recoveredAtEpochMs = recoveredAt,
                onlyAlertOnce = true,
            ),
        )
        // Keep the active signature until the user acknowledges the incident.
        prefs.edit().putString(KEY_ACTIVE_SIGNATURE, signature).apply()
    }

    fun acknowledge(context: Context): G7AcknowledgedError? {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = prefs.getString(KEY_ACTIVE_SIGNATURE, null) ?: return null
        val acknowledgedAt = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_LAST_ACK_SIGNATURE, signature)
            .putLong(KEY_LAST_ACK_AT, acknowledgedAt)
            .remove(KEY_ACTIVE_SIGNATURE)
            .remove(KEY_ACTIVE_CODE)
            .remove(KEY_ACTIVE_MESSAGE)
            .remove(KEY_FIRST_OCCURRED_AT)
            .remove(KEY_LAST_POSTED_AT)
            .remove(KEY_RECOVERED_AT)
            .apply()
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        return G7AcknowledgedError(signature, acknowledgedAt)
    }

    private fun buildNotification(
        context: Context,
        title: String,
        body: String,
        occurredAtEpochMs: Long,
        recoveredAtEpochMs: Long?,
        onlyAlertOnce: Boolean,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            7002,
            Intent(context, G7WatchActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val acknowledge = PendingIntent.getBroadcast(
            context,
            7002,
            Intent(context, G7ErrorAcknowledgeReceiver::class.java).setAction(G7ErrorAcknowledgeReceiver.ACTION_ACKNOWLEDGE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val errorTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(occurredAtEpochMs))
        val detail = if (recoveredAtEpochMs == null) {
            "$body\nAufgetreten: $errorTime"
        } else {
            val recoveredTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(recoveredAtEpochMs))
            "$body\nAufgetreten: $errorTime\nAutomatisch wiederhergestellt: $recoveredTime\nBitte quittieren."
        }
        val actionIcon = Icon.createWithResource(context, R.drawable.ic_g7_notification)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_g7_notification)
            .setColor(0xFFFF5D6C.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setWhen(occurredAtEpochMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(onlyAlertOnce)
            .addAction(Notification.Action.Builder(actionIcon, "Quittieren", acknowledge).build())
            .build()
    }
}

class G7ErrorAcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return
        val acknowledged = G7ErrorNotifier.acknowledge(context) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.applicationContext.recordG7Diagnostic(
                    code = "G7-ALERT-ACK",
                    message = "Collector error notification acknowledged",
                    metadata = mapOf(
                        "signature" to acknowledged.signature,
                        "acknowledgedAtEpochMs" to acknowledged.acknowledgedAtEpochMs,
                    ),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "app.aapswear.g7watch.ACKNOWLEDGE_ERROR"
    }
}
