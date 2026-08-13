package app.aapswear.mobile

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
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
import app.aapswear.model.GlucoseUnit
import app.aapswear.model.TherapyDisplayState

enum class DashboardScreen { OVERVIEW, WATCH, SETTINGS }
enum class DisplayUnitPreference { AAPS, MG_DL, MMOL_L }
enum class DashboardThemeMode { DARK, LIGHT }

data class DashboardUiPreferences(
    val unit: DisplayUnitPreference = DisplayUnitPreference.AAPS,
    val showDetails: Boolean = true,
    val showCgmGraph: Boolean = true,
    val showCgmTargetRange: Boolean = false,
    val showCgmBasal: Boolean = false,
    val showCgmActivity: Boolean = false,
    val showCgmPredictionIob: Boolean = false,
    val showCgmPredictionCob: Boolean = false,
    val showCgmPredictionUam: Boolean = false,
    val showCgmPredictionZeroTemp: Boolean = false,
    val showMetabolicGraph: Boolean = false,
    val cgmDotRadiusDp: Float = 2.4f,
    val cgmDotOutlineEnabled: Boolean = true,
    val cgmDotOutlineWidthDp: Float = 0.95f,
    val compact: Boolean = true,
    val graphHours: Int = 3,
    val liveNotification: Boolean = false,
    val watchFaceIndex: Int = 1,
    val dataSource: DataSourcePreference = DataSourcePreference.AUTOMATIC,
    val themeMode: DashboardThemeMode = DashboardThemeMode.DARK,
) {
    val anyCgmPredictionEnabled: Boolean
        get() =
            showCgmPredictionIob ||
                showCgmPredictionCob ||
                showCgmPredictionUam ||
                showCgmPredictionZeroTemp

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
                showCgmGraph =
                    preferences.getBoolean(
                        "showCgmGraph",
                        true,
                    ),
                showCgmTargetRange =
                    preferences.getBoolean(
                        "cgm.targetRange",
                        false,
                    ),
                showCgmBasal =
                    preferences.getBoolean(
                        "cgm.basal",
                        false,
                    ),
                showCgmActivity =
                    preferences.getBoolean(
                        "cgm.activity",
                        false,
                    ),
                showCgmPredictionIob =
                    preferences.getBoolean(
                        "cgm.prediction.iob",
                        false,
                    ),
                showCgmPredictionCob =
                    preferences.getBoolean(
                        "cgm.prediction.cob",
                        false,
                    ),
                showCgmPredictionUam =
                    preferences.getBoolean(
                        "cgm.prediction.uam",
                        false,
                    ),
                showCgmPredictionZeroTemp =
                    preferences.getBoolean(
                        "cgm.prediction.zeroTemp",
                        false,
                    ),
                showMetabolicGraph =
                    preferences.getBoolean(
                        "showMetabolicGraph",
                        false,
                    ),
                cgmDotRadiusDp =
                    preferences
                        .getFloat("cgm.dotRadiusDp", 2.4f)
                        .coerceIn(1.5f, 6.0f),
                cgmDotOutlineEnabled =
                    preferences.getBoolean(
                        "cgm.dotOutlineEnabled",
                        true,
                    ),
                cgmDotOutlineWidthDp =
                    preferences
                        .getFloat("cgm.dotOutlineWidthDp", 0.95f)
                        .coerceIn(0.25f, 3.0f),
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
                watchFaceIndex =
                    preferences
                        .getInt(
                            "watchFaceIndex",
                            1,
                        )
                        .coerceIn(0, 3),
                dataSource = runCatching {
                    DataSourcePreference.valueOf(
                        preferences.getString("dataSource", "AUTOMATIC")!!,
                    )
                }.getOrDefault(DataSourcePreference.AUTOMATIC),
                themeMode = runCatching {
                    DashboardThemeMode.valueOf(preferences.getString("themeMode", "DARK")!!)
                }.getOrDefault(DashboardThemeMode.DARK),
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
            )
    }
}

data class DashboardCallbacks(
    val navigate: (DashboardScreen) -> Unit,
    val setUnit: (DisplayUnitPreference) -> Unit,
    val setDataSource: (DataSourcePreference) -> Unit,
    val setThemeMode: (DashboardThemeMode) -> Unit,
    val setShowDetails: (Boolean) -> Unit,
    val setShowCgmGraph: (Boolean) -> Unit,
    val setCgmStream: (String, Boolean) -> Unit = { _, _ -> },
    val setShowMetabolicGraph: (Boolean) -> Unit,
    val setCompact: (Boolean) -> Unit,
    val setLiveNotification: (Boolean) -> Unit,
    val setWatchFaceIndex: (Int) -> Unit,
    val syncNow: () -> Unit,
    val openContactEmail: () -> Unit,
)

