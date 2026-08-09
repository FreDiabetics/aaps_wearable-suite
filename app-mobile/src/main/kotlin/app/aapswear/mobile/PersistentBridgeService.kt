package app.aapswear.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColorStore
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PersistentBridgeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var uiPreferences: SharedPreferences
    private lateinit var diagnostics: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var latestState: TherapyDisplayState? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        uiPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        diagnostics = getSharedPreferences(DIAGNOSTICS_NAME, MODE_PRIVATE)
        uiPreferences.registerOnSharedPreferenceChangeListener(this)
        diagnostics.registerOnSharedPreferenceChangeListener(this)
        createNotificationChannel()
        scope.launch {
            TherapyStateStore(this@PersistentBridgeService).state.collectLatest {
                latestState = it
                if (foregroundStarted) notifyUpdated()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE_LIVE) {
            uiPreferences.edit { putBoolean(PREFERENCE_LIVE_NOTIFICATION, false) }
        }
        promoteToForeground(buildNotification())
        foregroundStarted = true
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (foregroundStarted) notifyUpdated()
    }

    override fun onDestroy() {
        uiPreferences.unregisterOnSharedPreferenceChangeListener(this)
        diagnostics.unregisterOnSharedPreferenceChangeListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyUpdated() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val liveRequested = uiPreferences.getBoolean(PREFERENCE_LIVE_NOTIFICATION, false)
        val liveCapable = liveRequested && Build.VERSION.SDK_INT >= 36
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val display = notificationDisplay(latestState)
        val graph = NotificationGraphRenderer.render(this, latestState, uiPreferences)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outlined)
            .setColor(getColor(R.color.app_accent))
            .setContentTitle(display.title)
            .setContentText(display.subtitle)
            .setLargeIcon(graph)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(
                Notification.BigPictureStyle()
                    .bigPicture(graph)
                    .setSummaryText(display.subtitle),
            )

        if (liveCapable) {
            val disableLive = PendingIntent.getService(
                this,
                1,
                Intent(this, PersistentBridgeService::class.java).setAction(ACTION_DISABLE_LIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_notification),
                        "Live beenden",
                        disableLive,
                    ).build(),
                )
                .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        return builder.build()
    }

    private fun notificationDisplay(state: TherapyDisplayState?): NotificationDisplay {
        val glucose = state?.glucose
        val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, System.currentTimeMillis())
        if (glucose == null || freshness == Freshness.STALE || freshness == Freshness.NO_DATA) {
            return NotificationDisplay("—", "Keine aktuellen Glukosedaten")
        }
        val selectedUnit = DashboardUiPreferences.read(uiPreferences).unitFor(state)
        val value = if (selectedUnit == GlucoseUnit.MMOL_L) {
            String.format(Locale.getDefault(), "%.1f", glucose.valueMgDl / 18.0)
        } else glucose.valueMgDl.roundToInt().toString()
        val unit = if (selectedUnit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
        val trend = when (glucose.trend) {
            Trend.DOUBLE_UP -> "⇈"; Trend.SINGLE_UP -> "↑"; Trend.FORTY_FIVE_UP -> "↗"
            Trend.FLAT -> "→"; Trend.FORTY_FIVE_DOWN -> "↘"; Trend.SINGLE_DOWN -> "↓"
            Trend.DOUBLE_DOWN -> "⇊"; Trend.UNKNOWN -> ""
        }
        val age = ((System.currentTimeMillis() - glucose.measuredAtEpochMs).coerceAtLeast(0L) / 60_000L)
        val prefix = if (freshness == Freshness.DELAYED) "Verzögert · " else ""
        return NotificationDisplay("$value $trend", "$prefix$unit · $age min alt")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Glukose im Hintergrund", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Zeigt den aktuellen Glukosewert und hält die lokale Watch-Verbindung aktiv"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class NotificationDisplay(val title: String, val subtitle: String)

    companion object {
        const val PREFERENCE_LIVE_NOTIFICATION = "liveNotification"
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val CHANNEL_ID = "sugarlicious_background"
        const val NOTIFICATION_ID = 4101
        private const val PREFERENCES_NAME = "dashboard_ui"
        private const val DIAGNOSTICS_NAME = "diagnostics"
        private const val ACTION_REFRESH = "app.aapswear.action.REFRESH_PERSISTENT_NOTIFICATION"
        private const val ACTION_DISABLE_LIVE = "app.aapswear.action.DISABLE_LIVE_NOTIFICATION"

        fun start(context: Context): Boolean = startWithAction(context, null)
        fun refresh(context: Context): Boolean = startWithAction(context, ACTION_REFRESH)
        private fun startWithAction(context: Context, action: String?): Boolean = try {
            context.startForegroundService(Intent(context, PersistentBridgeService::class.java).apply { this.action = action })
            true
        } catch (_: SecurityException) { false } catch (_: IllegalStateException) { false }
    }
}

private object NotificationGraphRenderer {
    fun render(context: Context, state: TherapyDisplayState?, preferences: SharedPreferences): Bitmap {
        val width = 420
        val height = 180
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val palette = SugarliciousColorStore.load(preferences)
        val bounds = RectF(1f, 1f, width - 1f, height - 1f)
        val radius = 20f
        val clip = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.argb(SugarliciousColorRole.GRAPH_BACKGROUND)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val points = state?.glucoseHistory.orEmpty().takeLast(36)
        if (points.size >= 2) {
            val targetLow = state?.target?.lowMgDl ?: 80.0
            val targetHigh = state?.target?.highMgDl ?: 160.0
            val minValue = min(targetLow * 0.75, points.minOf { it.valueMgDl } * 0.9)
            val maxValue = max(targetHigh * 1.2, points.maxOf { it.valueMgDl } * 1.1)
            fun y(value: Double) = (height - 8f - ((value - minValue) / (maxValue - minValue).coerceAtLeast(1.0) * (height - 16f))).toFloat()
            paint.color = withAlpha(palette.argb(SugarliciousColorRole.RANGE_IN_RANGE), 42)
            canvas.drawRect(4f, y(targetHigh), width - 4f, y(targetLow), paint)
            val first = points.first().measuredAtEpochMs
            val last = points.last().measuredAtEpochMs.coerceAtLeast(first + 1L)
            points.forEach { point ->
                val x = 6f + (point.measuredAtEpochMs - first).toFloat() / (last - first) * (width - 12f)
                paint.color = when {
                    point.valueMgDl < targetLow -> palette.argb(SugarliciousColorRole.CGM_DOT_LOW)
                    point.valueMgDl > targetHigh -> palette.argb(SugarliciousColorRole.CGM_DOT_HIGH)
                    else -> android.graphics.Color.WHITE
                }
                canvas.drawCircle(x, y(point.valueMgDl), 5.0f, paint)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = palette.argb(SugarliciousColorRole.BORDER)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        return bitmap
    }

    private fun withAlpha(color: Int, alpha: Int) =
        android.graphics.Color.argb(alpha, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
}
