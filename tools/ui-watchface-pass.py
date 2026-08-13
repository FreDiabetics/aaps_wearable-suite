from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8'); print(p)
def rep(s,old,new,label):
    if new in s: return s
    if old not in s: raise RuntimeError(label)
    return s.replace(old,new,1)

# Mobile complication catalog.
p='app-mobile/src/main/kotlin/app/aapswear/mobile/ComplicationCatalog.kt'; s=read(p)
if 'import androidx.compose.foundation.layout.aspectRatio' not in s:
    s=s.replace('import androidx.compose.foundation.layout.Arrangement\n','import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.aspectRatio\n')
pat=r'internal val SugarliciousComplicationCatalog = listOf\(.*?\n\)\n\n@Composable'
new='''internal val SugarliciousComplicationCatalog = listOf(
    ComplicationCatalogEntry(1, "Glukose", ComplicationCategory.GLUCOSE, "SHORT · RANGED · LONG"),
    ComplicationCatalogEntry(29, "Glukose + Delta", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(11, "IOB", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(14, "COB", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(16, "Basal", ComplicationCategory.THERAPY, "SHORT"),
    ComplicationCatalogEntry(19, "Loop Status", ComplicationCategory.THERAPY, "SHORT · ICON"),
    ComplicationCatalogEntry(22, "Pumpe / Reservoir", ComplicationCategory.THERAPY, "SHORT · RANGED"),
    ComplicationCatalogEntry(30, "Sensoralter", ComplicationCategory.GLUCOSE, "SHORT · RANGED"),
    ComplicationCatalogEntry(31, "TIR", ComplicationCategory.GLUCOSE, "SHORT · GOAL · WEIGHTED"),
    ComplicationCatalogEntry(9, "CGM Graph", ComplicationCategory.GLUCOSE, "IMAGE"),
    ComplicationCatalogEntry(3, "Zeit + Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(32, "Glukose + Trend + Delta + Zeit", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(33, "Glukose + Trend + Zeit", ComplicationCategory.GLUCOSE, "SHORT · LONG"),
    ComplicationCatalogEntry(4, "Glukose + Trend + Delta", ComplicationCategory.GLUCOSE, "SHORT"),
    ComplicationCatalogEntry(34, "IOB + COB + Basal", ComplicationCategory.THERAPY, "SHORT · LONG"),
)

@Composable'''
s,c=re.subn(pat,new,s,count=1,flags=re.S)
if c!=1: raise RuntimeError('catalog list')
s=s.replace('    var expandedCategory by remember { mutableStateOf(ComplicationCategory.GLUCOSE) }\n','    var graphHours by remember { mutableStateOf(loadComplicationGraphHours(context)) }\n',1)
s=s.replace('syncComplicationPreset(context, selected)','syncComplicationPreset(context, selected, graphHours)')
s=s.replace('syncComplicationPreset(context, updated)','syncComplicationPreset(context, updated, graphHours)')
accordion=r'        ComplicationCategory\.entries\.forEach \{ category ->.*?        \}\n    \}\n\}\n\n@Composable\nprivate fun PresetStrip'
repl='''        Text(
            "CGM-GRAPH ZEITRAUM",
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(1, 2, 6, 12, 24).forEach { hours ->
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        graphHours = hours
                        saveComplicationGraphHours(context, hours)
                        scope.launch {
                            runCatching { syncComplicationPreset(context, selected, hours) }
                                .onSuccess { syncLabel = "Graph auf ${hours} h synchronisiert" }
                                .onFailure { syncLabel = "Graph lokal auf ${hours} h gesetzt" }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (graphHours == hours) SugarliciousColors.SurfaceSelected else SugarliciousColors.SurfaceHigh,
                ) {
                    Text(
                        "${hours}h",
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = if (graphHours == hours) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        SugarliciousComplicationCatalog.chunked(3).forEach { entries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                entries.forEach { entry ->
                    ComplicationCatalogTile(
                        modifier = Modifier.weight(1f),
                        entry = entry,
                        state = state,
                        graphHours = graphHours,
                        selected = entry.id in selected,
                        onToggle = {
                            val updated = togglePresetEntry(context, selected, entry.id)
                            if (updated == selected && entry.id !in selected && selected.size >= 4) {
                                Toast.makeText(context, "Das Watch-Preset hat maximal 4 Plätze.", Toast.LENGTH_SHORT).show()
                            } else {
                                selected = updated
                                syncLabel = "Preset geändert · wird synchronisiert"
                                scope.launch {
                                    runCatching { syncComplicationPreset(context, updated, graphHours) }
                                        .onSuccess { syncLabel = "Preset an Watch synchronisiert" }
                                        .onFailure { syncLabel = "Lokal gespeichert · Watch-Sync ausstehend" }
                                }
                            }
                        },
                    )
                }
                repeat(3 - entries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetStrip'''
