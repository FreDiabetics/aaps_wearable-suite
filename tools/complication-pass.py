from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(p): return (ROOT / p).read_text(encoding='utf-8')
def write(p, s):
    q = ROOT / p; q.parent.mkdir(parents=True, exist_ok=True); q.write_text(s, encoding='utf-8'); print(p)
def rep(s, old, new, label):
    if new in s: return s
    if old not in s: raise RuntimeError(label)
    return s.replace(old, new, 1)

p='complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt'
s=read(p)
if 'import android.os.Build' not in s:
    s=s.replace('import android.graphics.drawable.Icon\n','import android.graphics.drawable.Icon\nimport android.os.Build\n')
if 'GoalProgressComplicationData' not in s:
    s=s.replace('import androidx.wear.watchface.complications.data.ComplicationType\n','import androidx.wear.watchface.complications.data.ComplicationType\nimport androidx.wear.watchface.complications.data.GoalProgressComplicationData\nimport androidx.wear.watchface.complications.data.MonochromaticImage\nimport androidx.wear.watchface.complications.data.MonochromaticImageComplicationData\nimport androidx.wear.watchface.complications.data.WeightedElementsComplicationData\n')
if 'GLUCOSE_PLUS_DELTA' not in s:
    s=s.replace('    GLUCOSE,\n','    GLUCOSE,\n    GLUCOSE_PLUS_DELTA,\n    GLUCOSE_TREND_DELTA_AGE,\n    GLUCOSE_TREND_AGE,\n    SENSOR_AGE,\n    TIR,\n',1)
    s=s.replace('    IOB_COB,\n','    IOB_COB,\n    IOB_COB_BASAL,\n',1)

s=rep(s,'            ProviderKind.GLUCOSE ->\n                glucoseText to "Glucose"\n\n            ProviderKind.GLUCOSE_TREND ->','            ProviderKind.GLUCOSE ->\n                glucoseText to "Glucose"\n\n            ProviderKind.GLUCOSE_PLUS_DELTA ->\n                "$glucoseText ${deltaText.ifBlank { DASH }}" to "Glucose + Delta"\n\n            ProviderKind.GLUCOSE_TREND_DELTA_AGE ->\n                "$glucoseText$trendText ${deltaText.ifBlank { DASH }} $ageText" to "Glucose + Trend + Delta + Zeit"\n\n            ProviderKind.GLUCOSE_TREND_AGE ->\n                "$glucoseText$trendText $ageText" to "Glucose + Trend + Zeit"\n\n            ProviderKind.SENSOR_AGE ->\n                DASH to "Sensoralter"\n\n            ProviderKind.TIR ->\n                tirText(state, now) to "TIR 70–180"\n\n            ProviderKind.GLUCOSE_TREND ->','provider cases')
s=rep(s,'            ProviderKind.GLUCOSE_DELTA ->\n                "${deltaText.ifBlank { DASH }} · $ageText" to "Delta and age"\n','            ProviderKind.GLUCOSE_DELTA ->\n                "$ageText · ${deltaText.ifBlank { DASH }}" to "Zeit + Delta"\n','age delta')
s=rep(s,'            ProviderKind.GLUCOSE_TREND_DELTA ->\n                "$glucoseText$trendText" to deltaText.ifBlank { DASH }\n','            ProviderKind.GLUCOSE_TREND_DELTA ->\n                "$glucoseText$trendText ${deltaText.ifBlank { DASH }}" to "Glucose + Trend + Delta"\n','trend delta')
s=rep(s,'            ProviderKind.IOB_COB ->\n                "${units(therapyState?.insulin?.totalIob, "U", 1)} " +\n                    units(therapyState?.carbs?.cobGrams, "g", 0) to "IOB · COB"\n\n            ProviderKind.BASAL ->','            ProviderKind.IOB_COB ->\n                "${units(therapyState?.insulin?.totalIob, "U", 1)} " +\n                    units(therapyState?.carbs?.cobGrams, "g", 0) to "IOB · COB"\n\n            ProviderKind.IOB_COB_BASAL ->\n                "${units(therapyState?.insulin?.totalIob, "U", 1)} · ${units(therapyState?.carbs?.cobGrams, "g", 0)}" to\n                    "Basal ${units(therapyState?.basal?.currentUnitsPerHour, "U/h", 2)}"\n\n            ProviderKind.BASAL ->','combo')
s=rep(s,'            ProviderKind.RESERVOIR ->\n                units(therapyState?.pump?.reservoirUnits, "U", 0) to "Reservoir"\n','            ProviderKind.RESERVOIR ->\n                units(therapyState?.pump?.reservoirUnits, "U", 0) to "Pumpe ${therapyState?.pump?.status ?: DASH}"\n','reservoir')

