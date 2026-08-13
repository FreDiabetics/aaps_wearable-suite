from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"updated {rel}")


def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"missing exact block: {label}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, repl, label, flags=re.S):
    if re.search(pattern, text, flags) is None:
        raise RuntimeError(f"missing regex block: {label}")
    return re.sub(pattern, repl, text, count=1, flags=flags)


# ---------------------------------------------------------------------------
# Dashboard preferences + System/Light/Dark + requested CGM defaults/target.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt"
s = read(rel)
s = s.replace("enum class DashboardThemeMode { DARK, LIGHT }", "enum class DashboardThemeMode { SYSTEM, LIGHT, DARK }")
s = s.replace("val showCgmTargetRange: Boolean = false,", "val showCgmTargetRange: Boolean = true,\n    val showCgmTargetValue: Boolean = false,")
s = s.replace("val themeMode: DashboardThemeMode = DashboardThemeMode.DARK,", "val themeMode: DashboardThemeMode = DashboardThemeMode.SYSTEM,")
s = s.replace('preferences.getBoolean(\n                        "cgm.targetRange",\n                        false,\n                    )', 'preferences.getBoolean(\n                        "cgm.targetRange",\n                        true,\n                    )')
if "showCgmTargetValue =" not in s:
    s = s.replace(
        "                showCgmTargetRange =\n                    preferences.getBoolean(\n                        \"cgm.targetRange\",\n                        true,\n                    ),\n",
        "                showCgmTargetRange =\n                    preferences.getBoolean(\n                        \"cgm.targetRange\",\n                        true,\n                    ),\n                showCgmTargetValue =\n                    preferences.getBoolean(\n                        \"cgm.targetValue\",\n                        false,\n                    ),\n",
        1,
    )
s = s.replace('preferences.getString("themeMode", "DARK")!!', 'preferences.getString("themeMode", "SYSTEM")!!')
s = s.replace('.getOrDefault(DashboardThemeMode.DARK)', '.getOrDefault(DashboardThemeMode.SYSTEM)')
old_theme = '''                addView(
                    switchRowCompact(
                        "Heller Modus",
                        preferences.themeMode == DashboardThemeMode.LIGHT,
                        View.generateViewId(),
                    ) { callbacks.setThemeMode(if (it) DashboardThemeMode.LIGHT else DashboardThemeMode.DARK) },
                )'''
new_theme = '''                addView(
                    choiceRow(
                        "Darstellung",
                        listOf(
                            Triple("System", preferences.themeMode == DashboardThemeMode.SYSTEM) {
                                callbacks.setThemeMode(DashboardThemeMode.SYSTEM)
                            },
                            Triple("Hell", preferences.themeMode == DashboardThemeMode.LIGHT) {
                                callbacks.setThemeMode(DashboardThemeMode.LIGHT)
                            },
                            Triple("Dunkel", preferences.themeMode == DashboardThemeMode.DARK) {
                                callbacks.setThemeMode(DashboardThemeMode.DARK)
                            },
                        ),
                    ),
                )'''
s = replace_once(s, old_theme, new_theme, "theme choice row")
if '"Aktueller Zielwert"' not in s:
    needle = '''                    addView(
                        switchRowCompact(
                            "Zielbereich",
                            preferences.showCgmTargetRange,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetRange", it) },
                    )
                    addView(divider())'''
    replacement = '''                    addView(
                        switchRowCompact(
                            "Zielbereich",
                            preferences.showCgmTargetRange,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetRange", it) },
                    )
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Aktueller Zielwert",
                            preferences.showCgmTargetValue,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetValue", it) },
                    )
                    addView(divider())'''
    s = replace_once(s, needle, replacement, "current target toggle")
write(rel, s)

# ---------------------------------------------------------------------------
# Main activity: one-time requested defaults + system bar contrast.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt"
s = read(rel)
if 'overviewDefaultsMigratedV2' not in s:
    marker = '''        if (!uiPreferences.getBoolean("cgmDotsOnlyDefaultMigratedV1", false)) {
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
        }'''
    addition = marker + '''
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
        }'''
    s = replace_once(s, marker, addition, "overview defaults migration")
s = s.replace(
    "val light = DashboardUiPreferences.read(uiPreferences).themeMode == DashboardThemeMode.LIGHT",
    "val light = SugarliciousColors.palette.isLight",
)
write(rel, s)

