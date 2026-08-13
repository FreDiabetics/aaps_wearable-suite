from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')
    print('updated', path)

def literal(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected 1 match, got {count}')
    return text.replace(old, new, 1)

def regex(text, pattern, repl, label):
    out, count = re.subn(pattern, repl, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f'{label}: expected 1 match, got {count}')
    return out

# ---------------------------------------------------------------------------
# Shared monochrome icon. The outer hexagon is intentionally 0.5 wider.
# ---------------------------------------------------------------------------
icon = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="0.5"
        android:strokeLineJoin="round"
        android:pathData="M12.01,23.95c-0.56,0 -1.11,-0.14 -1.61,-0.43l-7.56,-4.36c-0.99,-0.57 -1.61,-1.64 -1.61,-2.78V7.64c0,-1.14 0.6,-2.21 1.59,-2.78L10.38,0.48c0.99,-0.57 2.22,-0.58 3.22,0l7.56,4.36c0.99,0.57 1.61,1.64 1.61,2.78v8.74c0,1.14 -0.6,2.21 -1.59,2.78l-7.55,4.38c-0.5,0.29 -1.05,0.43 -1.61,0.43ZM11.99,1.33c-0.34,0 -0.67,0.09 -0.97,0.26L3.47,5.96c-0.6,0.35 -0.97,0.99 -0.96,1.68v8.74c0,0.69 0.38,1.33 0.98,1.67l7.56,4.36c0.6,0.35 1.34,0.34 1.94,0l7.55,-4.38c0.6,-0.35 0.97,-0.99 0.96,-1.68V7.62c0,-0.69 -0.38,-1.33 -0.98,-1.67L12.96,1.58c-0.3,-0.17 -0.63,-0.26 -0.97,-0.26Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M11.94,4.28l-3.24,5.64s-0.01,0.1 0.03,0.13l3.24,2.17s0.07,0.02 0.11,0l3.24,-2.17s0.05,-0.08 0.03,-0.13l-3.24,-5.64c-0.04,-0.06 -0.13,-0.06 -0.17,0ZM11.97,10.54l-1.38,-0.92s-0.05,-0.08 -0.03,-0.13l1.38,-2.4c0.04,-0.06 0.13,-0.06 0.17,0l1.39,2.4s0.01,0.1 -0.03,0.13l-1.39,0.92s-0.07,0.02 -0.11,0ZM15.76,10.76l-3.28,2.2s-0.04,0.05 -0.04,0.08v4.19c0,0.05 0.04,0.1 0.1,0.1h6.97c0.07,0,0.12,-0.08 0.08,-0.14l-3.7,-6.39s-0.09,-0.06 -0.14,-0.03ZM13.8,13.59l1.37,-0.92s0.11,-0.02 0.14,0.03l1.77,3.07c0.04,0.06 0,0.14 -0.08,0.14h-3.14c-0.05,0 -0.1,-0.05 -0.1,-0.1v-2.15s0.02,-0.06 0.04,-0.08ZM11.6,13.04v4.19c0,0.05 -0.04,0.1 -0.1,0.1h-1.16c-0.05,0 -0.1,-0.04 -0.1,-0.1v-2.04c0,-0.05 -0.04,-0.1 -0.1,-0.1H5.81c-0.07,0 -0.12,-0.08 -0.08,-0.14l0.55,-0.95s0.05,-0.05 0.08,-0.05h3.78c0.05,0 0.1,-0.04 0.1,-0.1v-0.19s-0.02,-0.06 -0.04,-0.08l-1.42,-0.92s-0.1,-0.02 -0.13,0.03l-0.14,0.22s-0.05,0.04 -0.08,0.04H7.05c-0.07,0 -0.12,-0.08 -0.08,-0.14l1.17,-2.03s0.09,-0.06 0.14,-0.03l3.28,2.2s0.04,0.05 0.04,0.08Z" />
</vector>
'''
for target in (
    'app-mobile/src/main/res/drawable/ic_sugarlicious_monochrome.xml',
    'app-mobile/src/main/res/drawable-anydpi/ic_notification_outlined.xml',
    'app-wear/src/main/res/drawable/ic_sugarlicious_monochrome.xml',
):
    write(target, icon)

write('app-mobile/src/main/res/drawable/ic_arrow_back.xml', '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.42,-1.41L7.83,13H20z" />
</vector>
''')

