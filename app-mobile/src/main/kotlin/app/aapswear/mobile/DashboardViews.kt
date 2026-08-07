package app.aapswear.mobile

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

enum class DashboardScreen { OVERVIEW, WATCH, SETTINGS }
enum class DisplayUnitPreference { AAPS, MG_DL, MMOL_L }

data class DashboardUiPreferences(
    val unit: DisplayUnitPreference = DisplayUnitPreference.AAPS,
    val showDetails: Boolean = true,
    val showPredictions: Boolean = true,
    val compact: Boolean = true,
    val graphHours: Int = 6,
    val liveNotification: Boolean = false,
) {
    fun unitFor(state: TherapyDisplayState?): GlucoseUnit = when (unit) {
        DisplayUnitPreference.AAPS -> state?.glucose?.displayUnit ?: GlucoseUnit.MG_DL
        DisplayUnitPreference.MG_DL -> GlucoseUnit.MG_DL
        DisplayUnitPreference.MMOL_L -> GlucoseUnit.MMOL_L
    }

    companion object {
        fun read(preferences: SharedPreferences) = DashboardUiPreferences(
            unit = runCatching { DisplayUnitPreference.valueOf(preferences.getString("unit", "AAPS")!!) }.getOrDefault(DisplayUnitPreference.AAPS),
            showDetails = preferences.getBoolean("showDetails", true),
            showPredictions = preferences.getBoolean("showPredictions", true),
            compact = preferences.getBoolean("compact", true),
            graphHours = preferences.getInt("graphHours", 6).takeIf { it in listOf(6, 12, 24) } ?: 6,
            liveNotification = preferences.getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false),
        )
    }
}

data class DiagnosticsSnapshot(
    val sourceVersion: String?,
    val sourceContract: String?,
    val sourcePackage: String?,
    val receivedAt: Long,
    val measuredAt: Long,
    val reachableWatches: Int,
    val lastSyncAt: Long,
    val syncStatus: String?,
    val syncError: String?,
    val historyBackfillStatus: String?,
    val historyBackfillPointCount: Int,
    val historyBackfillRequestedAt: Long,
    val historyBackfillReceivedAt: Long,
) {
    companion object {
        fun read(preferences: SharedPreferences) = DiagnosticsSnapshot(
            sourceVersion = preferences.getString("sourceVersion", null),
            sourceContract = preferences.getString("contract", null),
            sourcePackage = preferences.getString("sourcePackage", null),
            receivedAt = preferences.getLong("received", 0L),
            measuredAt = preferences.getLong("measurement", 0L),
            reachableWatches = preferences.getInt("reachableWatches", 0),
            lastSyncAt = preferences.getLong("lastSyncAt", 0L),
            syncStatus = preferences.getString("lastSyncStatus", null),
            syncError = preferences.getString("lastSyncError", null),
            historyBackfillStatus = preferences.getString("historyBackfillStatus", null),
            historyBackfillPointCount = preferences.getInt("historyBackfillPointCount", 0),
            historyBackfillRequestedAt = preferences.getLong("historyBackfillRequestedAt", 0L),
            historyBackfillReceivedAt = preferences.getLong("historyBackfillReceivedAt", 0L),
        )
    }
}

data class DashboardCallbacks(
    val navigate: (DashboardScreen) -> Unit,
    val cycleUnit: () -> Unit,
    val cycleGraphHours: () -> Unit,
    val setGraphHours: (Int) -> Unit,
    val setUnit: (DisplayUnitPreference) -> Unit,
    val setShowDetails: (Boolean) -> Unit,
    val setShowPredictions: (Boolean) -> Unit,
    val setCompact: (Boolean) -> Unit,
    val setLiveNotification: (Boolean) -> Unit,
    val syncNow: () -> Unit,
    val configureNightscout: () -> Unit,
    val syncNightscout: () -> Unit,
    val copyDiagnostics: () -> Unit,
    val openContactEmail: () -> Unit,
    val openGithub: () -> Unit,
)