# ---------------------------------------------------------------------------
# Color store: SYSTEM follows Android uiMode.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/ui/theme/SugarliciousColors.kt"
s = read(rel)
if "import android.content.res.Configuration" not in s:
    s = s.replace(
        "import android.content.SharedPreferences\n",
        "import android.content.SharedPreferences\nimport android.content.res.Configuration\nimport android.content.res.Resources\n",
        1,
    )
old = '''    private fun isLight(preferences: SharedPreferences): Boolean =
        preferences.getString("themeMode", "DARK") == "LIGHT"'''
new = '''    private fun systemIsLight(): Boolean =
        (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES

    private fun isLight(preferences: SharedPreferences): Boolean =
        when (preferences.getString("themeMode", "SYSTEM")) {
            "LIGHT" -> true
            "DARK" -> false
            else -> systemIsLight()
        }'''
s = replace_once(s, old, new, "system theme resolution")
write(rel, s)

# ---------------------------------------------------------------------------
# Overview: one shared viewport, no default future treatments, target line,
# theme-aware trend arrow.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt"
s = read(rel)
if "import androidx.compose.ui.graphics.ColorFilter" not in s:
    s = s.replace("import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.ColorFilter\n", 1)
s = regex_once(
    s,
    r'''val metabolicChartViewport\s*=\s*remember\s*\{\s*ChartViewport\(\s*preferences\.graphHours,?\s*\)\s*\}''',
    "val metabolicChartViewport = cgmChartViewport",
    "shared chart viewport",
)
s = s.replace(
    '''    val metabolicFutureWindowMs =
        if (
            preferences.showMetabolicGraph
        ) {
            90L * 60_000L
        } else {
            0L
        }''',
    '''    val metabolicFutureWindowMs =
        if (
            preferences.showMetabolicGraph &&
            preferences.anyCgmPredictionEnabled
        ) {
            90L * 60_000L
        } else {
            0L
        }''',
)
s = regex_once(
    s,
    r'''LaunchedEffect\(\s*predictionFutureWindowMs,?\s*\)\s*\{\s*cgmChartViewport\.setFutureWindow\(\s*predictionFutureWindowMs,?\s*\)\s*\}\s*\n\s*LaunchedEffect\(\s*metabolicFutureWindowMs,?\s*\)\s*\{\s*metabolicChartViewport\.setFutureWindow\(\s*metabolicFutureWindowMs,?\s*\)\s*\}''',
    '''LaunchedEffect(
        predictionFutureWindowMs,
        metabolicFutureWindowMs,
    ) {
        cgmChartViewport.setFutureWindow(
            maxOf(
                predictionFutureWindowMs,
                metabolicFutureWindowMs,
            ),
        )
    }''',
    "shared future window",
)
if "showTargetValue = preferences.showCgmTargetValue" not in s:
    s = s.replace(
        "                showTargetRange = preferences.showCgmTargetRange,\n",
        "                showTargetRange = preferences.showCgmTargetRange,\n                showTargetValue = preferences.showCgmTargetValue,\n",
        1,
    )
if "ColorFilter.tint(" not in s:
    s = regex_once(
        s,
        r'''(painter\s*=\s*painterResource\(\s*R\.drawable\.ic_trend_arrow,?\s*\),\s*contentDescription\s*=\s*null,)''',
        r'''\1
                    colorFilter = ColorFilter.tint(SugarliciousColors.TextPrimary),''',
        "trend arrow tint",
    )
write(rel, s)

# ---------------------------------------------------------------------------
# CGM charts: live edge, shared pan clamp, target line, right/bold target labels.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt"
s = read(rel)
s = s.replace(
    '''        futureWindowMs = next
        notifyChanged()''',
    '''        futureWindowMs = next
        clampPan()
        notifyChanged()''',
    1,
)
s = s.replace(
    '''            panMs.coerceIn(
                -24L * HOUR_MS,
                2L * HOUR_MS,
            )''',
    '''            panMs.coerceIn(
                -24L * HOUR_MS,
                0L,
            )''',
    1,
)
if "private var showTargetValue = false" not in s:
    s = s.replace("    private var showTargetRange = false\n", "    private var showTargetRange = false\n    private var showTargetValue = false\n", 1)