# ---------------------------------------------------------------------------
# Mobile shell: no bottom pill, compact brand/settings row, fixed Watch header.
# ---------------------------------------------------------------------------
write('app-mobile/src/main/res/layout/activity_main.xml', '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root" android:layout_width="match_parent" android:layout_height="match_parent"
    android:fitsSystemWindows="true" android:orientation="vertical">
    <LinearLayout android:id="@+id/top_app_bar" android:layout_width="match_parent" android:layout_height="56dp"
        android:gravity="center_vertical" android:orientation="horizontal" android:paddingStart="16dp" android:paddingEnd="16dp">
        <ImageView android:id="@+id/top_back" android:layout_width="36dp" android:layout_height="36dp" android:contentDescription="Zurück" android:padding="7dp" android:src="@drawable/ic_arrow_back" android:visibility="gone" />
        <ImageView android:id="@+id/brand_logo" android:layout_width="34dp" android:layout_height="34dp" android:contentDescription="@string/brand_logo" android:padding="3dp" android:scaleType="fitCenter" android:src="@drawable/ic_sugarlicious_monochrome" />
        <TextView android:id="@+id/app_title" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_marginStart="7dp" android:layout_weight="1" android:gravity="start|center_vertical" android:text="@string/app_name" android:textColor="@color/app_text" android:textSize="@dimen/text_app_title" android:textStyle="bold" />
        <ImageView android:id="@+id/top_settings" android:layout_width="36dp" android:layout_height="36dp" android:contentDescription="Einstellungen" android:padding="7dp" android:src="@drawable/ic_settings" />
    </LinearLayout>
    <FrameLayout android:id="@+id/watch_fixed_header" android:layout_width="match_parent" android:layout_height="wrap_content" android:paddingStart="@dimen/space_md" android:paddingEnd="@dimen/space_md" android:visibility="gone" />
    <FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1">
        <ScrollView android:id="@+id/dashboard_scroll" android:layout_width="match_parent" android:layout_height="match_parent" android:clipToPadding="false" android:fillViewport="true" android:scrollbars="none">
            <LinearLayout android:id="@+id/dashboard_content" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:paddingStart="@dimen/space_md" android:paddingTop="@dimen/space_xxs" android:paddingEnd="@dimen/space_md" android:paddingBottom="24dp" />
        </ScrollView>
        <View android:id="@+id/scroll_fade" android:layout_width="match_parent" android:layout_height="22dp" android:layout_gravity="bottom" android:background="@drawable/bg_scroll_fade" android:importantForAccessibility="no" />
    </FrameLayout>
</LinearLayout>
''')

p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt'
s = read(p)
s = literal(s, '        bindNavigation()\n', '        bindTopNavigation()\n', 'bind top nav')
s = regex(s,
    r'''        val sourceAvailable = diagnosticState\.sourceVersion != null\n        findViewById<ImageView>\(R\.id\.source_shield\)\.apply \{.*?\n        updateNavigation\(\)''',
    '''        renderFixedWatchHeader(diagnosticState, uiState)
        updateTopBar()''', 'refresh top bar')
s = regex(s,
    r'''        findViewById<View>\(R\.id\.bottom_navigation\)\.background =\s*GradientDrawable\(\)\.apply \{.*?\n            \}\nstyleTitle\(\)''',
    '''        findViewById<ImageView>(R.id.brand_logo).imageTintList = ColorStateList.valueOf(text)
        findViewById<ImageView>(R.id.top_back).imageTintList = ColorStateList.valueOf(text)
        findViewById<ImageView>(R.id.top_settings).imageTintList = ColorStateList.valueOf(text)
        updateTopBar()''', 'runtime top colors')
s = regex(s, r'''    private fun bindNavigation\(\) \{.*?\n    private fun styleTitle\(\) \{''', '''    private fun bindTopNavigation() {
        findViewById<View>(R.id.top_settings).setOnClickListener { navigate(DashboardScreen.SETTINGS) }
        findViewById<View>(R.id.top_back).setOnClickListener { navigate(DashboardScreen.OVERVIEW) }
    }

    private fun updateTopBar() {
        val back = findViewById<View>(R.id.top_back)
        val brand = findViewById<View>(R.id.brand_logo)
        val title = findViewById<TextView>(R.id.app_title)
        val settings = findViewById<View>(R.id.top_settings)
        when (screen) {
            DashboardScreen.OVERVIEW -> {
                back.visibility = View.GONE
                brand.visibility = View.VISIBLE
                settings.visibility = View.VISIBLE
                styleTitle()
            }
            DashboardScreen.WATCH -> {
                back.visibility = View.VISIBLE
                brand.visibility = View.GONE
                settings.visibility = View.GONE
                title.text = "Watch"
            }
            DashboardScreen.SETTINGS -> {
                back.visibility = View.VISIBLE
                brand.visibility = View.GONE
                settings.visibility = View.GONE
                title.text = "Einstellungen"
            }
        }
    }

    private fun renderFixedWatchHeader(
        diagnosticState: DiagnosticsSnapshot,
        uiState: DashboardUiPreferences,
    ) {
        val container = findViewById<android.widget.FrameLayout>(R.id.watch_fixed_header)
        if (screen != DashboardScreen.WATCH) {
            container.visibility = View.GONE
            container.removeAllViews()
            return
        }
        container.visibility = View.VISIBLE
        container.removeAllViews()
        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow,
            )
            setContent {
                app.aapswear.mobile.ui.theme.SugarliciousTheme {
                    OverviewWatchFaceTile(
                        state = state,
                        diagnostics = diagnosticState,
                        selectedFaceIndex = uiState.watchFaceIndex,
                        onSelectedFace = { index ->
                            uiPreferences.edit { putInt("watchFaceIndex", index.coerceIn(0, 3)) }
                        },
                        onEdit = {},
                        interactive = false,
                    )
                }
            }
        }
        container.addView(
            composeView,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun styleTitle() {''', 'replace old nav methods')
write(p, s)

p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt'
s = read(p)
s = literal(s, '        )\n\n        GlucoseHeroCard(\n', '        )\n\n        Spacer(Modifier.height(8.dp))\n\n        GlucoseHeroCard(\n', 'overview breathing room')
write(p, s)

# ---------------------------------------------------------------------------
# Watch menu: fixed overview-style device header handled by Activity; tiles below scroll.
# Watchface cards track current preset and live time.
# ---------------------------------------------------------------------------
p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousWatchScreen.kt'
s = read(p)
s = s.replace('import androidx.compose.runtime.rememberCoroutineScope\n', 'import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.setValue\n', 1)
s = regex(s, r'''    Column\(\n        modifier =\n            Modifier\n                \.fillMaxWidth\(\)\n                \.menuSwipeNavigation\(.*?\n        verticalArrangement = Arrangement\.spacedBy\(8\.dp\),\n    \) \{''', '''    val context = LocalContext.current
    var activePreset by remember { mutableStateOf(loadComplicationPreset(context)) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {''', 'watch scroll content')
s = literal(s, '                        state = state,\n                        selected = preferences.watchFaceIndex == index,\n', '                        state = state,\n                        activeComplicationIds = activePreset,\n                        selected = preferences.watchFaceIndex == index,\n', 'watch tile preset')
s = literal(s, '        ComplicationStudio(state = state)\n', '        ComplicationStudio(state = state, onPresetChanged = { activePreset = it })\n', 'watch preset callback')
s = literal(s, '    state: TherapyDisplayState?,\n    selected: Boolean,\n', '    state: TherapyDisplayState?,\n    activeComplicationIds: List<Int>,\n    selected: Boolean,\n', 'watch tile signature')
s = literal(s, '                state = state,\n                modifier = Modifier.size(116.dp),\n', '                state = state,\n                activeComplicationIds = activeComplicationIds,\n                modifier = Modifier.size(116.dp),\n', 'watch tile dial')
write(p, s)

p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/OverviewWatchFaceTile.kt'
s = read(p)
s = literal(s, '    onEdit: () -> Unit,\n) {\n', '    onEdit: () -> Unit,\n    interactive: Boolean = true,\n) {\n', 'overview tile interactive')
# Insert preset in function after constants are established by using unique selected line.
s = literal(s, '    val selected = selectedFaceIndex.coerceIn(0, sugarliciousWatchFaceNames.lastIndex)\n', '    val context = LocalContext.current\n    val activeComplicationIds = loadComplicationPreset(context)\n    val selected = selectedFaceIndex.coerceIn(0, sugarliciousWatchFaceNames.lastIndex)\n', 'overview active preset')
s = literal(s, '                        state = state,\n                        modifier = Modifier.size(carouselFaceSize),\n', '                        state = state,\n                        activeComplicationIds = activeComplicationIds,\n                        modifier = Modifier.size(carouselFaceSize),\n', 'overview face preset')
s = regex(s, r'''            Box\(\n                Modifier\.matchParentSize\(\)\n                    \.then\(oneStepSwipe\)\n                    \.clickable\(onClick = onEdit\),\n            \)''', '''            if (interactive) {
                Box(
                    Modifier.matchParentSize()
                        .then(oneStepSwipe)
                        .clickable(onClick = onEdit),
                )
            }''', 'disable fixed header gestures')
s = literal(s, '    state: TherapyDisplayState?,\n    modifier: Modifier = Modifier,\n) {\n', '    state: TherapyDisplayState?,\n    activeComplicationIds: List<Int> = emptyList(),\n    modifier: Modifier = Modifier,\n) {\n', 'face dial signature')
s = regex(s, r'''            // Fixed preview geometry:.*?            val secondAngle = CAROUSEL_PREVIEW_SECOND_ANGLE\.toFloat\(\)''', '''            // Live preview time.
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val second = calendar.get(java.util.Calendar.SECOND)
            val hourAngle = (hour % 12) * 30f + minute * 0.5f
            val minuteAngle = minute * 6f + second * 0.1f
            val secondAngle = second * 6f''', 'live watch hands')
s = regex(s, r'''            Text\(\n                "IOB \$\{.*?\n                fontSize =\n                    5\.5\.sp,\n            \)''', '''            if (activeComplicationIds.isEmpty()) {
                Text(
                    "Keine Complications",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 5.2.sp,
                )
            } else {
                activeComplicationIds.take(3).forEach { complicationId ->
                    Text(
                        complicationPreviewLabel(complicationId, state),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 4.9.sp,
                        maxLines = 1,
                    )
                }
            }''', 'watchface active complications')
write(p, s)

# ---------------------------------------------------------------------------
# Complication catalog: logical order, 3-column name + preview only, graph last.
# ---------------------------------------------------------------------------
p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/ComplicationCatalog.kt'
s = read(p)
s = regex(s, r'internal val SugarliciousComplicationCatalog = listOf\(.*?\n\)', '''internal val SugarliciousComplicationCatalog = listOf(
    ComplicationCatalogEntry(1, "Glukose", ComplicationCategory.GLUCOSE, "SHORT · RANGED · LONG"),
    ComplicationCatalogEntry(35, "Trend", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(36, "Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(5, "Zeit seit letztem Wert", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(16, "Basal", ComplicationCategory.THERAPY, "SHORT"),
    ComplicationCatalogEntry(11, "IOB", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(14, "COB", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(2, "Glukose + Trend", ComplicationCategory.GLUCOSE, "SHORT · RANGED"),
    ComplicationCatalogEntry(29, "Glukose + Delta", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(3, "Zeit + Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(33, "Glukose + Trend + Zeit", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(4, "Glukose + Trend + Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(32, "Glukose + Trend + Delta + Zeit", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(34, "IOB + COB + Basal", ComplicationCategory.THERAPY, "SHORT · LONG"),
    ComplicationCatalogEntry(19, "Loop Status", ComplicationCategory.THERAPY, "SHORT · ICON"),
    ComplicationCatalogEntry(22, "Pumpe / Reservoir", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(30, "Sensoralter", ComplicationCategory.GLUCOSE, "SHORT · RANGED"),
    ComplicationCatalogEntry(31, "TIR", ComplicationCategory.GLUCOSE, "SHORT · GOAL · WEIGHTED"),
    ComplicationCatalogEntry(9, "CGM Graph", ComplicationCategory.GLUCOSE, "IMAGE"),
)''', 'ordered complication catalog')
s = literal(s, 'internal fun ComplicationStudio(\n    state: TherapyDisplayState?,\n) {', 'internal fun ComplicationStudio(\n    state: TherapyDisplayState?,\n    onPresetChanged: (List<Int>) -> Unit = {},\n) {', 'studio callback signature')
s = literal(s, '                                selected = updated\n                                syncLabel = "Preset geändert · wird synchronisiert"\n', '                                selected = updated\n                                onPresetChanged(updated)\n                                syncLabel = "Preset geändert · wird synchronisiert"\n', 'studio callback')
s = regex(s, r'''@Composable\nprivate fun ComplicationCatalogTile\(.*?\n\}\n\n@Composable\nprivate fun CompactComplicationPreview''', '''@Composable
private fun ComplicationCatalogTile(
    modifier: Modifier,
    entry: ComplicationCatalogEntry,
    state: TherapyDisplayState?,
    graphHours: Int,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.aspectRatio(1f)
            .background(if (selected) SugarliciousColors.SurfaceSelected else SugarliciousColors.SurfaceHigh, shape)
            .border(1.dp, if (selected) SugarliciousColors.Primary.copy(alpha = 0.62f) else SugarliciousColors.Border.copy(alpha = 0.55f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            entry.name,
            color = SugarliciousColors.TextPrimary,
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        CompactComplicationPreview(entry, state, graphHours)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CompactComplicationPreview''', 'minimal complication tile')
s = s.replace('private fun loadComplicationPreset(context: Context): List<Int> =', 'internal fun loadComplicationPreset(context: Context): List<Int> =', 1)
s = literal(s, '    return when (id) {\n        1 -> PhonePreview(glucoseText, unitLabel(g?.displayUnit), glucoseColor)\n', '    return when (id) {\n        1 -> PhonePreview(glucoseText, unitLabel(g?.displayUnit), glucoseColor)\n        35 -> PhonePreview(trend, "")\n        36 -> PhonePreview(delta, "")\n', 'standalone preview values')
s = literal(s, 'private fun demoHistory(now: Long, hours: Int): List<GlucoseSample> {', '''internal fun complicationPreviewLabel(id: Int, state: TherapyDisplayState?): String {
    val entry = SugarliciousComplicationCatalog.firstOrNull { it.id == id }
    val preview = previewFor(id, state)
    return "${entry?.name ?: "Comp"} ${preview.primary}".take(20)
}

private fun demoHistory(now: Long, hours: Int): List<GlucoseSample> {''', 'preview label helper')
s = regex(s, r'''@Composable\nprivate fun MiniGlucosePreview\(.*?\n\}\n\nprivate data class PhonePreview''', '''@Composable
private fun MiniGlucosePreview(samples: List<GlucoseSample>, current: GlucoseSample?, hours: Int) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE) }
    val now = System.currentTimeMillis()
    val cutoff = now - hours * 60L * 60_000L
    val merged = (samples + listOfNotNull(current))
        .filter { it.measuredAtEpochMs in cutoff..(now + 5 * 60_000L) && it.valueMgDl in 20.0..1000.0 }
        .distinctBy { it.measuredAtEpochMs }
        .sortedBy { it.measuredAtEpochMs }
        .ifEmpty { demoHistory(now, hours) }
    val dotRadiusDp = preferences.getFloat("cgm.dotRadiusDp", 2.4f).coerceIn(1.5f, 6f)
    val outlineEnabled = preferences.getBoolean("cgm.dotOutlineEnabled", true)
    val outlineWidthDp = preferences.getFloat("cgm.dotOutlineWidthDp", 0.95f).coerceIn(0.25f, 3f)
    Canvas(
        Modifier.fillMaxWidth().height(52.dp)
            .background(SugarliciousColors.GraphBackground),
    ) {
        val left = 3.dp.toPx()
        val right = size.width - 3.dp.toPx()
        val top = 3.dp.toPx()
        val bottom = size.height - 3.dp.toPx()
        val values = merged.map { it.valueMgDl }
        val minimum = kotlin.math.min(40.0, values.minOrNull() ?: 40.0) - 10.0
        val maximum = kotlin.math.max(200.0, values.maxOrNull() ?: 200.0) + 10.0
        val yMin = kotlin.math.floor(minimum / 20.0) * 20.0
        val yMax = kotlin.math.ceil(maximum / 20.0) * 20.0
        fun x(timestamp: Long) = left + (((timestamp - cutoff).toDouble() / (hours * 60L * 60_000L).toDouble()).coerceIn(0.0, 1.0) * (right - left)).toFloat()
        fun y(value: Double) = bottom - (((value - yMin) / (yMax - yMin).coerceAtLeast(1.0)).coerceIn(0.0, 1.0) * (bottom - top)).toFloat()
        val low = 80.0
        val high = 160.0
        drawRect(
            color = SugarliciousColors.RangeInRange,
            topLeft = Offset(left, y(high)),
            size = androidx.compose.ui.geometry.Size(right - left, (y(low) - y(high)).coerceAtLeast(1f)),
        )
        drawLine(SugarliciousColors.GraphDivider, Offset(left, y(high)), Offset(right, y(high)), 0.7.dp.toPx())
        drawLine(SugarliciousColors.GraphDivider, Offset(left, y(low)), Offset(right, y(low)), 0.7.dp.toPx())
        merged.forEachIndexed { index, sample ->
            val radius = dotRadiusDp.dp.toPx() * if (index == merged.lastIndex) 1.25f else 1f
            val center = Offset(x(sample.measuredAtEpochMs), y(sample.valueMgDl))
            if (outlineEnabled) {
                drawCircle(SugarliciousColors.GraphCurrentOutline, radius + outlineWidthDp.dp.toPx(), center)
            }
            val dotColor = when {
                sample.valueMgDl < low -> SugarliciousColors.CgmDotLow
                sample.valueMgDl > high -> SugarliciousColors.CgmDotHigh
                else -> SugarliciousColors.CgmDotInRange
            }
            drawCircle(dotColor, radius, center)
        }
    }
}

private data class PhonePreview''', 'mobile complication graph style')
write(p, s)

# ---------------------------------------------------------------------------
# Notification graph: expanded aspect ratio remains circular; dedicated CGM style/colors.
# ---------------------------------------------------------------------------
p = 'app-mobile/src/main/res/layout/notification_sugarlicious_expanded.xml'
s = read(p).replace('android:scaleType="fitXY"', 'android:scaleType="centerCrop"')
write(p, s)

p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt'
s = read(p)
s = literal(s, '        const val PREFERENCE_NOTIFICATION_GRAPH_HOURS = "notification.graphHours"\n', '''        const val PREFERENCE_NOTIFICATION_GRAPH_HOURS = "notification.graphHours"
        const val PREFERENCE_NOTIFICATION_DOT_RADIUS = "notification.cgmDotRadiusDp"
        const val PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED = "notification.cgmDotOutlineEnabled"
        const val PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH = "notification.cgmDotOutlineWidthDp"
''', 'notification style constants')
s = literal(s, '''        val palette =
            SugarliciousColorStore.load(
                preferences,
            )

        val bounds =
''', '''        val palette =
            SugarliciousColorStore.load(
                preferences,
            )
        val notificationColorPrefix =
            if (palette.isLight) "notification.color.light." else "notification.color.dark."
        fun graphColor(role: SugarliciousColorRole): Int {
            val key = notificationColorPrefix + role.preferenceKey
            return if (preferences.contains(key)) {
                preferences.getInt(key, palette.argb(role))
            } else {
                palette.argb(role)
            }
        }

        val bounds =
''', 'notification theme color resolver')
s = regex(s, r'''        val dotRadiusDp =.*?        val outlineWidthDp =.*?                \)\n''', '''        val dotRadiusDp =
            preferences.getFloat(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS,
                preferences.getFloat("cgm.dotRadiusDp", 2.4f),
            ).coerceIn(1.5f, 6.0f)
        val outlineEnabled =
            preferences.getBoolean(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED,
                preferences.getBoolean("cgm.dotOutlineEnabled", true),
            )
        val outlineWidthDp =
            preferences.getFloat(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH,
                preferences.getFloat("cgm.dotOutlineWidthDp", 0.95f),
            ).coerceIn(0.25f, 3.0f)
''', 'notification dot style')
for role in ('GRAPH_BACKGROUND', 'RANGE_IN_RANGE', 'GRAPH_DIVIDER', 'GRAPH_CURRENT_OUTLINE', 'CGM_DOT_LOW', 'CGM_DOT_IN_RANGE', 'CGM_DOT_HIGH'):
    s = re.sub(r'palette\.argb\(\s*SugarliciousColorRole\.' + role + r',?\s*\)', f'graphColor(SugarliciousColorRole.{role})', s)
write(p, s)

p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousColorSettingsPanel.kt'
s = read(p)
panel = '''

@Composable
internal fun NotificationGraphSettingsPanel() {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val preferences = remember { context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE) }
    var revision by remember { mutableStateOf(0) }
    var editingRole by remember { mutableStateOf<SugarliciousColorRole?>(null) }
    val palette = SugarliciousColorStore.load(preferences)
    val modePrefix = if (palette.isLight) "notification.color.light." else "notification.color.dark."
    fun key(role: SugarliciousColorRole) = modePrefix + role.preferenceKey
    fun resolved(role: SugarliciousColorRole): Int =
        if (preferences.contains(key(role))) preferences.getInt(key(role), palette.argb(role)) else palette.argb(role)
    var dotRadius by remember(revision) {
        mutableFloatStateOf(preferences.getFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS, preferences.getFloat("cgm.dotRadiusDp", 2.4f)).coerceIn(1.5f, 6f))
    }
    var outlineEnabled by remember(revision) {
        mutableStateOf(preferences.getBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED, preferences.getBoolean("cgm.dotOutlineEnabled", true)))
    }
    var outlineWidth by remember(revision) {
        mutableFloatStateOf(preferences.getFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH, preferences.getFloat("cgm.dotOutlineWidthDp", 0.95f)).coerceIn(0.25f, 3f))
    }
    val roles = listOf(
        SugarliciousColorRole.CGM_DOT_LOW,
        SugarliciousColorRole.CGM_DOT_IN_RANGE,
        SugarliciousColorRole.CGM_DOT_HIGH,
        SugarliciousColorRole.GRAPH_CURRENT_OUTLINE,
        SugarliciousColorRole.RANGE_IN_RANGE,
        SugarliciousColorRole.GRAPH_BACKGROUND,
        SugarliciousColorRole.GRAPH_DIVIDER,
    )
    Column(
        Modifier.fillMaxWidth().background(SugarliciousColors.Surface, RoundedCornerShape(24.dp))
            .border(1.dp, SugarliciousColors.Border, RoundedCornerShape(24.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NOTIFICATION-GRAPH", color = SugarliciousColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("CGM-Dots & Farben", color = SugarliciousColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(if (palette.isLight) "Aktuelle Farbvariante: Hell" else "Aktuelle Farbvariante: Dunkel", color = SugarliciousColors.TextSecondary, fontSize = 9.sp)
            }
            TextButton(onClick = {
                preferences.edit().apply {
                    remove(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS)
                    remove(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED)
                    remove(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH)
                    roles.forEach { remove(key(it)) }
                }.apply()
                revision++
            }) { Text("RESET", color = SugarliciousColors.Primary, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
        GraphSettingSlider(
            "Punktgröße", "Nur die CGM-Dots in der Notification", dotRadius, 1.5f..6f,
            "${String.format(locale, "%.1f", dotRadius)} dp",
            { dotRadius = it },
            { preferences.edit().putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS, dotRadius).apply() },
        )
        GraphSettingSwitch("Kontur", "Kontur der Notification-CGM-Dots", outlineEnabled) {
            outlineEnabled = it
            preferences.edit().putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED, it).apply()
        }
        if (outlineEnabled) {
            GraphSettingSlider(
                "Konturdicke", "Nur für Notification-CGM-Dots", outlineWidth, 0.25f..3f,
                "${String.format(locale, "%.2f", outlineWidth)} dp",
                { outlineWidth = it },
                { preferences.edit().putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH, outlineWidth).apply() },
            )
        }
        roles.forEach { role ->
            ColorSettingRow(
                role = role,
                argb = resolved(role),
                isDefault = !preferences.contains(key(role)),
                onEdit = { editingRole = role },
                onReset = { preferences.edit().remove(key(role)).apply(); revision++ },
            )
        }
    }
    editingRole?.let { role ->
        ColorEditorDialog(
            role = role,
            initialArgb = resolved(role),
            onDismiss = { editingRole = null },
            onSave = { argb ->
                preferences.edit().putInt(key(role), argb).apply()
                revision++
                editingRole = null
            },
        )
    }
}
'''
if 'internal fun NotificationGraphSettingsPanel()' not in s:
    s = literal(s, '\nprivate fun toHex(argb: Int): String =', panel + '\nprivate fun toHex(argb: Int): String =', 'notification graph settings panel')
write(p, s)

p = 'app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt'
s = read(p)
s = literal(s, '        parent.addView(settingsGroupLabel("BENACHRICHTIGUNG"), fullWidth())\n        parent.addView(\n', '''        parent.addView(settingsGroupLabel("BENACHRICHTIGUNG"), fullWidth())
        val notificationGraphCustomization = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                androidx.compose.ui.platform.ComposeView(context).apply {
                    setViewCompositionStrategy(
                        androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow,
                    )
                    setContent { SugarliciousTheme { NotificationGraphSettingsPanel() } }
                },
                fullWidth(),
            )
        }
        parent.addView(
''', 'notification custom panel container')
needle = '''                    addView(
                        choiceRow(
                            "Graph-Zeitraum",
                            listOf(
                                Triple("1 h", preferences.notificationGraphHours == 1) {
                                    callbacks.setNotificationGraphHours(1)
                                },
                                Triple("2 h", preferences.notificationGraphHours == 2) {
                                    callbacks.setNotificationGraphHours(2)
                                },
                                Triple("3 h", preferences.notificationGraphHours == 3) {
                                    callbacks.setNotificationGraphHours(3)
                                },
                            ),
                        ),
                    )
'''
s = literal(s, needle, needle + '''                    addView(divider())
                    addView(
                        actionRow("CGM-Dots & Farben", "Anpassen") {
                            notificationGraphCustomization.visibility =
                                if (notificationGraphCustomization.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        },
                    )
''', 'notification graph settings action')
s = literal(s, '        parent.addView(settingsGroupLabel("UHR & WATCHFACES"), fullWidth())\n', '        parent.addView(notificationGraphCustomization, cardParams(top = 4))\n\n        parent.addView(settingsGroupLabel("UHR & WATCHFACES"), fullWidth())\n', 'notification custom panel attach')
write(p, s)

# ---------------------------------------------------------------------------
# Wear app uses same icon. Expose only desired providers and standalone Trend/Delta.
# ---------------------------------------------------------------------------
p = 'app-wear/src/main/res/layout/activity_wear.xml'
s = read(p)
s = literal(s, 'android:src="@drawable/frediabetics_logo"', 'android:src="@drawable/ic_sugarlicious_monochrome"\n                    android:tint="@color/wear_text"', 'wear icon')
write(p, s)

p = 'complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt'
s = read(p)
s = literal(s, 'enum class ProviderKind {\n    GLUCOSE,\n', 'enum class ProviderKind {\n    GLUCOSE,\n    TREND_ONLY,\n    DELTA_ONLY,\n', 'provider kinds')
s = literal(s, '''            ProviderKind.GLUCOSE ->
                glucoseText to "Glucose"

            ProviderKind.GLUCOSE_PLUS_DELTA ->
''', '''            ProviderKind.GLUCOSE ->
                glucoseText to "Glucose"

            ProviderKind.TREND_ONLY ->
                trendText.ifBlank { DASH } to "Trend"

            ProviderKind.DELTA_ONLY ->
                deltaText.ifBlank { DASH } to "Delta"

            ProviderKind.GLUCOSE_PLUS_DELTA ->
''', 'standalone provider display')
s = literal(s, '''class GlucoseComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE)

class GlucosePlusDeltaComplication :
''', '''class GlucoseComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE)
class TrendOnlyComplication :
    TherapyComplicationService(ProviderKind.TREND_ONLY)
class DeltaOnlyComplication :
    TherapyComplicationService(ProviderKind.DELTA_ONLY)

class GlucosePlusDeltaComplication :
''', 'standalone provider classes')
s = regex(s, r'''object AllProviders \{\n    val classes = listOf\(.*?\n    \)\n\}''', '''object AllProviders {
    val classes = listOf(
        GlucoseComplication::class.java,
        TrendOnlyComplication::class.java,
        DeltaOnlyComplication::class.java,
        GlucoseAgeComplication::class.java,
        BasalComplication::class.java,
        IobComplication::class.java,
        CobComplication::class.java,
        GlucoseTrendComplication::class.java,
        GlucosePlusDeltaComplication::class.java,
        GlucoseDeltaComplication::class.java,
        GlucoseTrendAgeComplication::class.java,
        GlucoseTrendDeltaComplication::class.java,
        GlucoseTrendDeltaAgeComplication::class.java,
        IobCobBasalComplication::class.java,
        LoopComplication::class.java,
        ReservoirComplication::class.java,
        SensorAgeComplication::class.java,
        TirComplication::class.java,
        GlucoseGraphComplication::class.java,
    )
}''', 'provider class list')
write(p, s)

p = 'app-wear/src/main/kotlin/app/aapswear/wear/StateDataLayerService.kt'
s = read(p).replace('.filter { it in 1..34 }', '.filter { it in 1..36 }', 1)
write(p, s)

write('app-wear/src/main/AndroidManifest.xml', '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.type.watch" />
    <uses-permission android:name="com.google.wear.permission.PUSH_WATCH_FACES" />
    <uses-permission android:name="com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE" />
    <application android:allowBackup="false" android:dataExtractionRules="@xml/data_extraction_rules" android:fullBackupContent="false" android:icon="@mipmap/ic_launcher" android:label="@string/app_name" android:roundIcon="@mipmap/ic_launcher_round" android:theme="@style/WearTheme" android:usesCleartextTraffic="false">
        <meta-data android:name="com.google.android.wearable.standalone" android:value="false" />
        <activity android:name=".WearActivity" android:exported="true" android:taskAffinity=""><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
        <service android:name=".StateDataLayerService" android:exported="true"><intent-filter><action android:name="com.google.android.gms.wearable.DATA_CHANGED"/><action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED"/><data android:scheme="wear" android:host="*" android:pathPrefix="/aaps-display/v1"/></intent-filter></service>
        <service android:name="app.aapswear.complications.GlucoseComplication" android:label="01 Glukose" android:icon="@drawable/comp_preview_01" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE,LONG_TEXT"/><meta-data android:name="com.google.android.wearable.complications.STATIC_PREVIEW_DATA" android:resource="@xml/complication_preview_01"/></service>
        <service android:name="app.aapswear.complications.TrendOnlyComplication" android:label="02 Trend" android:icon="@drawable/comp_preview_02" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT"/></service>
        <service android:name="app.aapswear.complications.DeltaOnlyComplication" android:label="03 Delta" android:icon="@drawable/comp_preview_03" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT"/></service>
        <service android:name="app.aapswear.complications.GlucoseAgeComplication" android:label="04 Zeit seit letztem Wert" android:icon="@drawable/comp_preview_05" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT"/></service>
        <service android:name="app.aapswear.complications.BasalComplication" android:label="05 Basal" android:icon="@drawable/comp_preview_16" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT"/></service>
        <service android:name="app.aapswear.complications.IobComplication" android:label="06 IOB" android:icon="@drawable/comp_preview_11" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE"/></service>
        <service android:name="app.aapswear.complications.CobComplication" android:label="07 COB" android:icon="@drawable/comp_preview_14" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE"/></service>
        <service android:name="app.aapswear.complications.GlucoseTrendComplication" android:label="08 Glukose + Trend" android:icon="@drawable/comp_preview_02" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE"/></service>
        <service android:name="app.aapswear.complications.GlucosePlusDeltaComplication" android:label="09 Glukose + Delta" android:icon="@drawable/comp_preview_04" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.GlucoseDeltaComplication" android:label="10 Zeit + Delta" android:icon="@drawable/comp_preview_03" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT"/></service>
        <service android:name="app.aapswear.complications.GlucoseTrendAgeComplication" android:label="11 Glukose + Trend + Zeit" android:icon="@drawable/comp_preview_02" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.GlucoseTrendDeltaComplication" android:label="12 Glukose + Trend + Delta" android:icon="@drawable/comp_preview_04" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT"/></service>
        <service android:name="app.aapswear.complications.GlucoseTrendDeltaAgeComplication" android:label="13 Glukose + Trend + Delta + Zeit" android:icon="@drawable/comp_preview_04" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.IobCobBasalComplication" android:label="14 IOB + COB + Basal" android:icon="@drawable/comp_preview_15" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.LoopComplication" android:label="15 Loop Status" android:icon="@drawable/comp_preview_19" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,ICON"/></service>
        <service android:name="app.aapswear.complications.ReservoirComplication" android:label="16 Pumpe / Reservoir" android:icon="@drawable/comp_preview_22" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE"/></service>
        <service android:name="app.aapswear.complications.SensorAgeComplication" android:label="17 Sensoralter" android:icon="@drawable/comp_preview_05" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE"/></service>
        <service android:name="app.aapswear.complications.TirComplication" android:label="18 TIR" android:icon="@drawable/comp_preview_08" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,GOAL_PROGRESS,WEIGHTED_ELEMENTS"/></service>
        <service android:name="app.aapswear.complications.GlucoseGraphComplication" android:label="19 CGM Graph" android:icon="@drawable/comp_preview_09" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SMALL_IMAGE,LARGE_IMAGE"/></service>
    </application>
</manifest>
''')

# The old hardening helper is no longer needed and should disappear from source-only commit.
Path('tools/fix_mobile_watch_apply_script.py').unlink(missing_ok=True)
print('ALL REQUESTED SOURCE CHANGES APPLIED')
