package app.aapswear.mobile

import android.graphics.drawable.GradientDrawable
import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
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
    internal var activeDropdown: PopupWindow? = null
        private set

    private val diagnosticsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread(::refresh) }
    private val uiListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread {
                SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
                refresh()
            }
            scope.launch(Dispatchers.IO) {
                runCatching {
                    publishWatchConfig(applicationContext)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
        setContentView(R.layout.activity_main)
        if (!uiPreferences.getBoolean("graphHoursDefault3Migrated", false)) {
            uiPreferences.edit { putInt("graphHours", 3); putBoolean("graphHoursDefault3Migrated", true) }
        }
        content = findViewById(R.id.dashboard_content)
        scroll = findViewById(R.id.dashboard_scroll)
        screen = savedInstanceState?.getString("screen")?.let { runCatching { DashboardScreen.valueOf(it) }.getOrNull() } ?: DashboardScreen.OVERVIEW
        styleTitle()
        factory = DashboardViewFactory(this, DashboardCallbacks(
            navigate = ::navigate,
            cycleUnit = ::cycleUnit,
            cycleGraphHours = ::cycleGraphHours,
            setGraphHours = { uiPreferences.edit { putInt("graphHours", it) } },
            setUnit = { uiPreferences.edit { putString("unit", it.name) } },
            setShowDetails = { uiPreferences.edit { putBoolean("showDetails", it) } },
            setShowPredictions = { uiPreferences.edit { putBoolean("showPredictions", it) } },
            setShowMetabolicGraph = { uiPreferences.edit { putBoolean("showMetabolicGraph", it) } },
            setCompact = { uiPreferences.edit { putBoolean("compact", it) } },
            setLiveNotification = ::setLiveNotification,
            setWatchFaceIndex = {
                uiPreferences.edit {
                    putInt(
                        "watchFaceIndex",
                        it.coerceIn(0, 2),
                    )
                }
            },
            syncNow = ::syncNow,
            configureNightscout = { showNightscoutSetup(firstRun = false) },
            syncNightscout = { syncNightscout() },
            copyDiagnostics = ::copyDiagnostics,
            openContactEmail = ::openContactEmail,
            openGithub = ::openGithub,
        ))
        bindNavigation()
        findViewById<View>(R.id.menu_button).setOnClickListener(::showSectionMenu)
        findViewById<View>(R.id.more_button).setOnClickListener(::showMoreMenu)
        PersistentBridgeService.start(this)
        requestNotificationPermissionIfNeeded()
        scope.launch {
            TherapyStateStore(this@MainActivity).state.collectLatest {
                state = it
                refresh()
            }
        }
        scope.launch {
            NightscoutBackfillCoordinator.syncIfNeeded(applicationContext)
        }
        window.decorView.post {
            if (NightscoutConfigStore.shouldOfferSetup(this)) {
                showNightscoutSetup(firstRun = true)
            }
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        diagnostics.registerOnSharedPreferenceChangeListener(diagnosticsListener)
        uiPreferences.registerOnSharedPreferenceChangeListener(uiListener)
        clockJob = scope.launch { while (true) { delay(30.seconds); refresh() } }
        refresh()
    }

    override fun onStop() {
        activeDropdown?.dismiss()
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

    private fun refresh() {
        if (!::content.isInitialized || !::factory.isInitialized) return
        SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
        applyRuntimeColors()
        val diagnosticState = DiagnosticsSnapshot.read(diagnostics)
        val uiState = DashboardUiPreferences.read(uiPreferences)
        factory.render(content, screen, state, diagnosticState, uiState, System.currentTimeMillis())
        val sourceAvailable = diagnosticState.sourceVersion != null
        findViewById<ImageView>(R.id.source_shield).apply {
            alpha = if (sourceAvailable) 1f else 0.45f
            imageTintList = ColorStateList.valueOf(
                SugarliciousColors.argb(if (sourceAvailable) SugarliciousColorRole.PRIMARY else SugarliciousColorRole.TEXT_SECONDARY),
            )
        }
        updateNavigation()
    }


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

        findViewById<TextView>(R.id.app_title)
            .setTextColor(text)

        val iconButtonBackground =
            SugarliciousColors.argb(
                SugarliciousColorRole.SURFACE_HIGH,
            )

        findViewById<ImageView>(R.id.menu_button).apply {
            setColorFilter(text)
            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.OVAL
                    setColor(iconButtonBackground)
                }
        }
        findViewById<ImageView>(R.id.more_button).apply {
            setColorFilter(text)
            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.OVAL
                    setColor(iconButtonBackground)
                }
        }

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
        refresh()
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

    private fun showSectionMenu(anchor: View) {
        showPillDropdown(anchor, alignEnd = false, listOf(
            PillMenuItem(R.id.dropdown_overview, "Übersicht", screen == DashboardScreen.OVERVIEW) { navigate(DashboardScreen.OVERVIEW) },
            PillMenuItem(View.generateViewId(), "Watch", screen == DashboardScreen.WATCH) { navigate(DashboardScreen.WATCH) },
            PillMenuItem(R.id.dropdown_settings, "Einstellungen", screen == DashboardScreen.SETTINGS) { navigate(DashboardScreen.SETTINGS) },
        ))
    }

    private fun showMoreMenu(anchor: View) {
        showPillDropdown(anchor, alignEnd = true, listOf(
            PillMenuItem(R.id.dropdown_sync, "Jetzt an Watch senden", action = ::syncNow),
            PillMenuItem(R.id.dropdown_diagnostics, "Diagnose kopieren", action = ::copyDiagnostics),
            PillMenuItem(R.id.dropdown_app_info, "Einstellungen & App-Info") { navigate(DashboardScreen.SETTINGS) },
        ))
    }

    private fun showPillDropdown(anchor: View, alignEnd: Boolean, items: List<PillMenuItem>) {
        activeDropdown?.dismiss()
        val panel = LinearLayout(this).apply {
            id = R.id.dropdown_panel
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            background = GradientDrawable().apply { cornerRadius = 22.dp.toFloat(); setColor(SugarliciousColors.argb(SugarliciousColorRole.SURFACE)); setStroke(1.dp, SugarliciousColors.argb(SugarliciousColorRole.BORDER)) }
        }
        lateinit var popup: PopupWindow
        items.forEachIndexed { index, item ->
            panel.addView(TextView(this).apply {
                id = item.id
                text = item.label
                textSize = 14f
                setTextColor(SugarliciousColors.argb(if (item.selected) SugarliciousColorRole.PRIMARY else SugarliciousColorRole.TEXT_PRIMARY))
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 46.dp
                setPadding(18.dp, 0, 18.dp, 0)
                background = GradientDrawable().apply { cornerRadius = 18.dp.toFloat(); setColor(if (item.selected) SugarliciousColors.argb(SugarliciousColorRole.SURFACE_SELECTED) else SugarliciousColors.argb(SugarliciousColorRole.SURFACE_HIGH)); if (item.selected) setStroke(1.dp, SugarliciousColors.argb(SugarliciousColorRole.PRIMARY)) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    popup.dismiss()
                    item.action()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = 6.dp
            })
        }
        val menuWidth = 232.dp
        popup = PopupWindow(panel, menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            elevation = 16.dp.toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener { if (activeDropdown === this) activeDropdown = null }
        }
        activeDropdown = popup
        val xOffset = if (alignEnd) anchor.width - menuWidth else 0
        popup.showAsDropDown(anchor, xOffset, 4.dp)
    }

    private fun cycleUnit() {
        val current = DashboardUiPreferences.read(uiPreferences).unit
        val next = when (current) { DisplayUnitPreference.AAPS -> DisplayUnitPreference.MG_DL; DisplayUnitPreference.MG_DL -> DisplayUnitPreference.MMOL_L; DisplayUnitPreference.MMOL_L -> DisplayUnitPreference.AAPS }
        uiPreferences.edit { putString("unit", next.name) }
    }

    private fun cycleGraphHours() {
        val current = DashboardUiPreferences.read(uiPreferences).graphHours
        val next = when (current) {
            3 -> 6
            6 -> 12
            12 -> 24
            else -> 3
        }
        uiPreferences.edit { putInt("graphHours", next) }
    }

    private fun syncNow() {
        val latest = state
        if (latest == null) {
            Toast.makeText(this, "Noch keine gültigen AAPS-Daten vorhanden", Toast.LENGTH_SHORT).show()
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

    private fun syncNightscout() {
        scope.launch {
            val result = NightscoutBackfillCoordinator.syncIfNeeded(applicationContext, force = true)
            when (result.status) {
                NightscoutBackfillResult.Status.OK ->
                    Toast.makeText(this@MainActivity, "${result.pointCount} Nightscout-Werte synchronisiert", Toast.LENGTH_SHORT).show()
                NightscoutBackfillResult.Status.NOT_CONFIGURED ->
                    showNightscoutSetup(firstRun = false)
                NightscoutBackfillResult.Status.ERROR ->
                    Toast.makeText(this@MainActivity, "Nightscout-Backfill fehlgeschlagen: ${result.message ?: "unbekannt"}", Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    private fun showNightscoutSetup(firstRun: Boolean) {
        val existing = NightscoutConfigStore.load(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 6.dp, 24.dp, 0)
        }
        val urlInput = EditText(this).apply {
            hint = "https://dein-nightscout.example"
            setText(existing?.baseUrl.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val tokenInput = EditText(this).apply {
            hint = "Read-only Access Token (optional)"
            setText(existing?.accessToken.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        container.addView(urlInput)
        container.addView(tokenInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (firstRun) "24h-Verlauf aus Nightscout" else "Nightscout")
            .setMessage("Sugarlicious nutzt Nightscout nur lesend für den initialen 24h-Backfill und zum Reparieren von Graph-Lücken. AndroidAPS bleibt die Live-Datenquelle.")
            .setView(container)
            .setNegativeButton(if (firstRun) "Später" else "Abbrechen") { _, _ ->
                if (firstRun) NightscoutConfigStore.markSetupPromptShown(this)
            }
            .setPositiveButton("Speichern", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    NightscoutConfigStore.save(
                        this,
                        urlInput.text.toString(),
                        tokenInput.text.toString(),
                    )
                }.onSuccess {
                    dialog.dismiss()
                    Toast.makeText(this, "Nightscout gespeichert", Toast.LENGTH_SHORT).show()
                    syncNightscout()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Ungültige Nightscout-Konfiguration", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
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

    private fun copyDiagnostics() {
        val d = DiagnosticsSnapshot.read(diagnostics)
        val report = buildString {
            appendLine("Sugarlicious 0.5.1")
            appendLine("Quelle: ${d.sourcePackage ?: "—"}")
            appendLine("AAPS: ${d.sourceVersion ?: "—"}")
            appendLine("Vertrag: ${d.sourceContract ?: "—"}")
            appendLine("Schema: ${state?.schemaVersion ?: "—"}")
            appendLine("Fähigkeiten: ${state?.capabilities?.size ?: 0}")
            appendLine("Uhren erreichbar: ${d.reachableWatches}")
            appendLine("Sync: ${d.syncStatus ?: "—"}")
            appendLine("Nightscout Backfill: ${d.historyBackfillStatus ?: "—"}")
            appendLine("Nightscout Punkte: ${d.historyBackfillPointCount}")
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Sugarlicious Diagnose", report))
        Toast.makeText(this, "Diagnose ohne Therapiewerte kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun openGithub() {
        openExternal(Intent(Intent.ACTION_VIEW, getString(R.string.github_url).toUri()))
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

    private data class PillMenuItem(
        val id: Int,
        val label: String,
        val selected: Boolean = false,
        val action: () -> Unit,
    )

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 4102
    }
}