marker='        if (\n            kind == ProviderKind.GLUCOSE_IMAGE ||\n'
if 'kind == ProviderKind.LOOP &&' not in s:
    rich='''        if (kind == ProviderKind.LOOP && type == ComplicationType.MONOCHROMATIC_IMAGE) {
            val image = MonochromaticImage.Builder(
                Icon.createWithResource(this, android.R.drawable.stat_notify_sync_noanim),
            ).build()
            return MonochromaticImageComplicationData.Builder(image, description)
                .setTapAction(tap)
                .build()
        }

        if (kind == ProviderKind.TIR && Build.VERSION.SDK_INT >= 33) {
            val stats = tirStats(state, now)
            if (type == ComplicationType.GOAL_PROGRESS) {
                return GoalProgressComplicationData.Builder(
                    stats.inRangePercent,
                    TIR_GOAL_PERCENT,
                    description,
                ).setText(PlainComplicationText.Builder(stats.text).build())
                    .setTapAction(tap)
                    .build()
            }
            if (type == ComplicationType.WEIGHTED_ELEMENTS) {
                val elements = buildList {
                    if (stats.lowPercent > 0f) add(WeightedElementsComplicationData.Element(stats.lowPercent, Color.rgb(244, 67, 54)))
                    if (stats.inRangePercent > 0f) add(WeightedElementsComplicationData.Element(stats.inRangePercent, Color.rgb(76, 175, 80)))
                    if (stats.highPercent > 0f) add(WeightedElementsComplicationData.Element(stats.highPercent, Color.rgb(255, 152, 0)))
                    if (isEmpty()) add(WeightedElementsComplicationData.Element(1f, Color.GRAY))
                }
                return WeightedElementsComplicationData.Builder(elements, description)
                    .setText(PlainComplicationText.Builder(stats.text).build())
                    .setTapAction(tap)
                    .build()
            }
        }

'''
    s=s.replace(marker,rich+marker,1)

if 'ProviderKind.IOB,\n                ProviderKind.COB,' not in s:
    s=s.replace('                ProviderKind.RESERVOIR -> {\n','''                ProviderKind.IOB,
                ProviderKind.COB,
                ProviderKind.SENSOR_AGE -> {
                    val triple = when (kind) {
                        ProviderKind.IOB -> Triple(therapyState?.insulin?.totalIob?.toFloat()?.coerceIn(0f, IOB_GAUGE_MAX) ?: 0f, 0f, IOB_GAUGE_MAX)
                        ProviderKind.COB -> Triple(therapyState?.carbs?.cobGrams?.toFloat()?.coerceIn(0f, COB_GAUGE_MAX) ?: 0f, 0f, COB_GAUGE_MAX)
                        else -> Triple(0f, 0f, SENSOR_AGE_GAUGE_MAX_DAYS)
                    }
                    return RangedValueComplicationData.Builder(triple.first, triple.second, triple.third, description)
                        .setText(PlainComplicationText.Builder(pair.first).build())
                        .setTapAction(tap)
                        .build()
                }

                ProviderKind.RESERVOIR -> {
''',1)

