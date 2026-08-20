package app.aapswear.g7watch

import android.app.Notification

/** Platform-builder equivalent of the NotificationCompat silent flag. */
internal fun Notification.Builder.setSilent(silent: Boolean): Notification.Builder = apply {
    if (silent) {
        setSound(null)
        setVibrate(null)
    }
}
