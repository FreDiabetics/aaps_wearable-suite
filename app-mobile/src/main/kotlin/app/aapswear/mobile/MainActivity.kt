package app.aapswear.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

class MainActivity : Activity() {
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
    private val uiListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread(::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.dashboard_content)
        scroll = findViewById(R.id.dashboard_scroll)
        screen = savedInstanceState?.getString("screen")?.let { runCatching { DashboardScreen.valueOf(it) }.getOrNull() } ?: DashboardScreen.OVERVIEW
        styleTitle()
        factory = DashboardViewFactory(this, DashboardCallbacks(
            navigate = ::navigate,
            cycleUnit = ::cycleUnit,
            cycleGraphHours = ::cycleGraphHours,
            setGraphHours = { uiPreferences.edit().putInt("graphHours", it).apply() },
            setUnit = { uiPreferences.edit().putString("unit", it.name).apply() },
            setShowDetails = { uiPreferences.edit().putBoolean("showDetails", it).apply() },
            setShowPredictions = { uiPreferences.edit().putBoolean("showPredictions", it).apply() },
            setCompact = { uiPreferences.edit().putBoolean("compact", it).apply() },
            syncNow = ::syncNow,
            openContactEmail = ::openContactEmail,
            openGithub = ::openGithub,
        ))
        bindNavigation()
        findViewById<View>(R.id.menu_button).setOnClickListener(::showSectionMenu)
        findViewById<View>(R.id.more_button).setOnClickListener(::showMoreMenu)
        scope.launch {
            TherapyStateStore(this@MainActivity).state.collectLatest {
                state = it
                refresh()
            }
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        diagnostics.registerOnSharedPreferenceChangeListener(diagnosticsListener)
        uiPreferences.registerOnSharedPreferenceChangeListener(uiListener)
        clockJob = scope.launch { while (true) { delay(30_000L); refresh() } }
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
        val diagnosticState = DiagnosticsSnapshot.read(diagnostics)
        val uiState = DashboardUiPreferences.read(uiPreferences)
        factory.render(content, screen, state, diagnosticState, uiState, System.currentTimeMillis())
        findViewById<ImageView>(R.id.source_shield).alpha = if (diagnosticState.sourceVersion != null) 1f else 0.35f
        updateNavigation()
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.nav_overview).setOnClickListener { navigate(DashboardScreen.OVERVIEW) }
        findViewById<View>(R.id.nav_history).setOnClickListener { navigate(DashboardScreen.HISTORY) }
        findViewById<View>(R.id.nav_data).setOnClickListener { navigate(DashboardScreen.DATA) }
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
            Triple(DashboardScreen.OVERVIEW, R.id.nav_overview_icon, R.id.nav_overview_label),
            Triple(DashboardScreen.HISTORY, R.id.nav_history_icon, R.id.nav_history_label),
            Triple(DashboardScreen.DATA, R.id.nav_data_icon, R.id.nav_data_label),
            Triple(DashboardScreen.SETTINGS, R.id.nav_settings_icon, R.id.nav_settings_label),
        )
        entries.forEach { (target, iconId, labelId) ->
            val color = getColor(if (screen == target) R.color.app_cyan else R.color.app_text_secondary)
            findViewById<ImageView>(iconId).imageTintList = ColorStateList.valueOf(color)
            findViewById<TextView>(labelId).setTextColor(color)
        }
    }

    private fun styleTitle() {
        val value = SpannableString(getString(R.string.app_name))
        value.setSpan(ForegroundColorSpan(getColor(R.color.app_cyan)), 5, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.app_title).text = value
    }

    private fun showSectionMenu(anchor: View) {
        showPillDropdown(anchor, alignEnd = false, listOf(
            PillMenuItem(R.id.dropdown_overview, "Übersicht", screen == DashboardScreen.OVERVIEW) { navigate(DashboardScreen.OVERVIEW) },
            PillMenuItem(R.id.dropdown_history, "Verlauf", screen == DashboardScreen.HISTORY) { navigate(DashboardScreen.HISTORY) },
            PillMenuItem(R.id.dropdown_data, "Daten", screen == DashboardScreen.DATA) { navigate(DashboardScreen.DATA) },
            PillMenuItem(R.id.dropdown_settings, "Einstellungen", screen == DashboardScreen.SETTINGS) { navigate(DashboardScreen.SETTINGS) },
        ))
    }

    private fun showMoreMenu(anchor: View) {
        showPillDropdown(anchor, alignEnd = true, listOf(
            PillMenuItem(R.id.dropdown_sync, "Jetzt synchronisieren", action = ::syncNow),
            PillMenuItem(R.id.dropdown_diagnostics, "Diagnose kopieren", action = ::copyDiagnostics),
            PillMenuItem(R.id.dropdown_app_info, "Datenschutz & App-Info") { navigate(DashboardScreen.SETTINGS) },
        ))
    }

    private fun showPillDropdown(anchor: View, alignEnd: Boolean, items: List<PillMenuItem>) {
        activeDropdown?.dismiss()
        val panel = LinearLayout(this).apply {
            id = R.id.dropdown_panel
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            setBackgroundResource(R.drawable.bg_dropdown_panel)
        }
        lateinit var popup: PopupWindow
        items.forEachIndexed { index, item ->
            panel.addView(TextView(this).apply {
                id = item.id
                text = item.label
                textSize = 14f
                setTextColor(getColor(if (item.selected) R.color.app_cyan else R.color.app_text))
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 46.dp
                setPadding(18.dp, 0, 18.dp, 0)
                setBackgroundResource(if (item.selected) R.drawable.bg_dropdown_pill_selected else R.drawable.bg_dropdown_pill)
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
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
        uiPreferences.edit().putString("unit", next.name).apply()
    }

    private fun cycleGraphHours() {
        val current = DashboardUiPreferences.read(uiPreferences).graphHours
        uiPreferences.edit().putInt("graphHours", when (current) { 6 -> 12; 12 -> 24; else -> 6 }).apply()
    }

    private fun syncNow() {
        val latest = state
        if (latest == null) {
            Toast.makeText(this, "Noch keine gültigen AAPS-Daten vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        diagnostics.edit().putString("lastSyncStatus", "pending").apply()
        scope.launch {
            runCatching { withTimeout(4_000L) { publishState(applicationContext, latest) } }
                .onSuccess {
                    diagnostics.edit().putLong("lastSyncAt", System.currentTimeMillis()).putString("lastSyncStatus", "ok").remove("lastSyncError").apply()
                    Toast.makeText(this@MainActivity, "An Watch übertragen", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    diagnostics.edit().putString("lastSyncStatus", "unavailable").putString("lastSyncError", error.javaClass.simpleName).apply()
                    Toast.makeText(this@MainActivity, "Keine Watch erreichbar", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun copyDiagnostics() {
        val d = DiagnosticsSnapshot.read(diagnostics)
        val report = buildString {
            appendLine("Sugarlicious 0.5.0")
            appendLine("Quelle: ${d.sourcePackage ?: "—"}")
            appendLine("AAPS: ${d.sourceVersion ?: "—"}")
            appendLine("Vertrag: ${d.sourceContract ?: "—"}")
            appendLine("Schema: ${state?.schemaVersion ?: "—"}")
            appendLine("Fähigkeiten: ${state?.capabilities?.size ?: 0}")
            appendLine("Uhren erreichbar: ${d.reachableWatches}")
            appendLine("Sync: ${d.syncStatus ?: "—"}")
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Sugarlicious Diagnose", report))
        Toast.makeText(this, "Diagnose ohne Therapiewerte kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun openGithub() {
        openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_url))))
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
}