if "showTargetValue: Boolean = false" not in s:
    s = s.replace("        showTargetRange: Boolean = false,\n", "        showTargetRange: Boolean = false,\n        showTargetValue: Boolean = false,\n", 1)
if "this.showTargetValue" not in s:
    s = s.replace("        this.showTargetRange =\n            showTargetRange\n", "        this.showTargetRange =\n            showTargetRange\n        this.showTargetValue =\n            showTargetValue\n", 1)
s = s.replace(
    "            val end = viewport.endEpochMs(now)\n",
    '''            val liveEdge =
                if (viewport.futureWindowMs == 0L) {
                    state?.glucose?.measuredAtEpochMs?.coerceAtMost(now) ?: now
                } else {
                    now
                }
            val end = viewport.endEpochMs(liveEdge)
''',
    1,
)
if "showTargetValue" in s and "Ziel ${glucoseLabel(targetValue)}" not in s:
    insert = '''            if (showTargetValue) {
                val targetValue = (targetLow + targetHigh) / 2.0
                val targetY = mapGlucoseY(targetValue, plot)
                linePaint.color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL)
                linePaint.strokeWidth = 1f.dp
                linePaint.pathEffect = DashPathEffect(floatArrayOf(5f.dp, 4f.dp), 0f)
                canvas.drawLine(plot.left, targetY, plot.right, targetY, linePaint)
                linePaint.pathEffect = null
                drawText(
                    canvas,
                    "Ziel ${glucoseLabel(targetValue)}",
                    plot.right - 1f.dp,
                    (targetY - 4f.dp).coerceAtLeast(plot.top + 12f.dp),
                    10f,
                    SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL),
                    Paint.Align.RIGHT,
                )
            }
'''
    s = s.replace("            drawGrid(canvas, plot, start, end)\n", "            drawGrid(canvas, plot, start, end)\n" + insert, 1)
s = s.replace("x = plot.right - 8f.dp,", "x = plot.right - 1f.dp,")
s = regex_once(
    s,
    r'''\n\s*drawText\(\s*canvas,\s*"0",\s*plot\.right\s*-\s*8f\.dp,\s*zeroY\s*-\s*4f\.dp,\s*8f,\s*SugarliciousColors\.argb\(\s*SugarliciousColorRole\.GRAPH_LABEL,?\s*\),\s*Paint\.Align\.RIGHT,?\s*\)''',
    "",
    "remove zero label",
)
s = regex_once(
    s,
    r'''private fun drawTargetLabel\(canvas: Canvas, value: String, x: Float, y: Float\)\s*=\s*drawText\([\s\S]*?Paint\.Align\.RIGHT,?\s*\)''',
    '''private fun drawTargetLabel(canvas: Canvas, value: String, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                10f,
                resources.displayMetrics,
            )
            color = SugarliciousColors.argb(SugarliciousColorRole.GRAPH_LABEL)
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(value, x, y, paint)
    }''',
    "bold target labels",
)
# Metabolic graph uses same live edge anchor as CGM when no future data is enabled.
s = s.replace(
    '''            val end =
                viewport.endEpochMs(
                    System.currentTimeMillis(),
                )''',
    '''            val chartNow = System.currentTimeMillis()
            val liveEdge =
                if (viewport.futureWindowMs == 0L) {
                    state?.glucose?.measuredAtEpochMs?.coerceAtMost(chartNow) ?: chartNow
                } else {
                    chartNow
                }
            val end =
                viewport.endEpochMs(
                    liveEdge,
                )''',
    1,
)
write(rel, s)

# ---------------------------------------------------------------------------
# Collapsed notification: fixed 3h, fixed aspect, no oval dots.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/res/layout/notification_sugarlicious_collapsed.xml"
write(rel, '''<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="48dp">

    <ImageView
        android:id="@+id/notification_graph"
        android:layout_width="176dp"
        android:layout_height="46dp"
        android:layout_alignParentEnd="true"
        android:layout_centerVertical="true"
        android:contentDescription="@null"
        android:scaleType="centerCrop" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_toStartOf="@id/notification_graph"
        android:layout_marginEnd="6dp"
        android:gravity="center_vertical"
        android:orientation="vertical">

        <TextView
            android:id="@+id/notification_value"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:fontFamily="sans-serif-medium"
            android:maxLines="1"
            android:textSize="18sp"
            tools:text="123 →" />

        <TextView
            android:id="@+id/notification_meta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textSize="9sp"
            tools:text="mg/dL · 2 min alt" />
    </LinearLayout>
</RelativeLayout>
''')

rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt"
s = read(rel)
s = re.sub(r"const val COLLAPSED_WIDTH = \d+", "const val COLLAPSED_WIDTH = 704", s, count=1)
s = re.sub(r"const val COLLAPSED_HEIGHT = \d+", "const val COLLAPSED_HEIGHT = 184", s, count=1)
s = s.replace("private const val COLLAPSED_DISPLAY_HEIGHT_DP = 44f", "private const val COLLAPSED_DISPLAY_HEIGHT_DP = 46f")
if "graphHoursOverride = 3" not in s:
    s = s.replace(
        '''            displayHeightDp =
                COLLAPSED_DISPLAY_HEIGHT_DP,
        )''',
        '''            displayHeightDp =
                COLLAPSED_DISPLAY_HEIGHT_DP,
            graphHoursOverride = 3,
        )''',
        1,
    )
if "graphHoursOverride: Int? = null" not in s:
    s = s.replace(
        '''        displayHeightDp: Float =
            EXPANDED_DISPLAY_HEIGHT_DP,
    ): Bitmap {''',
        '''        displayHeightDp: Float =
            EXPANDED_DISPLAY_HEIGHT_DP,
        graphHoursOverride: Int? = null,
    ): Bitmap {''',
        1,
    )
s = s.replace(
    '''        val graphHours =
            preferences''',
    '''        val graphHours =
            graphHoursOverride ?: preferences''',
    1,
)
write(rel, s)

# ---------------------------------------------------------------------------
# Overview watch model name larger.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/main/kotlin/app/aapswear/mobile/OverviewWatchFaceTile.kt"
s = read(rel)
s = regex_once(
    s,
    r'''("Galaxy Watch Ultra"[\s\S]{0,180}?fontSize\s*=\s*)16\.sp''',
    r'''\g<1>20.sp''',
    "larger watch model name",
)
write(rel, s)

# ---------------------------------------------------------------------------
# Watch Face Push cancellation lifecycle fix.
# ---------------------------------------------------------------------------
rel = "app-wear/src/main/kotlin/app/aapswear/wear/WatchFacePushController.kt"
s = read(rel)
if "import kotlinx.coroutines.CancellationException" not in s:
    s = s.replace("import java.io.File\n", "import java.io.File\nimport kotlinx.coroutines.CancellationException\n", 1)
s = s.replace(
    '''        } catch (error: Exception) {
            "Watchface-Wechsel fehlgeschlagen: ${error.javaClass.simpleName}"
        } finally {''',
    '''        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "Watchface-Wechsel fehlgeschlagen: ${error.javaClass.simpleName}"
        } finally {''',
    1,
)
write(rel, s)

rel = "app-wear/src/main/kotlin/app/aapswear/wear/StateDataLayerService.kt"
s = read(rel)
if "import kotlinx.coroutines.sync.Mutex" not in s:
    s = s.replace("import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock\n", 1)
old_push = '''        scope.launch {
            val status =
                SugarliciousWatchFacePush.apply(
                    this@StateDataLayerService,
                    index,
                )

            runCatching {
                Wearable
                    .getMessageClient(
                        this@StateDataLayerService,
                    )
                    .sendMessage(
                        event.sourceNodeId,
                        WearProtocol.WATCH_FACE_STATUS_PATH,
                        status.encodeToByteArray(),
                    )
                    .await()
            }
        }'''
new_push = '''        val appContext = applicationContext
        val sourceNodeId = event.sourceNodeId

        watchFacePushScope.launch {
            watchFacePushMutex.withLock {
                val status =
                    SugarliciousWatchFacePush.apply(
                        appContext,
                        index,
                    )

                runCatching {
                    Wearable
                        .getMessageClient(appContext)
                        .sendMessage(
                            sourceNodeId,
                            WearProtocol.WATCH_FACE_STATUS_PATH,
                            status.encodeToByteArray(),
                        )
                        .await()
                }
            }
        }'''
