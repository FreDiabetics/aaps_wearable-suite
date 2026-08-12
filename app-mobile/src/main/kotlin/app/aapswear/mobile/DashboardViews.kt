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
enum class DashboardThemeMode { DARK, LIGHT }

data class DashboardUiPreferences(
    val unit: DisplayUnitPreference = DisplayUnitPreference.AAPS,
    val showDetails: Boolean = true,
    val showPredictions: Boolean = true,
    val showCgmTargetRange: Boolean = true,
    val showCgmBasal: Boolean = true,
    val showCgmActivity: Boolean = true,
    val showCgmPredictionIob: Boolean = true,
    val showCgmPredictionCob: Boolean = true,
    val showCgmPredictionUam: Boolean = true,
    val showCgmPredictionZeroTemp: Boolean = true,
    val showMetabolicGraph: Boolean = false,
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
                showPredictions =
                    preferences.getBoolean(
                        "showPredictions",
                        true,
                    ),
                showCgmTargetRange =
                    preferences.getBoolean(
                        "cgm.targetRange",
                        true,
                    ),
                showCgmBasal =
                    preferences.getBoolean(
                        "cgm.basal",
                        true,
                    ),
                showCgmActivity =
                    preferences.getBoolean(
                        "cgm.activity",
                        true,
                    ),
                showCgmPredictionIob =
                    preferences.getBoolean(
                        "cgm.prediction.iob",
                        preferences.getBoolean(
                            "showPredictions",
                            true,
                        ),
                    ),
                showCgmPredictionCob =
                    preferences.getBoolean(
                        "cgm.prediction.cob",
                        preferences.getBoolean(
                            "showPredictions",
                            true,
                        ),
                    ),
                showCgmPredictionUam =
                    preferences.getBoolean(
                        "cgm.prediction.uam",
                        preferences.getBoolean(
                            "showPredictions",
                            true,
                        ),
                    ),
                showCgmPredictionZeroTemp =
                    preferences.getBoolean(
                        "cgm.prediction.zeroTemp",
                        preferences.getBoolean(
                            "showPredictions",
                            true,
                        ),
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
    val setShowPredictions: (Boolean) -> Unit,
    val setCgmStream: (String, Boolean) -> Unit = { _, _ -> },
    val setShowMetabolicGraph: (Boolean) -> Unit,
    val setCompact: (Boolean) -> Unit,
    val setLiveNotification: (Boolean) -> Unit,
    val setWatchFaceIndex: (Int) -> Unit,
    val syncNow: () -> Unit,
    val openContactEmail: () -> Unit,
    val openGithub: () -> Unit,
)

class DashboardViewFactory(
    private val context: Context,
    private val callbacks: DashboardCallbacks,
) {
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
        state: TherapyDisplayState?,
        diagnostics: DiagnosticsSnapshot,
        preferences: DashboardUiPreferences,
    ) {
        parent.removeAllViews()
        overviewRenderState.value = null
        watchRenderState.value = null
        activeComposeScreen = null
        activeComposeView = null
        parent.addView(screenTitle())

        parent.addView(settingsGroupLabel("Verbindung & Daten"), fullWidth())
        parent.addView(
            tile(null).apply {
                addView(
                    labeledChoiceRow(
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
                    labeledChoiceRow(
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
                addView(
                    infoRowCompact(
                        "Verbundene Uhren",
                        when {
                            diagnostics.reachableWatches <= 0 -> "Keine"
                            diagnostics.reachableWatches == 1 -> "1 verbunden"
                            else -> "${diagnostics.reachableWatches} verbunden"
                        },
                    ),
                )
                addView(divider())
                addView(
                    infoRowCompact(
                        "Letzte Synchronisierung",
                        diagnostics.lastSyncAt.asDateTime(),
                    ),
                )
                addView(divider())
                addView(actionRow("Jetzt synchronisieren") { callbacks.syncNow() })
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("Anzeige & Übersicht"), fullWidth())
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
                        "Kompakte Übersicht",
                        preferences.compact,
                        R.id.dashboard_compact_switch,
                        callbacks.setCompact,
                    ),
                )
                addView(divider())
                addView(
                    switchRowCompact(
                        "IOB/COB-Verlaufsgraph",
                        preferences.showMetabolicGraph,
                        View.generateViewId(),
                        callbacks.setShowMetabolicGraph,
                    ),
                )
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("CGM-Graph"), fullWidth())
        parent.addView(
            tile(null).apply {
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
                        "Insulinaktivität",
                        preferences.showCgmActivity,
                        View.generateViewId(),
                    ) { callbacks.setCgmStream("cgm.activity", it) },
                )
                addView(divider())
                addView(
                    labeledChoiceRow(
                        "Prognosen",
                        listOf(
                            Triple("IOB", preferences.showCgmPredictionIob) {
                                callbacks.setCgmStream("cgm.prediction.iob", !preferences.showCgmPredictionIob)
                            },
                            Triple("COB", preferences.showCgmPredictionCob) {
                                callbacks.setCgmStream("cgm.prediction.cob", !preferences.showCgmPredictionCob)
                            },
                            Triple("UAM", preferences.showCgmPredictionUam) {
                                callbacks.setCgmStream("cgm.prediction.uam", !preferences.showCgmPredictionUam)
                            },
                            Triple("ZeroTemp", preferences.showCgmPredictionZeroTemp) {
                                callbacks.setCgmStream("cgm.prediction.zeroTemp", !preferences.showCgmPredictionZeroTemp)
                            },
                        ),
                    ),
                )
            },
            cardParams(top = 4),
        )

        parent.addView(settingsGroupLabel("Benachrichtigung"), fullWidth())
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

        parent.addView(settingsGroupLabel("Farben"), fullWidth())
        val colorSettings =
            androidx.compose.ui.platform.ComposeView(context).apply {
                setViewCompositionStrategy(
                    androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow,
                )
                setContent {
                    SugarliciousTheme {
                        SugarliciousColorSettingsPanel()
                    }
                }
            }

        parent.addView(colorSettings, cardParams(top = 4))
        parent.addView(settingsGroupLabel("Info & Support"), fullWidth())
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
            header.addView(value("Unabhaengige Companion-App fuer Wear OS", secondary, 12f, 2).apply { gravity = Gravity.CENTER })
            addView(header)
            addView(divider())
            addView(actionRow("E-Mail") { callbacks.openContactEmail() }.also { it.id = R.id.dashboard_contact_email })
            addView(divider())
            addView(actionRow("Projektseite") { callbacks.openGithub() }.also { it.id = R.id.dashboard_github })
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


    private fun settingsGroupLabel(label: String) =
        TextView(context).apply {
            text = label.uppercase()
            textSize = 12f
            setTextColor(accent)
            typeface = Typeface.create("sans", Typeface.BOLD)
            letterSpacing = 0.04f
            setPadding(4.dp, 10.dp, 4.dp, 2.dp)
        }

    private fun labeledChoiceRow(
        title: String,
        items: List<Triple<String, Boolean, () -> Unit>>,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4.dp, 0, 4.dp)
            addView(value(title, text, 15f, 1))
            addView(chipRow(items))
        }

    private fun infoRowCompact(
        name: String,
        value: String,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 52.dp
            setPadding(0, 6.dp, 0, 6.dp)

            addView(
                value(name, text, 15f, 1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                TextView(context).apply {
                    text = value
                    textSize = 13f
                    setTextColor(secondary)
                    maxLines = 2
                    gravity = Gravity.END
                },
            )
        }

    private fun actionRow(
        title: String,
        action: () -> Unit,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 52.dp
            setPadding(0, 6.dp, 0, 6.dp)
            background = selectableForeground()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }

            addView(
                value(title, text, 15f, 1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                TextView(context).apply {
                    text = "›"
                    textSize = 20f
                    setTextColor(secondary)
                },
            )
        }

    private fun divider() =
        View(context).apply {
            setBackgroundColor(SugarliciousColors.argb(SugarliciousColorRole.BORDER))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.dp,
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
            setPadding(0, 6.dp, 0, 6.dp)

            addView(
                value(title, text, 15f, 1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                Switch(context).apply {
                    this.id = id
                    isChecked = checked
                    thumbTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(accent, secondary),
                    )
                    trackTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(
                            SugarliciousColors.argb(SugarliciousColorRole.SURFACE_SELECTED),
                            SugarliciousColors.argb(SugarliciousColorRole.SURFACE_HIGH),
                        ),
                    )
                    setOnCheckedChangeListener { _, value -> callback(value) }
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
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp, 4.dp, 4.dp, 10.dp)
            addView(value("Einstellungen", text, 26f, 1))
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

    private val Int.dp: Int
        get() =
            (this * density).toInt()
}
