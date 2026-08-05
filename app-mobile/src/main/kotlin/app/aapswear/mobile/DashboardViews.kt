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
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayFormatter
import app.aapswear.model.TherapyDisplayState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

enum class DashboardScreen { OVERVIEW, HISTORY, DATA, SETTINGS }
enum class DisplayUnitPreference { AAPS, MG_DL, MMOL_L }

data class DashboardUiPreferences(
    val unit: DisplayUnitPreference = DisplayUnitPreference.AAPS,
    val showDetails: Boolean = true,
    val showPredictions: Boolean = true,
    val compact: Boolean = true,
    val graphHours: Int = 6,
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
    val syncNow: () -> Unit,
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
            DashboardScreen.HISTORY -> renderHistory(parent, state, preferences)
            DashboardScreen.DATA -> renderData(parent, state, diagnostics, preferences, now)
            DashboardScreen.SETTINGS -> renderSettings(parent, state, diagnostics, preferences)
        }
    }

    private fun renderOverview(parent: LinearLayout, state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, prefs: DashboardUiPreferences, now: Long) {
        val unit = prefs.unitFor(state)
        val glucose = state?.glucose
        val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, now)
        val current = freshness == Freshness.CURRENT || freshness == Freshness.DELAYED
        val glucoseText = if (glucose != null && current) glucose(glucose.valueMgDl, unit) else "—"
        val delta = if (glucose != null && current) signedDelta(glucose.deltaMgDl, unit).ifBlank { "—" } else "—"
        val age = glucose?.measuredAtEpochMs?.let { "${((now - it).coerceAtLeast(0L) / 60_000L)} min" } ?: "—"
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        top.addView(summaryTile("GLUKOSE", glucoseText, unitLabel(unit), green, R.id.dashboard_glucose, callbacks.cycleUnit), weightedTileParams())
        val deltaWithUnit = if (delta == "—") delta else "$delta ${unitLabel(unit)}"
        top.addView(summaryTile("TREND", if (current) TherapyDisplayFormatter.trendArrow(glucose?.trend ?: app.aapswear.model.Trend.UNKNOWN).ifBlank { "—" } else "—", "$deltaWithUnit\n$age", text), weightedTileParams())
        top.addView(summaryTile("ZIEL", if (current) TherapyDisplayFormatter.target(state?.target, unit) else "—", unitLabel(unit), text, valueSize = 17f), weightedTileParams())
        val loopSub = when (state?.loop?.status) { "enacted" -> "Ausgeführt"; "suggested" -> "Vorschlag"; else -> "Nicht verfügbar" }
        top.addView(summaryTile("STATUS", "Loop", loopSub, text, R.id.dashboard_source_status, subColor = if (state?.loop != null && current) green else secondary), weightedTileParams())
        parent.addView(top, fullWidth())

        parent.addView(glucoseGraphCard(state, prefs, compact = prefs.compact), cardParams())
        parent.addView(metabolicGraphCard(state, prefs, compact = prefs.compact), cardParams())

        if (prefs.showDetails) {
            val stats = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            stats.addView(statTile("IOB", decimal(state.takeIf { current }?.insulin?.totalIob, 2), "IE", blue), weightedTileParams(84))
            stats.addView(statTile("COB", decimal(state.takeIf { current }?.carbs?.cobGrams, 0), "g", orange), weightedTileParams(84))
            stats.addView(statTile("BASAL", decimal(state.takeIf { current }?.basal?.currentUnitsPerHour, 2), "IE/h", cyan), weightedTileParams(84))
            stats.addView(statTile("PROFIL", state.takeIf { current }?.profile?.name ?: "—", "Aktuell", purple), weightedTileParams(84))
            parent.addView(stats, cardParams(top = if (prefs.compact) 4 else 8))
        }
        parent.addView(connectionCard(state, diagnostics, current), cardParams())
    }

    private fun renderHistory(parent: LinearLayout, state: TherapyDisplayState?, prefs: DashboardUiPreferences) {
        parent.addView(screenTitle("Verlauf", "Echte, lokal empfangene AAPS-Anzeigedaten"))
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
        parent.addView(screenTitle("Einstellungen", "Direkt hier ändern – ohne Untermenüs"))
        val display = tile("ANZEIGE")
        display.addView(sectionLabel("Vorschau-Einheit"))
        display.addView(chipRow(listOf(
            Triple("AAPS", prefs.unit == DisplayUnitPreference.AAPS) { callbacks.setUnit(DisplayUnitPreference.AAPS) },
            Triple("mg/dL", prefs.unit == DisplayUnitPreference.MG_DL) { callbacks.setUnit(DisplayUnitPreference.MG_DL) },
            Triple("mmol/L", prefs.unit == DisplayUnitPreference.MMOL_L) { callbacks.setUnit(DisplayUnitPreference.MMOL_L) },
        )))
        display.addView(helper("Wirkt auf die Smartphone-Vorschau; AAPS bleibt die Datenquelle."))
        display.addView(switchRow("Therapiedetails anzeigen", "IOB, COB, Basal und Profil auf der Übersicht", prefs.showDetails, R.id.dashboard_details_switch, callbacks.setShowDetails))
        display.addView(switchRow("Prognosen im Graph", "Nur vorhandene AAPS-predBGs, keine eigene Berechnung", prefs.showPredictions, R.id.dashboard_predictions_switch, callbacks.setShowPredictions))
        display.addView(switchRow("Kompakte Übersicht", "Kleinere Graph-Tiles und Abstände", prefs.compact, R.id.dashboard_compact_switch, callbacks.setCompact))
        parent.addView(display, cardParams())

        val graph = tile("GRAPH-ZEITRAUM")
        graph.addView(chipRow(listOf(6, 12, 24).map { hours -> Triple("$hours h", prefs.graphHours == hours) { callbacks.setGraphHours(hours) } }))
        graph.addView(helper("Der lokale Anzeigepuffer ist auf 24 Stunden und 300 Punkte begrenzt."))
        parent.addView(graph, cardParams())

        parent.addView(infoCard("VERBINDUNG", listOf(
            "AndroidAPS" to diagnostics.sourceVersion.orDash(),
            "Datenvertrag" to diagnostics.sourceContract.orDash(),
            "Uhren erreichbar" to diagnostics.reachableWatches.toString(),
            "Synchronisation" to syncText(diagnostics.syncStatus),
        ), action = "JETZT SYNCHRONISIEREN" to callbacks.syncNow), cardParams())

        parent.addView(infoCard("DATENSCHUTZ & SICHERHEIT", listOf(
            "Betriebsart" to "strikt read-only",
            "Übertragung" to "lokal über Wear Data Layer",
            "Internet" to "keine Berechtigung",
            "Cloud / Telemetrie" to "nicht vorhanden",
            "Datenhaltung" to "letzter Zustand + begrenzter Graphpuffer",
        )), cardParams())
        parent.addView(infoCard("UMFANG", listOf(
            "Complication-Provider" to "27",
            "WFF-Pakete" to "23",
            "AAPS-Quelle" to (state?.sourceVersion ?: diagnostics.sourceVersion).orDash(),
        )), cardParams())
    }

    private fun glucoseGraphCard(state: TherapyDisplayState?, prefs: DashboardUiPreferences, compact: Boolean, large: Boolean = false): View {
        val card = tile(null)
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(sectionLabel("GLUKOSEVERLAUF"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(chip("${prefs.graphHours} h", true, callbacks.cycleGraphHours))
        card.addView(header)
        card.addView(legendRow())
        card.addView(GlucoseDashboardChart(context).apply {
            bind(state, prefs.unitFor(state), prefs.showPredictions, prefs.graphHours)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (if (large) 370 else if (compact) 160 else 220).dp))
        return card
    }

    private fun metabolicGraphCard(state: TherapyDisplayState?, prefs: DashboardUiPreferences, compact: Boolean, large: Boolean = false): View {
        val card = tile(null)
        card.addView(sectionLabel("INSULIN & KOHLENHYDRATE"))
        card.addView(MetabolicDashboardChart(context).apply { bind(state, prefs.graphHours) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (if (large) 390 else if (compact) 175 else 245).dp))
        return card
    }

    private fun legendRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(legend("●", "CGM", green)); addView(legend("●", "Prognose", context.getColor(R.color.app_yellow))); addView(legend("■", "Zielbereich", Color.rgb(16, 93, 40)))
    }

    private fun legend(symbol: String, label: String, color: Int) = TextView(context).apply {
        text = "$symbol  $label"; textSize = 10f; setTextColor(color); setPadding(0, 3.dp, 14.dp, 4.dp)
    }

    private fun summaryTile(title: String, value: String, sub: String, valueColor: Int, id: Int = View.NO_ID, click: (() -> Unit)? = null, valueSize: Float = 26f, subColor: Int = secondary) = tile(null).apply {
        if (id != View.NO_ID) this.id = id
        minimumHeight = 104.dp
        addView(label(title)); addView(value(value, valueColor, valueSize, maxLines = 1)); addView(helper(sub, maxLines = 2, color = subColor))
        if (click != null) { isClickable = true; isFocusable = true; foreground = selectableForeground(); setOnClickListener { click() } }
    }

    private fun statTile(title: String, value: String, suffix: String, color: Int) = tile(null).apply {
        minimumHeight = 84.dp; addView(label(title, color)); addView(value(value, text, 19f, maxLines = 1)); addView(helper(suffix, 1))
    }

    private fun connectionCard(state: TherapyDisplayState?, diagnostics: DiagnosticsSnapshot, current: Boolean): View {
        val card = tile(null).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        card.addView(ImageView(context).apply { setImageResource(R.drawable.ic_watch); contentDescription = "Smartwatch" }, LinearLayout.LayoutParams(42.dp, 42.dp))
        val center = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(10.dp, 0, 0, 0) }
        center.addView(label("VERBINDUNG"))
        val connected = diagnostics.reachableWatches > 0 && diagnostics.syncStatus == "ok"
        center.addView(value(if (connected) "●  Watch verbunden" else "○  Keine Watch erreichbar", if (connected) green else secondary, 15f))
        card.addView(center, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val battery = state.takeIf { current }?.device?.phoneBatteryPercent
        card.addView(helper(battery?.let { "Telefon: $it%" } ?: syncText(diagnostics.syncStatus), 1))
        card.addView(ImageView(context).apply { setImageResource(R.drawable.ic_chevron); contentDescription = "Verbindungsdetails" }, LinearLayout.LayoutParams(28.dp, 28.dp))
        card.id = R.id.dashboard_sync_status
        card.isClickable = true; card.isFocusable = true; card.foreground = selectableForeground(); card.setOnClickListener { callbacks.navigate(DashboardScreen.DATA) }
        return card
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
        text = label; textSize = 11f; setTextColor(if (selected) cyan else secondary); setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip); gravity = Gravity.CENTER; isClickable = true; isFocusable = true; setOnClickListener { click() }
    }

    private fun tile(title: String?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(12.dp, 11.dp, 12.dp, 11.dp); setBackgroundResource(R.drawable.bg_tile)
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

    private fun weightedTileParams(height: Int = 104) = LinearLayout.LayoutParams(0, height.dp, 1f).apply { marginStart = 3.dp; marginEnd = 3.dp }
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
    private val Int.dp get() = (this * density).toInt()
}