s = replace_once(s, old_push, new_push, "watchface process scope")
if "private val watchFacePushScope" not in s:
    s = s.replace(
        "    companion object {\n",
        '''    companion object {
        private val watchFacePushScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val watchFacePushMutex = Mutex()

''',
        1,
    )
write(rel, s)

# ---------------------------------------------------------------------------
# Complication graph: app-like non-linear scale, target band, grid and dots.
# ---------------------------------------------------------------------------
rel = "complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt"
s = read(rel)
if "import android.graphics.DashPathEffect" not in s:
    s = s.replace("import android.graphics.Color\n", "import android.graphics.Color\nimport android.graphics.DashPathEffect\n", 1)
start = s.index("    private fun drawGraphImage(")
end = s.index("    private fun readGraphColors(): WatchGraphColors", start)
new_graph = '''    private fun drawGraphImage(
        canvas: Canvas,
        state: TherapyDisplayState?,
        height: Int,
        now: Long,
        windowMs: Long,
    ) {
        val width = canvas.width
        val glucose = state?.glucose
        val colors = readGraphColors()
        val graphStyle = readGraphStyle()
        val targetLow = state?.target?.lowMgDl ?: DISPLAY_LOW_MGDL
        val targetHigh = state?.target?.highMgDl ?: DISPLAY_HIGH_MGDL
        val density = resources.displayMetrics.density
        val plotLeft = 1f
        val plotRight = width - 1f
        val plotTop = 1f
        val plotBottom = height - 1f
        val plotHeight = plotBottom - plotTop

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.graphBackground
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 22f, 22f, backgroundPaint)

        fun glucoseRatio(valueMgDl: Double): Double {
            val value = valueMgDl.coerceIn(0.0, 400.0)
            return when {
                value <= 80.0 -> 0.055 + value / 80.0 * (0.215 - 0.055)
                value <= 160.0 -> 0.215 + (value - 80.0) / 80.0 * (0.515 - 0.215)
                else -> 0.515 + (value - 160.0) / (400.0 - 160.0) * (1.0 - 0.515)
            }.coerceIn(0.055, 1.0)
        }

        fun yFor(valueMgDl: Double): Float =
            plotBottom - (glucoseRatio(valueMgDl) * plotHeight).toFloat()

        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.rangeInRange
            style = Paint.Style.FILL
        }
        canvas.drawRect(plotLeft, yFor(targetHigh), plotRight, yFor(targetLow), targetPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.divider
            style = Paint.Style.STROKE
            strokeWidth = 0.7f * density
        }
        canvas.drawLine(plotLeft, yFor(0.0), plotRight, yFor(0.0), gridPaint)
        gridPaint.pathEffect = DashPathEffect(floatArrayOf(3f * density, 3f * density), 0f)
        for (index in 1..3) {
            val y = plotTop + plotHeight * index / 4f
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
        }
        gridPaint.pathEffect = null

        val cutoff = now - windowMs
        val merged = linkedMapOf<Long, GlucoseSample>()
        state?.glucoseHistory.orEmpty().forEach { merged[it.measuredAtEpochMs] = it }
        glucose?.let {
            merged[it.measuredAtEpochMs] = GlucoseSample(it.valueMgDl, it.measuredAtEpochMs)
        }
        val samples = merged.values.asSequence()
            .filter {
                it.measuredAtEpochMs in cutoff..(now + FUTURE_TOLERANCE_MS) &&
                    it.valueMgDl in 20.0..1000.0
            }
            .sortedBy { it.measuredAtEpochMs }
            .toList()

        if (samples.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.divider
                textAlign = Paint.Align.CENTER
                textSize = 26f
            }
            canvas.drawText("No history", width / 2f, (plotTop + plotBottom) / 2f, emptyPaint)
            return
        }

        fun xFor(timestamp: Long): Float {
            val fraction = ((timestamp - cutoff).toDouble() / windowMs.toDouble()).coerceIn(0.0, 1.0)
            return plotLeft + (fraction * (plotRight - plotLeft)).toFloat()
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = colors.outline
        }

        samples.forEachIndexed { index, sample ->
            dotPaint.color = when {
                sample.valueMgDl < targetLow -> colors.cgmLow
                sample.valueMgDl > targetHigh -> colors.cgmHigh
                else -> colors.cgmInRange
            }
            val dotRadius = (
                graphStyle.cgmDotRadiusDp.coerceIn(1.5f, 6.0f) +
                    if (index == samples.lastIndex) 0.1f else 0f
                ) * density
            val x = xFor(sample.measuredAtEpochMs)
            val y = yFor(sample.valueMgDl)
            canvas.drawCircle(x, y, dotRadius, dotPaint)
            if (graphStyle.cgmDotOutlineEnabled) {
                val outlineWidth = graphStyle.cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f) * density
                outlinePaint.strokeWidth = outlineWidth
                canvas.drawCircle(x, y, dotRadius + outlineWidth / 2f, outlinePaint)
            }
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.divider
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawRoundRect(plotLeft, plotTop, plotRight, plotBottom, 22f, 22f, borderPaint)
    }

'''
s = s[:start] + new_graph + s[end:]
write(rel, s)

