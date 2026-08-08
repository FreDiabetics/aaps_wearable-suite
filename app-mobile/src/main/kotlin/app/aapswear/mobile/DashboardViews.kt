package app.aapswear.mobile

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import app.aapswear.mobile.ui.theme.SugarliciousColorRole
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.mobile.ui.theme.SugarliciousTheme
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState
import java.text.DateFormat
import java.util.Date

enum class DashboardScreen { OVERVIEW, WATCH, SETTINGS }
enum class DisplayUnitPreference { AAPS, MG_DL, MMOL_L }

data class DashboardUiPreferences(
    val unit: DisplayUnitPreference = DisplayUnitPreference.AAPS,
    val showDetails: Boolean = true,
    val showPredictions: Boolean = true,
    val showMetabolicGraph: Boolean = false,
    val compact: Boolean = true,
    val graphHours: Int = 3,
    val liveNotification: Boolean = false,
) {
    fun unitFor(state: TherapyDisplayState?): GlucoseUnit = when (unit) {
        DisplayUnitPreference.AAPS ->
            state?.glucose?.displayUnit ?: GlucoseUnit.MG_DL
        DisplayUnitPreference.MG_DL ->
            GlucoseUnit.MG_DL
        DisplayUnitPreference.MMOL_L ->
            GlucoseUnit.MMOL_L
    }

    companion object {
        fun read(preferences: SharedPreferences) =
            DashboardUiPreferences(
                unit = runCatching {
                    DisplayUnitPreference.valueOf(
                        preferences.getString("unit", "AAPS")!!,
                    )
                }.getOrDefault(DisplayUnitPreference.AAPS),
                showDetails =
                    preferences.getBoolean(
                        "showDetails",
                        true,
                    ),
                showPredictions =
                    preferences.getBoolean(
                        "showPredictions",
                        true,
                    ),
                showMetabolicGraph =
                    preferences.getBoolean(
                        "showMetabolicGraph",
                        false,
                    ),
                compact =
                    preferences.getBoolean(
                        "compact",
                        true,
                    ),
                graphHours =
                    preferences
                        .getInt("graphHours", 3)
                        .takeIf {
                            it in listOf(3, 6, 12, 24)
                        } ?: 3,
                liveNotification =
                    preferences.getBoolean(
                        PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION,
                        false,
                    ),
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
        fun read(preferences: SharedPreferences) =
            DiagnosticsSnapshot(
                sourceVersion =
                    preferences.getString(
                        "sourceVersion",
                        null,
                    ),
                sourceContract =
                    preferences.getString(
                        "contract",
                        null,
                    ),
                sourcePackage =
                    preferences.getString(
                        "sourcePackage",
                        null,
                    ),
                receivedAt =
                    preferences.getLong(
                        "received",
                        0L,
                    ),
                measuredAt =
                    preferences.getLong(
                        "measurement",
                        0L,
                    ),
                reachableWatches =
                    preferences.getInt(
                        "reachableWatches",
                        0,
                    ),
                lastSyncAt =
                    preferences.getLong(
                        "lastSyncAt",
                        0L,
                    ),
                syncStatus =
                    preferences.getString(
                        "lastSyncStatus",
                        null,
                    ),
                syncError =
                    preferences.getString(
                        "lastSyncError",
                        null,
                    ),
                historyBackfillStatus =
                    preferences.getString(
                        "historyBackfillStatus",
                        null,
                    ),
                historyBackfillPointCount =
                    preferences.getInt(
                        "historyBackfillPointCount",
                        0,
                    ),
                historyBackfillRequestedAt =
                    preferences.getLong(
                        "historyBackfillRequestedAt",
                        0L,
                    ),
                historyBackfillReceivedAt =
                    preferences.getLong(
                        "historyBackfillReceivedAt",
                        0L,
                    ),
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
    val setShowMetabolicGraph: (Boolean) -> Unit,
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
    private val density =
        context.resources.displayMetrics.density

    private val text: Int
        get() =
            SugarliciousColors.argb(
                SugarliciousColorRole.TEXT_PRIMARY,
            )

    private val secondary: Int
        get() =
            SugarliciousColors.argb(
                SugarliciousColorRole.TEXT_SECONDARY,
            )

    private val accent: Int
        get() =
            SugarliciousColors.argb(
                SugarliciousColorRole.PRIMARY,
            )

    private val green: Int
        get() =
            SugarliciousColors.argb(
                SugarliciousColorRole.GREEN,
            )

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
            DashboardScreen.OVERVIEW ->
                renderOverview(
                    parent,
                    state,
                    diagnostics,
                    preferences,
                    now,
                )

            DashboardScreen.WATCH ->
                renderWatch(
                    parent,
                    state,
                    diagnostics,
                    now,
                )

            DashboardScreen.SETTINGS ->
                renderSettings(
                    parent,
                    state,
                    diagnostics,
                    preferences,
                )
        }
    }

    private fun renderOverview(
        parent: LinearLayout,
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        preferences: DashboardUiPreferences,
        now: Long,
    ) {
        val composeView =
            androidx.compose.ui.platform.ComposeView(
                context,
            ).apply {
                setViewCompositionStrategy(
                    androidx.compose.ui.platform
                        .ViewCompositionStrategy
                        .DisposeOnDetachedFromWindow,
                )
                setContent {
                    SugarliciousTheme {
                        SugarliciousOverviewScreen(
                            state = state,
                            preferences = preferences,
                            now = now,
                            callbacks = callbacks,
                        )
                    }
                }
            }

        parent.addView(
            composeView,
            fullWidth(),
        )

        val freshness =
            FreshnessPolicy.classify(
                state?.glucose?.measuredAtEpochMs,
                now,
            )
        val current =
            freshness == Freshness.CURRENT ||
                freshness == Freshness.DELAYED

        parent.addView(
            connectionCard(
                state,
                diagnostics,
                current,
            ),
            cardParams(top = 8),
        )
    }

    private fun renderWatch(
        parent: LinearLayout,
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        now: Long,
    ) {
        val composeView =
            androidx.compose.ui.platform.ComposeView(
                context,
            ).apply {
                setViewCompositionStrategy(
                    androidx.compose.ui.platform
                        .ViewCompositionStrategy
                        .DisposeOnDetachedFromWindow,
                )
                setContent {
                    SugarliciousTheme {
                        SugarliciousWatchScreen(
                            state = state,
                            diagnostics = diagnostics,
                            now = now,
                            onSyncNow = callbacks.syncNow,
                        )
                    }
                }
            }

        parent.addView(
            composeView,
            fullWidth(),
        )
    }

    private fun renderSettings(
        parent: LinearLayout,
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        preferences: DashboardUiPreferences,
    ) {
        parent.addView(
            screenTitle(),
        )

        parent.addView(
            infoCard(
                "BRIDGE-STATUS",
                listOf(
                    "AndroidAPS" to
                        diagnostics.sourceVersion.orDash(),
                    "Datenvertrag" to
                        diagnostics.sourceContract.orDash(),
                    "Watch erreichbar" to
                        if (diagnostics.reachableWatches > 0) {
                            diagnostics.reachableWatches.toString()
                        } else {
                            "nein"
                        },
                    "Letzte Übertragung" to
                        diagnostics.lastSyncAt
                            .takeIf { it > 0L }
                            .asDateTime(),
                    "Sync-Status" to
                        syncText(diagnostics.syncStatus),
                ),
                action =
                    "JETZT AN WATCH SENDEN" to
                        callbacks.syncNow,
            ),
            cardParams(),
        )

        val display =
            tile("SMARTPHONE-VORSCHAU")

        display.addView(
            sectionLabel("Glukose-Einheit"),
        )
        display.addView(
            chipRow(
                listOf(
                    Triple(
                        "AAPS",
                        preferences.unit ==
                            DisplayUnitPreference.AAPS,
                    ) {
                        callbacks.setUnit(
                            DisplayUnitPreference.AAPS,
                        )
                    },
                    Triple(
                        "mg/dL",
                        preferences.unit ==
                            DisplayUnitPreference.MG_DL,
                    ) {
                        callbacks.setUnit(
                            DisplayUnitPreference.MG_DL,
                        )
                    },
                    Triple(
                        "mmol/L",
                        preferences.unit ==
                            DisplayUnitPreference.MMOL_L,
                    ) {
                        callbacks.setUnit(
                            DisplayUnitPreference.MMOL_L,
                        )
                    },
                ),
            ),
        )

        display.addView(
            helper(
                "Die Smartphone-App bleibt eine Vorschau der Wear-Bridge. AndroidAPS ist die Live-Datenquelle.",
            ),
        )

        display.addView(
            switchRow(
                "Therapiedetails anzeigen",
                "IOB, COB, Basal und Profil auf der Übersicht",
                preferences.showDetails,
                R.id.dashboard_details_switch,
                callbacks.setShowDetails,
            ),
        )

        display.addView(
            switchRow(
                "AAPS-Prognosen anzeigen",
                "Nur vorhandene predBGs; Sugarlicious berechnet keine Therapieprognose",
                preferences.showPredictions,
                R.id.dashboard_predictions_switch,
                callbacks.setShowPredictions,
            ),
        )

        display.addView(
            switchRow(
                "IOB/COB-Verlaufsgraph",
                "Zusätzlichen IOB- und COB-Verlauf auf der Übersicht anzeigen",
                preferences.showMetabolicGraph,
                View.generateViewId(),
                callbacks.setShowMetabolicGraph,
            ),
        )

        display.addView(
            switchRow(
                "Kompakte Übersicht",
                "Dichte Darstellung für die Bridge-Übersicht",
                preferences.compact,
                R.id.dashboard_compact_switch,
                callbacks.setCompact,
            ),
        )

        display.addView(
            switchRow(
                "Live-Benachrichtigung",
                "Optionaler Android-16-Live-Status; normale Benachrichtigung bleibt sonst aktiv",
                preferences.liveNotification,
                R.id.dashboard_live_notification_switch,
                callbacks.setLiveNotification,
            ),
        )

        display.addView(
            sectionLabel("Graph-Zeitraum"),
        )
        display.addView(
            chipRow(
                listOf(3, 6, 12, 24).map { hours ->
                    Triple(
                        "$hours h",
                        preferences.graphHours == hours,
                    ) {
                        callbacks.setGraphHours(hours)
                    }
                },
            ),
        )

        parent.addView(
            display,
            cardParams(),
        )

        val colorSettings =
            androidx.compose.ui.platform.ComposeView(
                context,
            ).apply {
                setViewCompositionStrategy(
                    androidx.compose.ui.platform
                        .ViewCompositionStrategy
                        .DisposeOnDetachedFromWindow,
                )
                setContent {
                    SugarliciousTheme {
                        SugarliciousColorSettingsPanel()
                    }
                }
            }

        parent.addView(
            colorSettings,
            cardParams(),
        )

        val nightscoutConfig =
            NightscoutConfigStore.load(context)

        val nightscout =
            tile("NIGHTSCOUT · HISTORISCHER BACKFILL")

        nightscout.addView(
            infoRow(
                "Konfiguration",
                nightscoutConfig?.baseUrl
                    ?: "nicht eingerichtet",
            ),
        )
        nightscout.addView(
            infoRow(
                "Access Token",
                if (
                    nightscoutConfig?.accessToken != null
                ) {
                    "verschlüsselt gespeichert"
                } else {
                    "nicht gesetzt"
                },
            ),
        )
        nightscout.addView(
            infoRow(
                "Status",
                backfillText(diagnostics),
            ),
        )
        nightscout.addView(
            infoRow(
                "Punkte im 24h-Puffer",
                diagnostics.historyBackfillPointCount
                    .toString(),
            ),
        )
        nightscout.addView(
            infoRow(
                "Letzter Backfill",
                diagnostics.historyBackfillReceivedAt
                    .takeIf { it > 0L }
                    .asDateTime(),
            ),
        )
        nightscout.addView(
            helper(
                "Nightscout dient nur für 24h-Historie und Gap-Reparatur. Live-Daten kommen weiterhin aus AndroidAPS.",
                3,
            ),
        )
        nightscout.addView(
            chipRow(
                listOf(
                    Triple(
                        if (nightscoutConfig == null) {
                            "EINRICHTEN"
                        } else {
                            "BEARBEITEN"
                        },
                        true,
                    ) {
                        callbacks.configureNightscout()
                    },
                    Triple(
                        "24H AKTUALISIEREN",
                        nightscoutConfig != null,
                    ) {
                        callbacks.syncNightscout()
                    },
                ),
            ),
        )

        parent.addView(
            nightscout,
            cardParams(),
        )

        parent.addView(
            infoCard(
                "DIAGNOSE",
                listOf(
                    "AAPS-Paket" to
                        diagnostics.sourcePackage.orDash(),
                    "AAPS-Version" to
                        diagnostics.sourceVersion.orDash(),
                    "Vertrag" to
                        diagnostics.sourceContract.orDash(),
                    "Schema" to
                        (
                            state?.schemaVersion
                                ?.toString()
                                ?: "—"
                            ),
                    "Fähigkeiten" to
                        (
                            state?.capabilities
                                ?.size
                                ?.toString()
                                ?: "0"
                            ),
                    "Letzter AAPS-Empfang" to
                        diagnostics.receivedAt
                            .takeIf { it > 0L }
                            .asDateTime(),
                    "Letzter Messwert" to
                        diagnostics.measuredAt
                            .takeIf { it > 0L }
                            .asDateTime(),
                    "Sync-Fehler" to
                        diagnostics.syncError.orDash(),
                ),
                action =
                    "DIAGNOSE KOPIEREN" to
                        callbacks.copyDiagnostics,
            ),
            cardParams(),
        )

        parent.addView(
            infoCard(
                "DATENSCHUTZ & SICHERHEIT",
                listOf(
                    "Betriebsart" to
                        "strikt read-only",
                    "Datenweg" to
                        "AndroidAPS → Sugarlicious → Wear OS",
                    "Nightscout" to
                        "nur lesend für Historie / Gap-Reparatur",
                    "Cloud / Telemetrie" to
                        "keine Sugarlicious-Telemetrie",
                    "Token" to
                        "Android Keystore · AES/GCM",
                ),
            ),
            cardParams(),
        )

        parent.addView(
            aboutCard(),
            cardParams(bottom = 10),
        )
    }

    private fun aboutCard(): View =
        tile("ÜBER SUGARLICIOUS").apply {
            val header =
                LinearLayout(context).apply {
                    orientation =
                        LinearLayout.HORIZONTAL
                    gravity =
                        Gravity.CENTER_VERTICAL
                    setPadding(
                        0,
                        8.dp,
                        0,
                        8.dp,
                    )
                }

            header.addView(
                ImageView(context).apply {
                    setImageResource(
                        R.drawable.frediabetics_logo,
                    )
                    contentDescription =
                        context.getString(
                            R.string.brand_logo,
                        )
                    scaleType =
                        ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(
                    58.dp,
                    58.dp,
                ),
            )

            header.addView(
                LinearLayout(context).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    setPadding(
                        12.dp,
                        0,
                        0,
                        0,
                    )
                    addView(
                        value(
                            "Sugarlicious",
                            text,
                            20f,
                            1,
                        ),
                    )
                    addView(
                        helper(
                            "Version 0.5.1 · FreDiabetics · Open Source",
                            2,
                            accent,
                        ),
                    )
                    addView(
                        helper(
                            "Lokale, strikt read-only AndroidAPS-Anzeige",
                            2,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            addView(header)

            addView(
                infoRow(
                    "Kontakt",
                    context.getString(
                        R.string.contact_email,
                    ),
                ),
            )
            addView(
                infoRow(
                    "GitHub",
                    "FreDiabetics/aaps_wearable-suite",
                ),
            )

            addView(
                chipRow(
                    listOf(
                        Triple(
                            "E-MAIL",
                            true,
                        ) {
                            callbacks.openContactEmail()
                        },
                        Triple(
                            "GITHUB",
                            true,
                        ) {
                            callbacks.openGithub()
                        },
                    ),
                ).also { row ->
                    row.getChildAt(0).id =
                        R.id.dashboard_contact_email
                    row.getChildAt(1).id =
                        R.id.dashboard_github
                },
            )
        }

    private fun connectionCard(
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        current: Boolean,
    ): View {
        val connected =
            diagnostics.reachableWatches > 0 &&
                diagnostics.syncStatus == "ok"

        val nightscoutText =
            when (
                diagnostics.historyBackfillStatus
            ) {
                "ok" ->
                    "${diagnostics.historyBackfillPointCount} Punkte"

                "not_configured" ->
                    "nicht eingerichtet"

                null ->
                    "—"

                else ->
                    diagnostics.historyBackfillStatus
                        .orDash()
            }

        return LinearLayout(context).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL
            setPadding(
                14.dp,
                9.dp,
                14.dp,
                9.dp,
            )
            minimumHeight =
                54.dp
            background =
                roundedBackground(
                    SugarliciousColors.argb(
                        SugarliciousColorRole.SURFACE,
                    ),
                    SugarliciousColors.argb(
                        SugarliciousColorRole.BORDER,
                    ),
                    18,
                )

            addView(
                View(context).apply {
                    background =
                        GradientDrawable().apply {
                            shape =
                                GradientDrawable.OVAL
                            setColor(
                                if (connected) {
                                    green
                                } else {
                                    secondary
                                },
                            )
                        }
                },
                LinearLayout.LayoutParams(
                    9.dp,
                    9.dp,
                ),
            )

            val center =
                LinearLayout(context).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    setPadding(
                        10.dp,
                        0,
                        0,
                        0,
                    )
                    addView(
                        value(
                            if (connected) {
                                "Watch verbunden"
                            } else {
                                "Keine Watch erreichbar"
                            },
                            text,
                            14f,
                            maxLines = 1,
                        ),
                    )
                    addView(
                        helper(
                            syncText(
                                diagnostics.syncStatus,
                            ),
                            1,
                        ),
                    )
                }

            addView(
                center,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            val right =
                LinearLayout(context).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    gravity =
                        Gravity.END

                    addView(
                        TextView(context).apply {
                            text =
                                context.getString(
                                    R.string.dashboard_nightscout_label,
                                )
                            textSize =
                                9f
                            setTextColor(
                                secondary,
                            )
                            gravity =
                                Gravity.END
                            letterSpacing =
                                0.04f
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text =
                                nightscoutText
                            textSize =
                                11f
                            setTextColor(
                                if (
                                    diagnostics.historyBackfillStatus ==
                                    "ok"
                                ) {
                                    accent
                                } else {
                                    secondary
                                },
                            )
                            gravity =
                                Gravity.END
                            maxLines =
                                1
                        },
                    )
                }

            addView(right)

            val battery =
                state
                    .takeIf { current }
                    ?.device
                    ?.phoneBatteryPercent

            if (battery != null) {
                addView(
                    TextView(context).apply {
                        text =
                            context.getString(
                                R.string.dashboard_phone_battery_inline,
                                battery,
                            )
                        textSize =
                            11f
                        setTextColor(
                            secondary,
                        )
                        gravity =
                            Gravity.CENTER_VERTICAL
                    },
                )
            }

            id =
                R.id.dashboard_sync_status
            isClickable =
                true
            isFocusable =
                true
            foreground =
                selectableForeground()
            setOnClickListener {
                callbacks.navigate(
                    DashboardScreen.WATCH,
                )
            }
        }
    }

    private fun infoCard(
        title: String,
        rows: List<Pair<String, String>>,
        action: Pair<String, () -> Unit>? = null,
    ): View =
        tile(title).apply {
            rows.forEach { (name, value) ->
                addView(
                    infoRow(
                        name,
                        value,
                    ),
                )
            }

            action?.let { (name, callback) ->
                addView(
                    chip(
                        name,
                        true,
                        callback,
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity =
                            Gravity.END
                        topMargin =
                            10.dp
                    },
                )
            }
        }

    private fun infoRow(
        name: String,
        value: String,
    ) =
        LinearLayout(context).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.TOP
            setPadding(
                0,
                7.dp,
                0,
                7.dp,
            )

            addView(
                TextView(context).apply {
                    text =
                        name
                    textSize =
                        13f
                    setTextColor(
                        secondary,
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.46f,
                ),
            )

            addView(
                TextView(context).apply {
                    text =
                        value
                    textSize =
                        13f
                    setTextColor(
                        this@DashboardViewFactory.text,
                    )
                    gravity =
                        Gravity.END
                    maxLines =
                        3
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.54f,
                ),
            )
        }

    private fun switchRow(
        title: String,
        description: String,
        checked: Boolean,
        id: Int,
        callback: (Boolean) -> Unit,
    ) =
        LinearLayout(context).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL
            setPadding(
                0,
                10.dp,
                0,
                4.dp,
            )

            val copy =
                LinearLayout(context).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    addView(
                        value(
                            title,
                            text,
                            14f,
                        ),
                    )
                    addView(
                        helper(
                            description,
                            3,
                        ),
                    )
                }

            addView(
                copy,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            addView(
                Switch(context).apply {
                    this.id =
                        id
                    isChecked =
                        checked
                    thumbTintList =
                        ColorStateList(
                            arrayOf(
                                intArrayOf(
                                    android.R.attr.state_checked,
                                ),
                                intArrayOf(),
                            ),
                            intArrayOf(
                                accent,
                                secondary,
                            ),
                        )
                    trackTintList =
                        ColorStateList(
                            arrayOf(
                                intArrayOf(
                                    android.R.attr.state_checked,
                                ),
                                intArrayOf(),
                            ),
                            intArrayOf(
                                SugarliciousColors.argb(
                                    SugarliciousColorRole.SURFACE_SELECTED,
                                ),
                                SugarliciousColors.argb(
                                    SugarliciousColorRole.SURFACE_HIGH,
                                ),
                            ),
                        )
                    setOnCheckedChangeListener { _, value ->
                        callback(value)
                    }
                },
            )
        }

    private fun chipRow(
        items:
            List<
                Triple<
                    String,
                    Boolean,
                    () -> Unit,
                >,
            >,
    ) =
        LinearLayout(context).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.START
            setPadding(
                0,
                7.dp,
                0,
                4.dp,
            )

            items.forEach { (label, selected, click) ->
addView(
                    chip(
                        label,
                        selected,
                        click,
                    ),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginEnd =
                            7.dp
                    },
                )
            }
        }

    private fun chip(
        label: String,
        selected: Boolean,
        click: () -> Unit,
    ) =
        TextView(context).apply {
            text =
                label
            textSize =
                11f
            minHeight =
                36.dp
            setTextColor(
                if (selected) {
                    accent
                } else {
                    secondary
                },
            )
            background =
                roundedBackground(
                    if (selected) {
                        SugarliciousColors.argb(
                            SugarliciousColorRole.SURFACE_SELECTED,
                        )
                    } else {
                        SugarliciousColors.argb(
                            SugarliciousColorRole.SURFACE_HIGH,
                        )
                    },
                    if (selected) {
                        accent
                    } else {
                        SugarliciousColors.argb(
                            SugarliciousColorRole.BORDER,
                        )
                    },
                    999,
                )
            gravity =
                Gravity.CENTER
            isClickable =
                true
            isFocusable =
                true
            setPadding(
                12.dp,
                0,
                12.dp,
                0,
            )
            setOnClickListener {
                click()
            }
        }

    private fun tile(
        title: String?,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                12.dp,
                11.dp,
                12.dp,
                11.dp,
            )
            background =
                roundedBackground(
                    SugarliciousColors.argb(
                        SugarliciousColorRole.SURFACE,
                    ),
                    SugarliciousColors.argb(
                        SugarliciousColorRole.BORDER,
                    ),
                    22,
                )
            clipToOutline =
                true

            title?.let {
                addView(
                    sectionLabel(it),
                )
            }
        }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape =
                GradientDrawable.RECTANGLE
            cornerRadius =
                radiusDp.dp.toFloat()
            setColor(
                fillColor,
            )
            setStroke(
                1.dp,
                strokeColor,
            )
        }

    private fun screenTitle() =
        LinearLayout(context).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                4.dp,
                4.dp,
                4.dp,
                8.dp,
            )
            addView(
                value(
                    "Einstellungen",
                    text,
                    24f,
                ),
            )
            addView(
                helper(
                    "Sugarlicious als AndroidAPS → Wear OS Bridge konfigurieren",
                    2,
                ),
            )
        }

    private fun sectionLabel(
        label: String,
    ) =
        TextView(context).apply {
            text =
                label
            textSize =
                12f
            setTextColor(
                this@DashboardViewFactory.text,
            )
            typeface =
                Typeface.create(
                    "sans",
                    Typeface.NORMAL,
                )
            letterSpacing =
                0.03f
        }

    private fun value(
        value: String,
        color: Int,
        size: Float,
        maxLines: Int = 2,
    ) =
        TextView(context).apply {
            text =
                value
            textSize =
                size
            setTextColor(
                color,
            )
            typeface =
                Typeface.create(
                    "sans",
                    Typeface.NORMAL,
                )
            this.maxLines =
                maxLines

            if (maxLines == 1) {
                ellipsize =
                    TextUtils.TruncateAt.END
            }
        }

    private fun helper(
        value: String,
        maxLines: Int = 2,
        color: Int = secondary,
    ) =
        TextView(context).apply {
            text =
                value
            textSize =
                11f
            setTextColor(
                color,
            )
            this.maxLines =
                maxLines
        }

    private fun fullWidth() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

    private fun cardParams(
        top: Int = 6,
        bottom: Int = 0,
    ) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin =
                top.dp
            bottomMargin =
                bottom.dp
        }

    private fun selectableForeground(): Drawable? =
        context
            .obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.selectableItemBackground,
                ),
            )
            .let { array ->
                array
                    .getDrawable(0)
                    .also {
                        array.recycle()
                    }
            }

    private fun String?.orDash() =
        this
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "—"

    private fun Long?.asDateTime() =
        if (
            this == null ||
            this <= 0L
        ) {
            "—"
        } else {
            DateFormat
                .getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT,
                )
                .format(
                    Date(this),
                )
        }

    private fun syncText(
        status: String?,
    ) =
        when (status) {
            "ok" ->
                "übertragen"
            "pending" ->
                "wird übertragen"
            "unavailable" ->
                "keine Uhr erreichbar"
            "invalid_payload" ->
                "ungültige Nachricht"
            else ->
                "noch nicht versucht"
        }

    private fun backfillText(
        diagnostics: DiagnosticsSnapshot,
    ) =
        when (
            diagnostics.historyBackfillStatus
        ) {
            "ok" ->
                "${diagnostics.historyBackfillPointCount} Punkte · 24 h synchronisiert"

            "requested" ->
                "wird synchronisiert"

            "not_configured" ->
                "nicht eingerichtet"

            "error" ->
                "Fehler"

            else ->
                "noch nicht konfiguriert"
        }

    private val Int.dp: Int
        get() =
            (this * density).toInt()
}