class DashboardViewFactory(
    private val context: Context,
    private val callbacks: DashboardCallbacks,
) {
    private enum class SettingsPanel {
        COLORS,
        CGM_PREDICTIONS,
    }

    private val expandedSettingsPanels = mutableSetOf<SettingsPanel>()

    private fun settingsPanelVisibility(panel: SettingsPanel): Int =
        if (panel in expandedSettingsPanels) View.VISIBLE else View.GONE

    private fun toggleSettingsPanel(panel: SettingsPanel): Int {
        if (!expandedSettingsPanels.add(panel)) expandedSettingsPanels.remove(panel)
        return settingsPanelVisibility(panel)
    }

    private data class ComposeRenderState(
        val state: TherapyDisplayState?,
        val diagnostics: DiagnosticsSnapshot,
        val preferences: DashboardUiPreferences,
        val now: Long,
    )

    private val overviewRenderState =
        androidx.compose.runtime.mutableStateOf<ComposeRenderState?>(null)
    private val watchRenderState =
        androidx.compose.runtime.mutableStateOf<ComposeRenderState?>(null)
    private var activeComposeScreen: DashboardScreen? = null
    private var activeComposeView: androidx.compose.ui.platform.ComposeView? = null

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

    fun render(
        parent: LinearLayout,
        screen: DashboardScreen,
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        preferences: DashboardUiPreferences,
        now: Long,
    ) {
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
                    preferences,
                    now,
                )

            DashboardScreen.SETTINGS ->
                renderSettings(
                    parent,
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
        overviewRenderState.value = ComposeRenderState(state, diagnostics, preferences, now)
        if (activeComposeScreen == DashboardScreen.OVERVIEW &&
            activeComposeView?.parent === parent
        ) {
            return
        }

        parent.removeAllViews()
        watchRenderState.value = null
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
                        overviewRenderState.value?.let { rendered ->
                            SugarliciousOverviewScreen(
                                state = rendered.state,
                                diagnostics = rendered.diagnostics,
                                preferences = rendered.preferences,
                                now = rendered.now,
                                callbacks = callbacks,
                            )
                        }
                    }
                }
            }

        parent.addView(
            composeView,
            fullWidth(),
        )
        activeComposeScreen = DashboardScreen.OVERVIEW
        activeComposeView = composeView
    }

    private fun renderWatch(
        parent: LinearLayout,
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        preferences: DashboardUiPreferences,
        now: Long,
    ) {
        watchRenderState.value = ComposeRenderState(state, diagnostics, preferences, now)
        if (activeComposeScreen == DashboardScreen.WATCH &&
            activeComposeView?.parent === parent
        ) {
            return
        }

        parent.removeAllViews()
        overviewRenderState.value = null
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
                        watchRenderState.value?.let { rendered ->
                            SugarliciousWatchScreen(
                                state = rendered.state,
                                preferences = rendered.preferences,
                                onSelectedFace = callbacks.setWatchFaceIndex,
                                onNavigate = callbacks.navigate,
                            )
                        }
                    }
                }
            }

        parent.addView(
            composeView,
            fullWidth(),
        )
        activeComposeScreen = DashboardScreen.WATCH
        activeComposeView = composeView
    }

    private fun renderSettings(
        parent: LinearLayout,
        preferences: DashboardUiPreferences,
    ) {
        parent.removeAllViews()
        overviewRenderState.value = null
        watchRenderState.value = null
        activeComposeScreen = null
        activeComposeView = null
        parent.addView(screenTitle())

        parent.addView(settingsGroupLabel("VERBINDUNG & DATEN"), fullWidth())
        parent.addView(
            tile(null).apply {
                addView(
                    choiceRow(
                        "Datenquelle",
                        listOf(
                            Triple("Automatisch", preferences.dataSource == DataSourcePreference.AUTOMATIC) {
                                callbacks.setDataSource(DataSourcePreference.AUTOMATIC)
                            },
                            Triple("Loop-App", preferences.dataSource == DataSourcePreference.ANDROID_APS) {
                                callbacks.setDataSource(DataSourcePreference.ANDROID_APS)
                            },
                            Triple("xDrip+", preferences.dataSource == DataSourcePreference.XDRIP_PLUS) {
                                callbacks.setDataSource(DataSourcePreference.XDRIP_PLUS)
                            },
                        ),
                    ),
                )
                addView(divider())
                addView(
                    choiceRow(
                        "Glukose-Einheit",
                        listOf(
                            Triple("Wie Datenquelle", preferences.unit == DisplayUnitPreference.AAPS) {
                                callbacks.setUnit(DisplayUnitPreference.AAPS)
                            },
                            Triple("mg/dL", preferences.unit == DisplayUnitPreference.MG_DL) {
                                callbacks.setUnit(DisplayUnitPreference.MG_DL)
                            },
                            Triple("mmol/L", preferences.unit == DisplayUnitPreference.MMOL_L) {
                                callbacks.setUnit(DisplayUnitPreference.MMOL_L)
                            },
                        ),
                    ),
                )
                addView(divider())
                addView(actionRow("Jetzt synchronisieren", "Jetzt") { callbacks.syncNow() })
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("ANZEIGE"), fullWidth())
        val colorContainer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = settingsPanelVisibility(SettingsPanel.COLORS)
                val colorSettings =
                    androidx.compose.ui.platform.ComposeView(context).apply {
                        setViewCompositionStrategy(
                            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow,
                        )
                        setContent {
                            SugarliciousTheme {
                                SugarliciousColorSettingsPanel(
                                    showCgmGraph = preferences.showCgmGraph,
                                    showMetabolicGraph = preferences.showMetabolicGraph,
                                )
                            }
                        }
                    }
                addView(colorSettings, fullWidth())
            }

        parent.addView(
            tile(null).apply {
                addView(
                    switchRowCompact(
                        "Heller Modus",
                        preferences.themeMode == DashboardThemeMode.LIGHT,
                        View.generateViewId(),
                    ) { callbacks.setThemeMode(if (it) DashboardThemeMode.LIGHT else DashboardThemeMode.DARK) },
                )
                addView(divider())
                addView(
                    switchRowCompact(
                        "Therapiedetails",
                        preferences.showDetails,
                        R.id.dashboard_details_switch,
                        callbacks.setShowDetails,
                    ),
                )
                addView(divider())
                addView(
                    switchRowCompact(
                        "Kompakte \u00dcbersicht",
                        preferences.compact,
                        R.id.dashboard_compact_switch,
                        callbacks.setCompact,
                    ),
                )
                addView(divider())
                addView(
                    actionRow("Farben & Darstellung", "Anpassen") {
                        colorContainer.visibility =
                            toggleSettingsPanel(SettingsPanel.COLORS)
                    },
                )
            },
            cardParams(top = 4),
        )
        parent.addView(colorContainer, cardParams(top = 4))

        parent.addView(settingsGroupLabel("CGM-GRAPH"), fullWidth())
        val predictionContainer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = settingsPanelVisibility(SettingsPanel.CGM_PREDICTIONS)
                addView(
                    tile(null).apply {
                        addView(
                            switchRowCompact(
                                "IOB-Prognose",
                                preferences.showCgmPredictionIob,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream("cgm.prediction.iob", it) },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "COB-Prognose",
                                preferences.showCgmPredictionCob,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream("cgm.prediction.cob", it) },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "UAM-Prognose",
                                preferences.showCgmPredictionUam,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream("cgm.prediction.uam", it) },
                        )
                        addView(divider())
                        addView(
                            switchRowCompact(
                                "ZeroTemp-Prognose",
                                preferences.showCgmPredictionZeroTemp,
                                View.generateViewId(),
                            ) { callbacks.setCgmStream("cgm.prediction.zeroTemp", it) },
                        )
                    },
                    fullWidth(),
                )
            }

        parent.addView(
            tile(null).apply {
                addView(
                    switchRowCompact(
                        "Graph anzeigen",
                        preferences.showCgmGraph,
                        View.generateViewId(),
                        callbacks.setShowCgmGraph,
                    ),
                )

                if (preferences.showCgmGraph) {
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Zielbereich",
                            preferences.showCgmTargetRange,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetRange", it) },
                    )
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Basal",
                            preferences.showCgmBasal,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.basal", it) },
                    )
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Insulinaktivit\u00e4t",
                            preferences.showCgmActivity,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.activity", it) },
                    )
                    addView(divider())
                    addView(
                        actionRow(
                            "Vorhersagen",
                            if (preferences.anyCgmPredictionEnabled) "Aktiv" else "Aus",
                        ) {
                            predictionContainer.visibility =
                                toggleSettingsPanel(SettingsPanel.CGM_PREDICTIONS)
                        },
                    )
                }
            },
            cardParams(top = 4),
        )
        parent.addView(predictionContainer, cardParams(top = 4))

        parent.addView(settingsGroupLabel("VERLAUF"), fullWidth())
        parent.addView(
            tile(null).apply {
                addView(
                    switchRowCompact(
                        "IOB/COB-Graph",
                        preferences.showMetabolicGraph,
                        View.generateViewId(),
                        callbacks.setShowMetabolicGraph,
                    ),
                )
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("BENACHRICHTIGUNG"), fullWidth())
        parent.addView(
            tile(null).apply {
                addView(
                    switchRowCompact(
                        "Live-Benachrichtigung",
                        preferences.liveNotification,
                        R.id.dashboard_live_notification_switch,
                        callbacks.setLiveNotification,
                    ),
                )
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("UHR & WATCHFACES"), fullWidth())
        parent.addView(
            tile(null).apply {
                addView(
                    actionRow("Watchfaces", "Ausw\u00e4hlen") {
                        callbacks.navigate(DashboardScreen.WATCH)
                    },
                )
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("INFO & SUPPORT"), fullWidth())
        parent.addView(aboutCard(), cardParams(top = 4, bottom = 10))
    }
    private fun aboutCard(): View =
        tile(null).apply {
            val header =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, 8.dp, 0, 8.dp)
                }

            header.addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.frediabetics_logo)
                    contentDescription = context.getString(R.string.brand_logo)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(56.dp, 56.dp),
            )

            header.addView(value("Sugarlicious", text, 20f, 1).apply { gravity = Gravity.CENTER })
            header.addView(helper("Unabhängiges Projekt · by FreDiabetics", 1).apply { gravity = Gravity.CENTER })
            addView(header)
            addView(divider())
            addView(
                actionRow("E-Mail", null) {
                    callbacks.openContactEmail()
                }.also {
                    it.id = R.id.dashboard_contact_email
                },
            )
        }

    private fun settingsGroupLabel(label: String) =
        TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(accent)
            applyChromaticOutline(accent)
            typeface = Typeface.create("sans", Typeface.BOLD)
            letterSpacing = 0.04f
            setPadding(4.dp, 12.dp, 4.dp, 3.dp)
        }

    private fun divider() =
        View(context).apply {
            setBackgroundColor(
                SugarliciousColors.argb(
                    SugarliciousColorRole.BORDER,
                ),
            )
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.dp,
                )
        }

    private fun choiceRow(
        title: String,
        items: List<Triple<String, Boolean, () -> Unit>>,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 5.dp, 0, 5.dp)
            addView(value(title, text, 14f, 1))
            addView(chipRow(items))
        }

    private fun actionRow(
        title: String,
        valueText: String?,
        action: () -> Unit,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 52.dp
            setPadding(0, 5.dp, 0, 5.dp)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }

            addView(
                value(title, text, 15f, 1),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            valueText?.let {
                addView(
                    TextView(context).apply {
                        text = it
                        textSize = 13f
                        setTextColor(secondary)
                        gravity = Gravity.END
                        setPadding(8.dp, 0, 8.dp, 0)
                    },
                )
            }

            addView(
                TextView(context).apply {
                    text = "\u203a"
                    textSize = 21f
                    setTextColor(secondary)
                },
            )
        }

    private fun switchRowCompact(
        title: String,
        checked: Boolean,
        id: Int,
        callback: (Boolean) -> Unit,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 52.dp
            setPadding(0, 5.dp, 0, 5.dp)

            addView(
                value(title, text, 15f, 1),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            addView(
                Switch(context).apply {
                    this.id = id
                    isChecked = checked
                    thumbTintList =
                        ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf(),
                            ),
                            intArrayOf(accent, secondary),
                        )
                    trackTintList =
                        ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_checked),
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
            val chipTextColor =
                if (selected) {
                    accent
                } else {
                    secondary
                }
            setTextColor(chipTextColor)
            applyChromaticOutline(chipTextColor)
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
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp, 4.dp, 4.dp, 10.dp)
            addView(
                value(
                    "Einstellungen",
                    text,
                    26f,
                    1,
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
            applyChromaticOutline(color)
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
            applyChromaticOutline(color)
            this.maxLines =
                maxLines
        }

    private fun TextView.applyChromaticOutline(color: Int) {
        if (SugarliciousColors.shouldOutlineChromatic(color)) {
            setShadowLayer(
                0.65f * density,
                0f,
                0f,
                SugarliciousColors.ChromaticOutlineArgb,
            )
        } else {
            setShadowLayer(0f, 0f, 0f, 0)
        }
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

    private val Int.dp: Int
        get() =
            (this * density).toInt()
}