s=rep(s,'''        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(
                pair.first.take(24),
            ).build(),
            description,
        )
            .setTapAction(tap)
            .build()
''','''        val shortBuilder = ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(pair.first.take(24)).build(),
            description,
        ).setTapAction(tap)
        if (kind == ProviderKind.IOB_COB_BASAL || kind == ProviderKind.RESERVOIR) {
            shortBuilder.setTitle(PlainComplicationText.Builder(pair.second.take(24)).build())
        }
        return shortBuilder.build()
''','short builder')
s=rep(s,'        val windowMs =\n            if (kind == ProviderKind.GRAPH_LARGE) GRAPH_LARGE_WINDOW_MS\n            else GRAPH_WINDOW_MS\n','        val windowMs =\n            if (kind == ProviderKind.GRAPH_LARGE) GRAPH_LARGE_WINDOW_MS\n            else readComplicationGraphHours() * 60L * 60_000L\n','graph hours')
if 'private fun readComplicationGraphHours()' not in s:
    s=s.replace('    private fun readGraphColors(): WatchGraphColors {\n','''    private fun readComplicationGraphHours(): Int =
        getSharedPreferences("watch_display", Context.MODE_PRIVATE)
            .getInt("complication_graph_hours", 3)
            .takeIf { it in listOf(1, 2, 6, 12, 24) }
            ?: 3

    private fun readGraphColors(): WatchGraphColors {
''',1)
if 'private data class TirStats' not in s:
    s=s.replace('    private fun compactTherapyStatus(\n','''    private data class TirStats(
        val lowPercent: Float,
        val inRangePercent: Float,
        val highPercent: Float,
        val hasData: Boolean,
    ) {
        val text: String get() = if (hasData) "${inRangePercent.toInt()}%" else DASH
    }

    private fun tirStats(state: TherapyDisplayState?, now: Long): TirStats {
        val samples = state?.glucoseHistory.orEmpty().filter {
            it.measuredAtEpochMs in (now - TIR_WINDOW_MS)..(now + FUTURE_TOLERANCE_MS)
        }
        if (samples.isEmpty()) return TirStats(0f, 0f, 0f, false)
        val total = samples.size.toFloat()
        val low = samples.count { it.valueMgDl < TIR_LOW_MGDL } * 100f / total
        val high = samples.count { it.valueMgDl > TIR_HIGH_MGDL } * 100f / total
        return TirStats(low, (100f - low - high).coerceIn(0f, 100f), high, true)
    }

    private fun tirText(state: TherapyDisplayState?, now: Long): String = tirStats(state, now).text

    private fun compactTherapyStatus(
''',1)
if 'TIR_WINDOW_MS' not in s:
    s=s.replace('        private const val GLUCOSE_GAUGE_MAX = 260f\n\n','''        private const val GLUCOSE_GAUGE_MAX = 260f
        private const val IOB_GAUGE_MAX = 10f
        private const val COB_GAUGE_MAX = 150f
        private const val SENSOR_AGE_GAUGE_MAX_DAYS = 14f
        private const val TIR_LOW_MGDL = 70.0
        private const val TIR_HIGH_MGDL = 180.0
        private const val TIR_GOAL_PERCENT = 70f
        private const val TIR_WINDOW_MS = 24 * 60 * 60_000L

''',1)
if 'class GlucosePlusDeltaComplication' not in s:
    s=s.replace('class GlucoseTrendComplication :\n','''class GlucosePlusDeltaComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_PLUS_DELTA)
class GlucoseTrendDeltaAgeComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_TREND_DELTA_AGE)
class GlucoseTrendAgeComplication :
    TherapyComplicationService(ProviderKind.GLUCOSE_TREND_AGE)
class SensorAgeComplication :
    TherapyComplicationService(ProviderKind.SENSOR_AGE)
class TirComplication :
    TherapyComplicationService(ProviderKind.TIR)

class GlucoseTrendComplication :
''',1)
if 'class IobCobBasalComplication' not in s:
    s=s.replace('class BasalComplication :\n','''class IobCobBasalComplication :
    TherapyComplicationService(ProviderKind.IOB_COB_BASAL)

class BasalComplication :
''',1)
if 'GlucosePlusDeltaComplication::class.java' not in s:
    s=s.replace('        GlucoseComplication::class.java,\n','''        GlucoseComplication::class.java,
        GlucosePlusDeltaComplication::class.java,
        GlucoseTrendDeltaAgeComplication::class.java,
        GlucoseTrendAgeComplication::class.java,
        SensorAgeComplication::class.java,
        TirComplication::class.java,
''',1)
    s=s.replace('        IobCobComplication::class.java,\n','        IobCobComplication::class.java,\n        IobCobBasalComplication::class.java,\n',1)
write(p,s)

# Wear Data Layer: receive complication graph hours.
p='app-wear/src/main/kotlin/app/aapswear/wear/StateDataLayerService.kt'; s=read(p)
old='''    private fun persistComplicationPreset(event: DataEvent) {
        val ids =
            runCatching {
                DataMapItem
                    .fromDataItem(event.dataItem)
                    .dataMap
                    .getIntegerArrayList("ids")
            }
                .getOrNull()
                .orEmpty()
                .filter { it in 1..28 }
                .distinct()
                .take(MAX_PRESET_ITEMS)

        getSharedPreferences(
            COMPLICATION_SETUP_PREFS,
            Context.MODE_PRIVATE,
        )
            .edit()
            .putString(
                COMPLICATION_PRESET_KEY,
                ids.joinToString(","),
            )
            .apply()
    }
'''
new='''    private fun persistComplicationPreset(event: DataEvent) {
        val dataMap = runCatching {
            DataMapItem.fromDataItem(event.dataItem).dataMap
        }.getOrNull() ?: return
        val ids = dataMap.getIntegerArrayList("ids").orEmpty()
            .filter { it in 1..34 }
            .distinct()
            .take(MAX_PRESET_ITEMS)
        val graphHours = dataMap.getInt("graphHours", 3)
            .takeIf { it in listOf(1, 2, 6, 12, 24) }
            ?: 3
        getSharedPreferences(COMPLICATION_SETUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(COMPLICATION_PRESET_KEY, ids.joinToString(","))
            .putInt(COMPLICATION_GRAPH_HOURS_KEY, graphHours)
            .apply()
        getSharedPreferences(WearDisplayPreferences.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("complication_graph_hours", graphHours)
            .apply()
        requestAllComplicationUpdates()
    }
'''
s=rep(s,old,new,'persist preset')
if 'COMPLICATION_GRAPH_HOURS_KEY' not in s:
    s=s.replace('        private const val COMPLICATION_PRESET_KEY =\n            "selected_ids"\n','        private const val COMPLICATION_PRESET_KEY =\n            "selected_ids"\n        private const val COMPLICATION_GRAPH_HOURS_KEY =\n            "graph_hours"\n',1)
write(p,s)

print('complication core pass done')