# ---------------------------------------------------------------------------
# Tests for requested defaults + future viewport/prediction setup.
# ---------------------------------------------------------------------------
rel = "app-mobile/src/test/kotlin/app/aapswear/mobile/MainActivityTest.kt"
s = read(rel)
s = s.replace("@Test fun `fresh install defaults to CGM dots only`()", "@Test fun `fresh install uses requested overview and CGM defaults`()")
s = s.replace(
    '''        assertTrue(ui.showCgmGraph)
        assertFalse(ui.showCgmTargetRange)
        assertFalse(ui.showCgmBasal)''',
    '''        assertTrue(ui.showCgmGraph)
        assertTrue(ui.showDetails)
        assertTrue(ui.showCgmTargetRange)
        assertFalse(ui.showCgmTargetValue)
        assertFalse(ui.showCgmBasal)''',
    1,
)
write(rel, s)

rel = "app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt"
s = read(rel)
old = '''        val bitmap = render(GlucoseDashboardChart(context).apply { bind(state, GlucoseUnit.MG_DL, true, 6) }, 230)'''
new = '''        val viewport = ChartViewport(6).apply {
            setFutureWindow(15L * 60_000L)
        }
        val bitmap = render(
            GlucoseDashboardChart(context = context, sharedViewport = viewport).apply {
                bind(
                    state = state,
                    unit = GlucoseUnit.MG_DL,
                    showPredictions = true,
                    durationHours = 6,
                    showTargetRange = true,
                    showPredictionIob = true,
                )
            },
            230,
        )'''
s = replace_once(s, old, new, "prediction test setup")
if "viewport cannot pan beyond configured future edge" not in s:
    insert_at = s.index("    private fun render(")
    test = '''    @Test fun `viewport cannot pan beyond configured future edge`() {
        val now = 10_000_000L
        val viewport = ChartViewport(6)
        viewport.setFutureWindow(0L)
        viewport.pan(-10_000f, 100f)
        assertEquals(0L, viewport.panMs)
        assertEquals(now, viewport.endEpochMs(now))

        viewport.setFutureWindow(60L * 60_000L)
        viewport.pan(-10_000f, 100f)
        assertEquals(0L, viewport.panMs)
        assertEquals(now + 60L * 60_000L, viewport.endEpochMs(now))
    }

'''
    s = s[:insert_at] + test + s[insert_at:]
write(rel, s)

# ---------------------------------------------------------------------------
# WFF metadata for all pushed non-analog faces.
# ---------------------------------------------------------------------------
info = '''<?xml version="1.0" encoding="utf-8"?>
<WatchFaceInfo>
    <Preview value="@drawable/preview" />
    <MultipleInstancesAllowed value="true" />
    <FlavorsSupported value="false" />
    <Editable value="true" />
</WatchFaceInfo>
'''
shapes = '''<?xml version="1.0" encoding="utf-8"?>
<WatchFaces>
    <WatchFace file="@raw/watchface" height="450" shape="CIRCLE" width="450" />
</WatchFaces>
'''
for face in ("sugarlicious-orbit", "sugarlicious-rings", "sugarlicious-graph"):
    write(f"watchfaces/{face}/src/main/res/xml/watch_face_info.xml", info)
    write(f"watchfaces/{face}/src/main/res/xml/watch_face_shapes.xml", shapes)