class DashboardViewFactory(
    private val context: Context,
    private val callbacks: DashboardCallbacks,
) {
    private val density = context.resources.displayMetrics.density
    private val text = context.getColor(R.color.app_text)
    private val secondary = context.getColor(R.color.app_text_secondary)
    private val green = context.getColor(R.color.app_green)
    private val cyan = context.getColor(R.color.app_cyan)
    private val accent = context.getColor(R.color.app_accent)
    private val blue = context.getColor(R.color.app_blue)
    private val orange = context.getColor(R.color.app_orange)
    private val purple = context.getColor(R.color.app_purple)

    fun render(
        parent: LinearLayout,
        screen: DashboardScreen,
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        preferences: DashboardUiPreferences,
        now: Long,
    ) {
        parent.removeAllViews()
        when (screen) {
            DashboardScreen.OVERVIEW -> renderOverview(parent, state, diagnostics, preferences, now)
            DashboardScreen.WATCH -> renderWatch(parent, state, diagnostics, now)
            DashboardScreen.SETTINGS -> renderSettings(parent, state, diagnostics, preferences)
        }
    }

    private fun renderOverview(parent: LinearLayout, state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, prefs: DashboardUiPreferences, now: Long) {
        val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                app.aapswear.mobile.ui.theme.SugarliciousTheme {
                    SugarliciousOverviewScreen(
                        state = state,
                        diagnostics = diagnostics,
                        preferences = prefs,
                        now = now,
                        callbacks = callbacks,
                    )
                }
            }
        }
        parent.addView(composeView, fullWidth())

        val freshness = FreshnessPolicy.classify(state?.glucose?.measuredAtEpochMs, now)
        val current = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
        parent.addView(connectionCard(state, diagnostics, current), cardParams(top = 8))
    }

    private fun renderWatch(parent: LinearLayout, state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, now: Long) {
        val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                app.aapswear.mobile.ui.theme.SugarliciousTheme {
                    SugarliciousWatchScreen(
                        state = state,
                        diagnostics = diagnostics,
                        now = now,
                        onSyncNow = callbacks.syncNow,
                    )
                }
            }
        }
        parent.addView(composeView, fullWidth())
    }

    private fun renderHistory(parent: LinearLayout, state: TherapyDisplayState?, prefs: DashboardUiPreferences) {
        parent.addView(screenTitle("Verlauf", "AAPS-Livedaten + echte Nightscout-Backfill-Werte"))
        parent.addView(glucoseGraphCard(state, prefs, compact = false, large = true), cardParams())
        parent.addView(metabolicGraphCard(state, prefs, compact = false, large = true), cardParams())
        parent.addView(infoCard("Datenbasis", listOf(
            "Glukoseverlauf" to "maximal 24 Stunden im letzten Anzeigezustand",
            "Prognosen" to if (state?.glucosePredictions.isNullOrEmpty()) "im AAPS-Payload nicht verfügbar" else "Suggested/Enacted predBGs",
            "IOB/COB" to "aus den fortlaufend empfangenen Statuswerten",
        )), cardParams())
    }

    private fun renderData(parent: LinearLayout, state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, prefs: DashboardUiPreferences, now: Long) {
        parent.addView(screenTitle("Daten", "Vollständiger read-only Anzeigestatus"))
        val unit = prefs.unitFor(state)
        val freshness = FreshnessPolicy.classify(state?.glucose?.measuredAtEpochMs, now)
        val display = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
        parent.addView(infoCard("Glukose", listOf(
            "Wert" to state?.glucose?.takeIf { display }?.let { glucose(it.valueMgDl, unit) + " " + unitLabel(unit) }.orDash(),
            "Trend" to state?.glucose?.takeIf { display }?.let { TherapyDisplayFormatter.trendArrow(it.trend) }.orDash(),
            "Delta" to state?.glucose?.takeIf { display }?.let { signedDelta(it.deltaMgDl, unit) }.orDash(),
            "Durchschnittsdelta" to state?.glucose?.takeIf { display }?.let { signedDelta(it.averageDeltaMgDl, unit) }.orDash(),
            "Alter" to TherapyDisplayFormatter.ageMinutes(state?.glucose?.measuredAtEpochMs, now),
            "Ziel" to TherapyDisplayFormatter.target(state?.target, unit),
        )), cardParams())
        parent.addView(infoCard("Insulin & Kohlenhydrate", listOf(
            "IOB gesamt" to units(state.takeIf { display }?.insulin?.totalIob, " IE", 2),
            "Bolus-IOB" to units(state.takeIf { display }?.insulin?.bolusIob, " IE", 2),
            "Basal-IOB" to units(state.takeIf { display }?.insulin?.basalIob, " IE", 2),
            "COB" to units(state.takeIf { display }?.carbs?.cobGrams, " g", 0),
            "Zukünftige KH" to units(state.takeIf { display }?.carbs?.futureCarbsGrams, " g", 0),
        )), cardParams())
        parent.addView(infoCard("Basal & Loop", listOf(
            "Basalrate" to units(state.takeIf { display }?.basal?.currentUnitsPerHour, " IE/h", 2),
            "Temporäre Basalrate" to (state.takeIf { display }?.basal?.displayText ?: state.takeIf { display }?.basal?.tempAbsoluteUnitsPerHour?.let { units(it, " IE/h", 2) }).orDash(),
            "Temporär verbleibend" to remaining(state.takeIf { display }?.basal?.tempEndsAtEpochMs, now),
            "Loopstatus" to state.takeIf { display }?.loop?.status.orDash(),
            "Letzter Loop" to state.takeIf { display }?.loop?.lastRunAtEpochMs.asDateTime(),
            "Profil" to state.takeIf { display }?.profile?.name.orDash(),
        )), cardParams())
        parent.addView(infoCard("Pumpe & Geräte", listOf(
            "Pumpenstatus" to state.takeIf { display }?.pump?.status.orDash(),
            "Reservoir" to units(state.takeIf { display }?.pump?.reservoirUnits, " IE", 1),
            "Pumpenakku" to state.takeIf { display }?.pump?.batteryPercent.percent(),
            "Telefonakku" to state.takeIf { display }?.device?.phoneBatteryPercent.percent(),
            "Uhren erreichbar" to diagnostics.reachableWatches.toString(),
        )), cardParams())
        parent.addView(infoCard("Datenvertrag", listOf(
            "AndroidAPS" to diagnostics.sourceVersion.orDash(),
            "Vertrag" to diagnostics.sourceContract.orDash(),
            "Schema" to (state?.schemaVersion?.toString() ?: "—"),
            "Fähigkeiten" to (state?.capabilities?.size?.toString() ?: "0"),
            "Letzter Empfang" to diagnostics.receivedAt.asDateTime(),
        )), cardParams())
    }

    private fun renderSettings(parent: LinearLayout, state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, prefs: DashboardUiPreferences) {
        parent.addView(screenTitle("Einstellungen", "Sugarlicious als AndroidAPS → Wear OS Bridge konfigurieren"))

        parent.addView(infoCard("BRIDGE-STATUS", listOf(
            "AndroidAPS" to diagnostics.sourceVersion.orDash(),
            "Datenvertrag" to diagnostics.sourceContract.orDash(),
            "Watch erreichbar" to if (diagnostics.reachableWatches > 0) "${diagnostics.reachableWatches}" else "nein",
            "Letzte Übertragung" to diagnostics.lastSyncAt.takeIf { it > 0L }.asDateTime(),
            "Sync-Status" to syncText(diagnostics.syncStatus),
        ), action = "JETZT AN WATCH SENDEN" to callbacks.syncNow), cardParams())

        val display = tile("SMARTPHONE-VORSCHAU")
        display.addView(sectionLabel("Glukose-Einheit"))
        display.addView(chipRow(listOf(
            Triple("AAPS", prefs.unit == DisplayUnitPreference.AAPS) { callbacks.setUnit(DisplayUnitPreference.AAPS) },
            Triple("mg/dL", prefs.unit == DisplayUnitPreference.MG_DL) { callbacks.setUnit(DisplayUnitPreference.MG_DL) },
            Triple("mmol/L", prefs.unit == DisplayUnitPreference.MMOL_L) { callbacks.setUnit(DisplayUnitPreference.MMOL_L) },
        )))
        display.addView(helper("Die Smartphone-App bleibt eine Vorschau der Wear-Bridge. AndroidAPS ist die Live-Datenquelle."))
        display.addView(switchRow("Therapiedetails anzeigen", "IOB, COB, Basal und Profil auf der Übersicht", prefs.showDetails, R.id.dashboard_details_switch, callbacks.setShowDetails))
        display.addView(switchRow("AAPS-Prognosen anzeigen", "Nur vorhandene predBGs; Sugarlicious berechnet keine Therapieprognose", prefs.showPredictions, R.id.dashboard_predictions_switch, callbacks.setShowPredictions))
        display.addView(switchRow("Kompakte Übersicht", "Dichte Darstellung für die Bridge-Übersicht", prefs.compact, R.id.dashboard_compact_switch, callbacks.setCompact))
        display.addView(switchRow("Live-Benachrichtigung", "Optionaler Android-16-Live-Status; normale Benachrichtigung bleibt sonst aktiv", prefs.liveNotification, R.id.dashboard_live_notification_switch, callbacks.setLiveNotification))
        display.addView(sectionLabel("Graph-Zeitraum"))
        display.addView(chipRow(listOf(6, 12, 24).map { hours ->
            Triple("$hours h", prefs.graphHours == hours) { callbacks.setGraphHours(hours) }
        }))
        parent.addView(display, cardParams())

        val nightscoutConfig = NightscoutConfigStore.load(context)
        val nightscout = tile("NIGHTSCOUT · HISTORISCHER BACKFILL")
        nightscout.addView(infoRow("Konfiguration", nightscoutConfig?.baseUrl ?: "nicht eingerichtet"))
        nightscout.addView(infoRow("Access Token", if (nightscoutConfig?.accessToken != null) "verschlüsselt gespeichert" else "nicht gesetzt"))
        nightscout.addView(infoRow("Status", backfillText(diagnostics)))
        nightscout.addView(infoRow("Punkte im 24h-Puffer", diagnostics.historyBackfillPointCount.toString()))
        nightscout.addView(infoRow("Letzter Backfill", diagnostics.historyBackfillReceivedAt.takeIf { it > 0L }.asDateTime()))
        nightscout.addView(helper("Nightscout dient nur für 24h-Historie und Gap-Reparatur. Live-Daten kommen weiterhin aus AndroidAPS.", 3))
        nightscout.addView(chipRow(listOf(
            Triple(if (nightscoutConfig == null) "EINRICHTEN" else "BEARBEITEN", true) { callbacks.configureNightscout() },
            Triple("24H AKTUALISIEREN", nightscoutConfig != null) { callbacks.syncNightscout() },
        )))
        parent.addView(nightscout, cardParams())

        parent.addView(infoCard("DIAGNOSE", listOf(
            "AAPS-Paket" to diagnostics.sourcePackage.orDash(),
            "AAPS-Version" to diagnostics.sourceVersion.orDash(),
            "Vertrag" to diagnostics.sourceContract.orDash(),
            "Schema" to (state?.schemaVersion?.toString() ?: "—"),
            "Fähigkeiten" to (state?.capabilities?.size?.toString() ?: "0"),
            "Letzter AAPS-Empfang" to diagnostics.receivedAt.takeIf { it > 0L }.asDateTime(),
            "Letzter Messwert" to diagnostics.measuredAt.takeIf { it > 0L }.asDateTime(),
            "Sync-Fehler" to diagnostics.syncError.orDash(),
        ), action = "DIAGNOSE KOPIEREN" to callbacks.copyDiagnostics), cardParams())

        parent.addView(infoCard("DATENSCHUTZ & SICHERHEIT", listOf(
            "Betriebsart" to "strikt read-only",
            "Datenweg" to "AndroidAPS → Sugarlicious → Wear OS",
            "Nightscout" to "nur lesend für Historie / Gap-Reparatur",
            "Cloud / Telemetrie" to "keine Sugarlicious-Telemetrie",
            "Token" to "Android Keystore · AES/GCM",
        )), cardParams())

        parent.addView(aboutCard(), cardParams(bottom = 10))
    }

    private fun aboutCard(): View = tile("ÜBER SUGARLICIOUS").apply {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp, 0, 8.dp)
        }
        header.addView(ImageView(context).apply {
            setImageResource(R.drawable.frediabetics_logo)
            contentDescription = context.getString(R.string.brand_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(58.dp, 58.dp))
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 0, 0, 0)
            addView(value("Sugarlicious", text, 20f, 1))
            addView(helper("Version 0.5.1 · FreDiabetics · Open Source", 2, accent))
            addView(helper("Lokale, strikt read-only AndroidAPS-Anzeige", 2))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(header)
        addView(infoRow("Kontakt", context.getString(R.string.contact_email)))
        addView(infoRow("GitHub", "FreDiabetics/aaps_wearable-suite"))
        addView(chipRow(listOf(
            Triple("E-MAIL", true) { callbacks.openContactEmail() },
            Triple("GITHUB", true) { callbacks.openGithub() },
        )).also { row ->
            row.getChildAt(0).id = R.id.dashboard_contact_email
            row.getChildAt(1).id = R.id.dashboard_github
        })
    }

    private fun glucoseGraphCard(state: TherapyDisplayState?, prefs: DashboardUiPreferences, compact: Boolean, large: Boolean = false, chartHeightDp: Int? = null): View {
        val card = tile(null)
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(sectionLabel("GLUKOSEVERLAUF"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(chip("${prefs.graphHours} h", true, callbacks.cycleGraphHours))
        card.addView(header)
        card.addView(legendRow())
        card.addView(GlucoseDashboardChart(context).apply {
            bind(state, prefs.unitFor(state), prefs.showPredictions, prefs.graphHours)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (chartHeightDp ?: if (large) 370 else if (compact) 160 else 220).dp))
        return card
    }

    private fun metabolicGraphCard(state: TherapyDisplayState?, prefs: DashboardUiPreferences, compact: Boolean, large: Boolean = false, chartHeightDp: Int? = null): View {
        val card = tile(null)
        card.addView(sectionLabel("INSULIN & KOHLENHYDRATE"))
        card.addView(MetabolicDashboardChart(context).apply { bind(state, prefs.graphHours) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (chartHeightDp ?: if (large) 390 else if (compact) 175 else 245).dp))
        return card
    }

    private fun legendRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(legend("●", "CGM", green)); addView(legend("●", "Prognose", context.getColor(R.color.app_yellow))); addView(legend("■", "80–160", Color.rgb(16, 93, 40)))
    }

    private fun legend(symbol: String, label: String, color: Int) = TextView(context).apply {
        text = context.getString(R.string.legend_item, symbol, label); textSize = 10f; setTextColor(color); setPadding(0, 3.dp, 14.dp, 4.dp)
    }

    private fun summaryTile(title: String, value: String, sub: String, valueColor: Int, id: Int = View.NO_ID, click: (() -> Unit)? = null, valueSize: Float = 26f, subColor: Int = secondary, minHeightDp: Int = 104) = tile(null).apply {
        if (id != View.NO_ID) this.id = id
        minimumHeight = minHeightDp.dp
        addView(label(title)); addView(value(value, valueColor, valueSize, maxLines = 1)); addView(helper(sub, maxLines = 2, color = subColor))
        if (click != null) { isClickable = true; isFocusable = true; foreground = selectableForeground(); setOnClickListener { click() } }
    }

    /**
     * Primary glucose tile: current value and Sugarlicious trend artwork are one visual unit.
     * AndroidAPS remains authoritative for the Trend enum.
     */
    private fun glucoseTile(
        glucoseText: String,
        trend: Trend,
        sub: String,
        valueColor: Int,
        minHeightDp: Int = 104,
    ) = tile(null).apply {
        id = R.id.dashboard_glucose
        minimumHeight = minHeightDp.dp
        addView(label("GLUKOSE"))

        val valueRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        valueRow.addView(value(glucoseText, valueColor, 28f, maxLines = 1))

        trendArrowCluster(trend, 16)?.let { arrow ->
            valueRow.addView(
                arrow,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 18.dp).apply {
                    marginStart = 3.dp
                },
            )
        }

        addView(valueRow)
        addView(helper(sub, maxLines = 1))
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
        setOnClickListener { callbacks.cycleUnit() }
    }

    private fun trendArrowCluster(trend: Trend, arrowSizeDp: Int): View? {
        val rotation = when (trend) {
            Trend.DOUBLE_UP, Trend.SINGLE_UP -> -90f
            Trend.FORTY_FIVE_UP -> -45f
            Trend.FLAT -> 0f
            Trend.FORTY_FIVE_DOWN -> 45f
            Trend.SINGLE_DOWN, Trend.DOUBLE_DOWN -> 90f
            Trend.UNKNOWN -> return null
        }
        val copies = if (trend == Trend.DOUBLE_UP || trend == Trend.DOUBLE_DOWN) 2 else 1

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            repeat(copies) { index ->
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_trend_arrow)
                        clearColorFilter()
                        this.rotation = rotation
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                    },
                    LinearLayout.LayoutParams(arrowSizeDp.dp, arrowSizeDp.dp).apply {
                        if (copies == 2 && index == 1) marginStart = (-6).dp
                    },
                )
            }
        }
    }

    private fun statTile(title: String, value: String, suffix: String, color: Int, minHeightDp: Int = 84) = tile(null).apply {
        minimumHeight = minHeightDp.dp; addView(label(title, color)); addView(value(value, text, 19f, maxLines = 1)); addView(helper(suffix, 1))
    }

    private fun connectionCard(state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, current: Boolean): View {
        val connected = diagnostics.reachableWatches > 0 && diagnostics.syncStatus == "ok"
        val nightscoutText = when (diagnostics.historyBackfillStatus) {
            "ok" -> "${diagnostics.historyBackfillPointCount} Punkte"
            "not_configured" -> "nicht eingerichtet"
            null -> "—"
            else -> diagnostics.historyBackfillStatus.orDash()
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp, 9.dp, 14.dp, 9.dp)
            minimumHeight = 54.dp
            setBackgroundResource(R.drawable.bg_connection_strip)

            addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (connected) green else secondary)
                }
            }, LinearLayout.LayoutParams(9.dp, 9.dp))

            val center = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10.dp, 0, 0, 0)
                addView(value(if (connected) "Watch verbunden" else "Keine Watch erreichbar", text, 14f, maxLines = 1))
                addView(helper(syncText(diagnostics.syncStatus), 1))
            }
            addView(center, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val right = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(TextView(context).apply {
                    text = "NIGHTSCOUT"
                    textSize = 9f
                    setTextColor(secondary)
                    gravity = Gravity.END
                    letterSpacing = 0.04f
                })
                addView(TextView(context).apply {
                    text = nightscoutText
                    textSize = 11f
                    setTextColor(if (diagnostics.historyBackfillStatus == "ok") accent else secondary)
                    gravity = Gravity.END
                    maxLines = 1
                })
            }
            addView(right)

            val battery = state.takeIf { current }?.device?.phoneBatteryPercent
            if (battery != null) {
                addView(TextView(context).apply {
                    text = "  $battery%"
                    textSize = 11f
                    setTextColor(secondary)
                    gravity = Gravity.CENTER_VERTICAL
                })
            }

            id = R.id.dashboard_sync_status
            isClickable = true
            isFocusable = true
            foreground = selectableForeground()
            setOnClickListener { callbacks.navigate(DashboardScreen.WATCH) }
        }
    }

    private fun infoCard(title: String, rows: List<Pair<String, String>>, action: Pair<String, () -> Unit>? = null): View = tile(title).apply {
        rows.forEach { (name, value) -> addView(infoRow(name, value)) }
        action?.let { (name, callback) -> addView(chip(name, true, callback), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END; topMargin = 10.dp }) }
    }

    private fun infoRow(name: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(0, 7.dp, 0, 7.dp)
        addView(TextView(context).apply { text = name; textSize = 13f; setTextColor(secondary) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.46f))
        addView(TextView(context).apply { text = value; textSize = 13f; setTextColor(this@DashboardViewFactory.text); gravity = Gravity.END; maxLines = 3 }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.54f))
    }

    private fun switchRow(title: String, description: String, checked: Boolean, id: Int, callback: (Boolean) -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 10.dp, 0, 4.dp)
        val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(value(title, text, 14f)); addView(helper(description, 3)) }
        addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(context).apply { this.id = id; isChecked = checked; buttonTintList = null; setOnCheckedChangeListener { _, value -> callback(value) } })
    }

    private fun chipRow(items: List<Triple<String, Boolean, () -> Unit>>) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START; setPadding(0, 7.dp, 0, 4.dp)
        items.forEach { (label, selected, click) -> addView(chip(label, selected, click), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 7.dp }) }
    }

    private fun chip(label: String, selected: Boolean, click: () -> Unit) = TextView(context).apply {
        text = label; textSize = 11f; minHeight = 36.dp; setTextColor(if (selected) accent else secondary); setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip); gravity = Gravity.CENTER; isClickable = true; isFocusable = true; setOnClickListener { click() }
    }

    private fun tile(title: String?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(12.dp, 11.dp, 12.dp, 11.dp); setBackgroundResource(R.drawable.bg_tile); clipToOutline = true
        title?.let { addView(sectionLabel(it)) }
    }

    private fun screenTitle(title: String, sub: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(4.dp, 4.dp, 4.dp, 8.dp); addView(value(title, text, 24f)); addView(helper(sub, 2))
    }
    private fun sectionLabel(value: String) = TextView(context).apply { text = value; textSize = 12f; setTextColor(this@DashboardViewFactory.text); typeface = Typeface.create("sans", Typeface.NORMAL); letterSpacing = 0.03f }
    private fun label(value: String, color: Int = secondary) = TextView(context).apply { text = value; textSize = 11f; setTextColor(color); letterSpacing = 0.04f }
    private fun value(value: String, color: Int, size: Float, maxLines: Int = 2) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); typeface = Typeface.create("sans", Typeface.NORMAL); this.maxLines = maxLines
        if (maxLines == 1) ellipsize = TextUtils.TruncateAt.END
    }
    private fun helper(value: String, maxLines: Int = 2, color: Int = secondary) = TextView(context).apply { text = value; textSize = 11f; setTextColor(color); this.maxLines = maxLines }

    private fun weightedTileParams(height: Int = 104, weight: Float = 1f) = LinearLayout.LayoutParams(0, height.dp, weight).apply { marginStart = 3.dp; marginEnd = 3.dp }
    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun cardParams(top: Int = 6, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top.dp; bottomMargin = bottom.dp }
    private fun selectableForeground(): Drawable? = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).let { array -> array.getDrawable(0).also { array.recycle() } }

    private fun glucose(valueMgDl: Double, unit: GlucoseUnit) = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.getDefault(), "%.1f", valueMgDl / 18.0) else valueMgDl.toInt().toString()
    private fun signedDelta(valueMgDl: Double?, unit: GlucoseUnit): String = valueMgDl?.let { value -> val converted = if (unit == GlucoseUnit.MMOL_L) value / 18.0 else value; val body = if (unit == GlucoseUnit.MMOL_L) String.format(Locale.getDefault(), "%.1f", converted) else converted.toInt().toString(); (if (converted >= 0) "+" else "") + body } ?: ""
    private fun unitLabel(unit: GlucoseUnit) = if (unit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
    private fun decimal(value: Double?, digits: Int) = value?.let { NumberFormat.getNumberInstance().apply { minimumFractionDigits = digits; maximumFractionDigits = digits }.format(it) } ?: "—"
    private fun units(value: Double?, suffix: String, digits: Int) = value?.let { decimal(it, digits) + suffix } ?: "—"
    private fun String?.orDash() = this?.takeIf { it.isNotBlank() } ?: "—"
    private fun Int?.percent() = this?.let { "$it%" } ?: "—"
    private fun Long?.asDateTime() = if (this == null || this <= 0L) "—" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
    private fun remaining(end: Long?, now: Long) = end?.takeIf { it > now }?.let { "${(it - now) / 60_000L} min" } ?: "—"
    private fun syncText(status: String?) = when (status) { "ok" -> "übertragen"; "pending" -> "wird übertragen"; "unavailable" -> "keine Uhr erreichbar"; "invalid_payload" -> "ungültige Nachricht"; else -> "noch nicht versucht" }
    private fun backfillText(diagnostics: DiagnosticsSnapshot) = when (diagnostics.historyBackfillStatus) {
        "ok" -> "${diagnostics.historyBackfillPointCount} Punkte · 24 h synchronisiert"
        "requested" -> "wird synchronisiert"
        else -> "noch nicht konfiguriert"
    }
    private val Int.dp get() = (this * density).toInt()
}