s,c=re.subn(accordion,repl,s,count=1,flags=re.S)
if c!=1: raise RuntimeError('catalog grid')
if 'private fun ComplicationCatalogTile(' not in s:
    marker='@Composable\nprivate fun ComplicationCatalogRow(\n'
    tile='''@Composable
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
        modifier = modifier
            .aspectRatio(0.92f)
            .background(if (selected) SugarliciousColors.SurfaceSelected else SugarliciousColors.SurfaceHigh, shape)
            .border(1.dp, if (selected) SugarliciousColors.Primary.copy(alpha = 0.55f) else SugarliciousColors.Border.copy(alpha = 0.55f), shape)
            .clickable(onClick = onToggle)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(entry.name, color = SugarliciousColors.TextPrimary, fontSize = 8.5.sp, lineHeight = 10.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(entry.types, color = SugarliciousColors.TextSecondary, fontSize = 6.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        CompactComplicationPreview(entry, state, graphHours)
        Text(if (selected) "✓ PRESET" else "+ PRESET",
            color = if (selected) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
            fontSize = 6.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CompactComplicationPreview(
    entry: ComplicationCatalogEntry,
    state: TherapyDisplayState?,
    graphHours: Int,
) {
    if (entry.id == 9) {
        MiniGlucosePreview(
            samples = state?.glucoseHistory.orEmpty(),
            current = state?.glucose?.let { GlucoseSample(it.valueMgDl, it.measuredAtEpochMs) },
            hours = graphHours,
        )
        return
    }
    val preview = previewFor(entry.id, state)
    Text(preview.primary, color = preview.color, fontSize = 10.sp, lineHeight = 11.sp,
        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (preview.secondary.isNotBlank()) {
        Text(preview.secondary, color = SugarliciousColors.TextSecondary, fontSize = 6.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

'''
    if marker not in s: raise RuntimeError('tile marker')
    s=s.replace(marker,tile+marker,1)

s=rep(s,'        28 -> PhonePreview("$glucoseText$trend", "Text", glucoseColor)\n        3 -> PhonePreview("$delta · $age", "")\n','''        28 -> PhonePreview("$glucoseText$trend", "Text", glucoseColor)
        29 -> PhonePreview("$glucoseText $delta", "", glucoseColor)
        30 -> PhonePreview("—", "Sensoralter")
        31 -> {
            val samples = state?.glucoseHistory.orEmpty().filter { it.measuredAtEpochMs >= now - 24L * 60L * 60_000L }
            val tir = if (samples.isEmpty()) 100 else samples.count { it.valueMgDl in 70.0..180.0 } * 100 / samples.size
            PhonePreview("${tir}%", "TIR 70–180")
        }
        32 -> PhonePreview("$glucoseText$trend $delta $age", "", glucoseColor)
        33 -> PhonePreview("$glucoseText$trend $age", "", glucoseColor)
        34 -> PhonePreview(
            "${number(state?.insulin?.totalIob, 1, "1.2")}U · ${number(state?.carbs?.cobGrams, 0, "15")}g",
            "Basal ${number(state?.basal?.currentUnitsPerHour, 2, "0.80")}U/h",
        )
        3 -> PhonePreview("$age · $delta", "")
''','previews')
if 'COMPLICATION_GRAPH_HOURS_KEY' not in s:
    s=s.replace('private const val PRESET_KEY = "selected_ids"\n','private const val PRESET_KEY = "selected_ids"\nprivate const val COMPLICATION_GRAPH_HOURS_KEY = "graph_hours"\n',1)
    s=s.replace('private fun togglePresetEntry(\n','''private fun loadComplicationGraphHours(context: Context): Int =
    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE)
        .getInt(COMPLICATION_GRAPH_HOURS_KEY, 3)
        .takeIf { it in listOf(1, 2, 6, 12, 24) } ?: 3

private fun saveComplicationGraphHours(context: Context, hours: Int) {
    context.getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE).edit {
        putInt(COMPLICATION_GRAPH_HOURS_KEY, hours.takeIf { it in listOf(1, 2, 6, 12, 24) } ?: 3)
    }
}

private fun togglePresetEntry(
''',1)
s=s.replace('''internal suspend fun syncComplicationPreset(
    context: Context,
    ids: List<Int>,
) {
''','''internal suspend fun syncComplicationPreset(
    context: Context,
    ids: List<Int>,
    graphHours: Int = loadComplicationGraphHours(context),
) {
''',1)
s=s.replace('        dataMap.putIntegerArrayList("ids", ArrayList(ids))\n        dataMap.putLong("updatedAt", System.currentTimeMillis())\n','        dataMap.putIntegerArrayList("ids", ArrayList(ids))\n        dataMap.putInt("graphHours", graphHours.takeIf { it in listOf(1, 2, 6, 12, 24) } ?: 3)\n        dataMap.putLong("updatedAt", System.currentTimeMillis())\n',1)
write(p,s)

