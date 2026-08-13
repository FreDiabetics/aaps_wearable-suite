package app.aapswear.mobile

import android.graphics.drawable.GradientDrawable
import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColorStore
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val diagnostics by lazy { getSharedPreferences("diagnostics", MODE_PRIVATE) }
    private val uiPreferences by lazy { getSharedPreferences("dashboard_ui", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var factory: DashboardViewFactory
    private var state: app.aapswear.model.TherapyDisplayState? = null
    private var screen = DashboardScreen.OVERVIEW
    private var clockJob: Job? = null
    private var settingsSwipeStartX = 0f
    private var settingsSwipeStartY = 0f
    private var settingsSwipeTracking = false

    private val diagnosticsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread(::refresh) }
    private val uiListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (uiPreferenceRequiresDashboardRefresh(key)) {
                runOnUiThread {
                    SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
                    refresh(forceSettingsRender = true)
                }
            }
            scope.launch(Dispatchers.IO) {
                runCatching {
                    publishWatchConfig(applicationContext)
                }
            }
        }

    internal fun uiPreferenceRequiresDashboardRefresh(key: String?): Boolean =
        key != "watchFaceIndex"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
        setContentView(R.layout.activity_main)
        if (!uiPreferences.getBoolean("graphHoursDefault3Migrated", false)) {
            uiPreferences.edit { putInt("graphHours", 3); putBoolean("graphHoursDefault3Migrated", true) }
        }
        if (!uiPreferences.getBoolean("cgmDotsOnlyDefaultMigratedV1", false)) {
            uiPreferences.edit {
                putBoolean("showCgmGraph", true)
                putBoolean("showMetabolicGraph", false)
                putBoolean("showPredictions", false)
                putBoolean("cgm.targetRange", false)
                putBoolean("cgm.basal", false)
                putBoolean("cgm.activity", false)
                putBoolean("cgm.prediction.iob", false)
                putBoolean("cgm.prediction.cob", false)
                putBoolean("cgm.prediction.uam", false)
                putBoolean("cgm.prediction.zeroTemp", false)
                putBoolean("cgmDotsOnlyDefaultMigratedV1", true)
            }
        }
        if (!uiPreferences.getBoolean("overviewDefaultsMigratedV2", false)) {
            uiPreferences.edit {
                putBoolean("showCgmGraph", true)
                putBoolean("showDetails", true)
                putBoolean("showMetabolicGraph", false)
                putBoolean("cgm.targetRange", true)
                putBoolean("cgm.targetValue", false)
                putBoolean("cgm.basal", false)
                putBoolean("cgm.activity", false)
                putBoolean("cgm.prediction.iob", false)
                putBoolean("cgm.prediction.cob", false)
                putBoolean("cgm.prediction.uam", false)
                putBoolean("cgm.prediction.zeroTemp", false)
                putBoolean("overviewDefaultsMigratedV2", true)
            }
        }
        content = findViewById(R.id.dashboard_content)
        scroll = findViewById(R.id.dashboard_scroll)
        screen = savedInstanceState?.getString("screen")?.let { runCatching { DashboardScreen.valueOf(it) }.getOrNull() } ?: DashboardScreen.OVERVIEW
        styleTitle()
        factory = DashboardViewFactory(this, DashboardCallbacks(
            navigate = ::navigate,
            setUnit = { uiPreferences.edit { putString("unit", it.name) } },
            setDataSource = { uiPreferences.edit { putString("dataSource", it.name) } },
            setThemeMode = { uiPreferences.edit { putString("themeMode", it.name) } },
            setShowDetails = { uiPreferences.edit { putBoolean("showDetails", it) } },
            setShowCgmGraph = { uiPreferences.edit { putBoolean("showCgmGraph", it) } },
            setCgmStream = { key, enabled ->
                uiPreferences.edit {
                    putBoolean(
                        key,
                        enabled,
                    )
                }
            },
            setShowMetabolicGraph = { uiPreferences.edit { putBoolean("showMetabolicGraph", it) } },
            setCompact = { uiPreferences.edit { putBoolean("compact", it) } },
            setLiveNotification = ::setLiveNotification,
            setNotificationGraphEnabled = { enabled ->
                uiPreferences.edit {
                    putBoolean(
                        PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED,
                        enabled,
                    )
                }
            },
            setNotificationGraphHours = { hours ->
                uiPreferences.edit {
                    putInt(
                        PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS,
                        hours.coerceIn(1, 3),
                    )
                }
            },
            setWatchFaceIndex = {
                uiPreferences.edit {
                    putInt(
                        "watchFaceIndex",
                        it.coerceIn(0, 3),
                    )
                }
            },
            syncNow = ::syncNow,
            openContactEmail = ::openContactEmail,
        ))
        bindNavigation()
        PersistentBridgeService.start(this)
        requestNotificationPermissionIfNeeded()
        scope.launch {
            TherapyStateStore(this@MainActivity).state.collectLatest {
                state = it
                refresh()
            }
        }
        refresh(forceSettingsRender = true)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        var swipeTarget: DashboardScreen? = null

        if (screen == DashboardScreen.SETTINGS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val safeStartArea = resources.displayMetrics.widthPixels * 0.70f
                    settingsSwipeTracking = event.pointerCount == 1 && event.rawX <= safeStartArea
                    settingsSwipeStartX = event.rawX
                    settingsSwipeStartY = event.rawY
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    settingsSwipeTracking = false
                }

                MotionEvent.ACTION_UP -> {
                    if (settingsSwipeTracking) {
                        swipeTarget = menuSwipeTarget(
                            screen = screen,
                            deltaX = event.rawX - settingsSwipeStartX,
                            deltaY = event.rawY - settingsSwipeStartY,
                            minimumDistancePx = 72.dp.toFloat(),
                        )
                    }
                    settingsSwipeTracking = false
                }

                MotionEvent.ACTION_CANCEL -> {
                    settingsSwipeTracking = false
                }
            }
        } else {
            settingsSwipeTracking = false
        }

        val handled = super.dispatchTouchEvent(event)
        swipeTarget?.let(::navigate)
        return handled
    }

    override fun onStart() {
        super.onStart()
        diagnostics.registerOnSharedPreferenceChangeListener(diagnosticsListener)
        uiPreferences.registerOnSharedPreferenceChangeListener(uiListener)
        clockJob = scope.launch { while (true) { delay(30.seconds); refresh() } }
        refresh()
    }

    override fun onStop() {
        clockJob?.cancel(); clockJob = null
        diagnostics.unregisterOnSharedPreferenceChangeListener(diagnosticsListener)
        uiPreferences.unregisterOnSharedPreferenceChangeListener(uiListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh(forceSettingsRender: Boolean = false) {
        if (!::content.isInitialized || !::factory.isInitialized) return
        SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
        applyRuntimeColors()
        val diagnosticState = DiagnosticsSnapshot.read(diagnostics)
        val uiState = DashboardUiPreferences.read(uiPreferences)
        if (screen != DashboardScreen.SETTINGS || forceSettingsRender) {
            factory.render(content, screen, state, diagnosticState, uiState, System.currentTimeMillis())
        }
        val sourceAvailable = diagnosticState.sourceVersion != null
        findViewById<ImageView>(R.id.source_shield).apply {
            alpha = if (sourceAvailable) 1f else 0.45f
            imageTintList = ColorStateList.valueOf(
                SugarliciousColors.argb(if (sourceAvailable) SugarliciousColorRole.PRIMARY else SugarliciousColorRole.TEXT_SECONDARY),
            )
        }
        updateNavigation()
    }


    @Suppress("DEPRECATION")
    private fun applyRuntimeColors() {
        val backgroundColor =
            SugarliciousColors.argb(
                SugarliciousColorRole.BACKGROUND,
            )
        val surface =
            SugarliciousColors.argb(
                SugarliciousColorRole.SURFACE,
            )
        val border =
            SugarliciousColors.argb(
                SugarliciousColorRole.BORDER,
            )
        val text =
            SugarliciousColors.argb(
                SugarliciousColorRole.TEXT_PRIMARY,
            )

        findViewById<View>(R.id.root)
            .setBackgroundColor(backgroundColor)
        findViewById<View>(R.id.scroll_fade).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, backgroundColor),
        )
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        val light = SugarliciousColors.palette.isLight
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            val flags = (if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0) or
                (if (light) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }

        findViewById<TextView>(R.id.app_title)
            .setTextColor(text)

        findViewById<View>(R.id.bottom_navigation).background =
            GradientDrawable().apply {
                cornerRadius = 28.dp.toFloat()
                setColor(surface)
                setStroke(1.dp, border)
            }
styleTitle()
    }
    private fun bindNavigation() {
        findViewById<View>(R.id.nav_overview).setOnClickListener { navigate(DashboardScreen.OVERVIEW) }
        findViewById<View>(R.id.nav_watch).setOnClickListener { navigate(DashboardScreen.WATCH) }
        findViewById<View>(R.id.nav_settings).setOnClickListener { navigate(DashboardScreen.SETTINGS) }
    }

    private fun navigate(target: DashboardScreen) {
        if (screen == target) return
        screen = target
        scroll.scrollTo(0, 0)
        refresh(forceSettingsRender = true)
    }

    private fun updateNavigation() {
        val entries = listOf(
            DashboardScreen.OVERVIEW to Triple(R.id.nav_overview, R.id.nav_overview_icon, R.id.nav_overview_label),
            DashboardScreen.WATCH to Triple(R.id.nav_watch, R.id.nav_watch_icon, R.id.nav_watch_label),
            DashboardScreen.SETTINGS to Triple(R.id.nav_settings, R.id.nav_settings_icon, R.id.nav_settings_label),
        )
        entries.forEach { (target, views) ->
            val (itemId, iconId, labelId) = views
            val selected = screen == target
            val color = SugarliciousColors.argb(if (selected) SugarliciousColorRole.PRIMARY else SugarliciousColorRole.TEXT_SECONDARY)
            findViewById<View>(itemId).background =
                GradientDrawable().apply {
                    cornerRadius = 20.dp.toFloat()
                    setColor(
                        if (selected) {
                            SugarliciousColors.argb(
                                SugarliciousColorRole.SURFACE_SELECTED,
                            )
                        } else {
                            Color.TRANSPARENT
                        },
                    )
                }
            findViewById<ImageView>(iconId).apply {
                imageTintList = ColorStateList.valueOf(color)
                alpha = if (selected) 1f else 0.72f
            }
            findViewById<TextView>(labelId).apply {
                setTextColor(color)
                setTypeface(
                    typeface,
                    if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
                )
            }
        }
    }

    private fun styleTitle() {
        val value = SpannableString(getString(R.string.app_name))
        value.setSpan(ForegroundColorSpan(SugarliciousColors.argb(SugarliciousColorRole.PRIMARY)), 5, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.app_title).text = value
    }

    private fun syncNow() {
        val latest = state
        if (latest == null) {
            Toast.makeText(this, "Noch keine gültigen Loop-Daten vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        diagnostics.edit { putString("lastSyncStatus", "pending") }
        scope.launch {
            runCatching { withTimeout(4.seconds) { publishState(applicationContext, latest) } }
                .onSuccess {
                    diagnostics.edit { putLong("lastSyncAt", System.currentTimeMillis()); putString("lastSyncStatus", "ok"); remove("lastSyncError") }
                    Toast.makeText(this@MainActivity, "An Watch übertragen", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    diagnostics.edit { putString("lastSyncStatus", "unavailable"); putString("lastSyncError", error.javaClass.simpleName) }
                    Toast.makeText(this@MainActivity, "Keine Watch erreichbar", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setLiveNotification(enabled: Boolean) {
        uiPreferences.edit { putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, enabled) }
        PersistentBridgeService.refresh(this)
        if (!enabled) return
        if (Build.VERSION.SDK_INT < 36) {
            Toast.makeText(this, "Live-Status benötigt Android 16; normale Benachrichtigung bleibt aktiv", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.canPostPromotedNotifications()) {
            openExternal(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }


    private fun openContactEmail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.contact_email), null))
            .putExtra(Intent.EXTRA_SUBJECT, "Sugarlicious")
        openExternal(intent)
    }

    private fun openExternal(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Keine passende App installiert", Toast.LENGTH_SHORT).show()
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 4102
    }
}
