from pathlib import Path

# Therapy complication constants.
p = Path('complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt')
s = p.read_text(encoding='utf-8')
if 'private const val TIR_WINDOW_MS' not in s:
    old = '        private const val GLUCOSE_GAUGE_MAX = 260f\n\n'
    new = '''        private const val GLUCOSE_GAUGE_MAX = 260f
        private const val IOB_GAUGE_MAX = 10f
        private const val COB_GAUGE_MAX = 150f
        private const val SENSOR_AGE_GAUGE_MAX_DAYS = 14f
        private const val TIR_LOW_MGDL = 70.0
        private const val TIR_HIGH_MGDL = 180.0
        private const val TIR_GOAL_PERCENT = 70f
        private const val TIR_WINDOW_MS = 24 * 60 * 60_000L

'''
    if old not in s:
        raise SystemExit('missing constants insertion point')
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Wear preset key used by the generated graph-hour persistence code.
p = Path('app-wear/src/main/kotlin/app/aapswear/wear/StateDataLayerService.kt')
s = p.read_text(encoding='utf-8')
if 'private const val COMPLICATION_GRAPH_HOURS_KEY' not in s:
    old = '''        private const val COMPLICATION_PRESET_KEY =
            "selected_ids"
'''
    new = '''        private const val COMPLICATION_PRESET_KEY =
            "selected_ids"
        private const val COMPLICATION_GRAPH_HOURS_KEY =
            "graph_hours"
'''
    if old not in s:
        raise SystemExit('missing complication preset constant insertion point')
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