# Round-safe Wear app layout.
p='app-wear/src/main/res/layout/activity_wear.xml'; s=read(p)
s=rep(s,'android:paddingStart="20dp"','android:paddingStart="28dp"','start')
s=rep(s,'android:paddingTop="8dp"','android:paddingTop="14dp"','top')
s=rep(s,'android:paddingEnd="20dp"','android:paddingEnd="28dp"','end')
s=rep(s,'android:paddingBottom="24dp"','android:paddingBottom="32dp"','bottom')
s=rep(s,'android:layout_height="36dp"','android:layout_height="42dp"','header')
s=rep(s,'android:layout_width="30dp"\n                    android:layout_height="30dp"','android:layout_width="26dp"\n                    android:layout_height="26dp"','logo')
s=rep(s,'android:textSize="14sp"','android:textSize="12sp"','title')
write(p,s)

# WFF native personalization.
faces=['sugarlicious-analog','sugarlicious-orbit','sugarlicious-rings','sugarlicious-graph']
config='''    <UserConfigurations>
        <ColorConfiguration id="materialAccent" displayName="material_color_label" defaultValue="0">
            <ColorOption id="0" displayName="material_green" colors="#FF4CAF50" />
            <ColorOption id="1" displayName="material_blue" colors="#FF2196F3" />
            <ColorOption id="2" displayName="material_purple" colors="#FF6750A4" />
            <ColorOption id="3" displayName="material_teal" colors="#FF009688" />
            <ColorOption id="4" displayName="material_orange" colors="#FFFF9800" />
            <ColorOption id="5" displayName="material_red" colors="#FFF44336" />
        </ColorConfiguration>
        <ListConfiguration id="handStyle" displayName="hand_style_label" defaultValue="0">
            <ListOption id="0" displayName="hand_style_standard" />
            <ListOption id="1" displayName="hand_style_classic" />
            <ListOption id="2" displayName="hand_style_minimal" />
        </ListConfiguration>
    </UserConfigurations>
'''
strings='''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="material_color_label">Material-Farbe</string>
    <string name="material_green">Material Grün</string>
    <string name="material_blue">Material Blau</string>
    <string name="material_purple">Material Violett</string>
    <string name="material_teal">Material Türkis</string>
    <string name="material_orange">Material Orange</string>
    <string name="material_red">Material Rot</string>
    <string name="hand_style_label">Zeiger</string>
    <string name="hand_style_standard">Standard</string>
    <string name="hand_style_classic">Klassisch · Platzhalter</string>
    <string name="hand_style_minimal">Minimal · Platzhalter</string>
</resources>
'''
for face in faces:
    q=f'watchfaces/{face}/src/main/res/raw/watchface.xml'; w=read(q)
    if '<UserConfigurations>' not in w: w=w.replace('    <Scene',config+'    <Scene',1)
    for hand in ('HourHand','MinuteHand','SecondHand'):
        w=re.sub(rf'(<{hand}\\b(?![^>]*tintColor=)[^>]*)(>)',rf'\\1 tintColor="[CONFIGURATION.materialAccent]"\\2',w)
    write(q,w)
    write(f'watchfaces/{face}/src/main/res/values/strings.xml',strings)
write(p,s)
print('ui/watchface pass done')