# ---------------------------------------------------------------------------
# Faster Gradle defaults.
# ---------------------------------------------------------------------------
rel = "gradle.properties"
s = read(rel)
props = {
    "org.gradle.parallel": "true",
    "org.gradle.caching": "true",
    "org.gradle.configuration-cache": "true",
    "org.gradle.configuration-cache.problems": "warn",
    "kotlin.incremental": "true",
}
lines = s.splitlines()
for key, value in props.items():
    found = False
    for i, line in enumerate(lines):
        if line.startswith(key + "="):
            lines[i] = f"{key}={value}"
            found = True
            break
    if not found:
        lines.append(f"{key}={value}")
write(rel, "\n".join(lines) + "\n")

# ---------------------------------------------------------------------------
# One-command local sync/build/install workflow.
# ---------------------------------------------------------------------------
write("dev.ps1", r'''param(
    [Parameter(Position = 0)]
    [ValidateSet("mobile", "wear", "all", "wfp")]
    [string]$Target = "mobile",
    [switch]$Test,
    [switch]$NoPull
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$phone = "adb-R3GL30M0HYX-gUIExC._adb-tls-connect._tcp"
$watch = "adb-RFAY12MBZ8X-AVH2AE._adb-tls-connect._tcp"

function Assert-LastExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

if (-not $NoPull) {
    $dirty = @(git status --porcelain)
    Assert-LastExitCode "git status"
    if ($dirty.Count -gt 0) {
        throw "Local changes exist. Commit or stash them first, or use -NoPull intentionally."
    }
    Write-Host "Syncing GitHub..."
    git pull --ff-only
    Assert-LastExitCode "git pull"
}

$effectiveTarget = $Target
if ($Target -eq "wfp") {
    Write-Host "Preparing Watch Face Push assets..."
    & .\tools\watchface-push\Prepare-WatchFacePushAssets.ps1
    if (-not $?) { throw "Watch Face Push asset preparation failed" }
    $effectiveTarget = "wear"
}

[string[]]$gradleTasks = @()
if ($effectiveTarget -eq "mobile") {
    if ($Test) { $gradleTasks += ":app-mobile:testDebugUnitTest" }
    $gradleTasks += ":app-mobile:assembleDebug"
} elseif ($effectiveTarget -eq "wear") {
    if ($Test) { $gradleTasks += ":app-wear:testDebugUnitTest" }
    $gradleTasks += ":app-wear:assembleDebug"
} elseif ($effectiveTarget -eq "all") {
    if ($Test) {
        $gradleTasks += ":app-mobile:testDebugUnitTest"
        $gradleTasks += ":app-wear:testDebugUnitTest"
        $gradleTasks += ":complications:testDebugUnitTest"
    }
    $gradleTasks += ":app-mobile:assembleDebug"
    $gradleTasks += ":app-wear:assembleDebug"
} else {
    throw "Unsupported target: $effectiveTarget"
}

Write-Host "Running Gradle tasks:"
foreach ($task in $gradleTasks) { Write-Host "  $task" }
& .\gradlew.bat @gradleTasks
Assert-LastExitCode "Gradle"

if (($effectiveTarget -eq "mobile") -or ($effectiveTarget -eq "all")) {
    $mobileApk = Get-ChildItem .\app-mobile\build\outputs\apk\debug\*.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $mobileApk) { throw "Mobile APK not found" }
    Write-Host "Installing Mobile..."
    adb -s $phone install -r $mobileApk.FullName
    Assert-LastExitCode "Mobile install"
    adb -s $phone shell am force-stop app.aapswear
    Assert-LastExitCode "Mobile force-stop"
    adb -s $phone shell monkey -p app.aapswear 1 | Out-Null
    Assert-LastExitCode "Mobile start"
}

if (($effectiveTarget -eq "wear") -or ($effectiveTarget -eq "all")) {
    $wearApk = Get-ChildItem .\app-wear\build\outputs\apk\debug\*.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $wearApk) { throw "Wear APK not found" }
    Write-Host "Installing Wear..."
    adb -s $watch install -r $wearApk.FullName
    Assert-LastExitCode "Wear install"
    adb -s $watch shell am force-stop app.aapswear
    Assert-LastExitCode "Wear force-stop"
    adb -s $watch shell monkey -p app.aapswear 1 | Out-Null
    Assert-LastExitCode "Wear start"
}

Write-Host ""
Write-Host "OK: $effectiveTarget built and installed."
''')

print("all current chat changes integrated")
