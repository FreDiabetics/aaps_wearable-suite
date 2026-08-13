from pathlib import Path

# Fix the generated Mobile complication preview to use the actual SugarliciousColors API.
p = Path('app-mobile/src/main/kotlin/app/aapswear/mobile/ComplicationCatalog.kt')
s = p.read_text(encoding='utf-8')
replacements = {
    'SugarliciousColors.GraphBackground': 'SugarliciousColors.color(SugarliciousColorRole.GRAPH_BACKGROUND)',
    'SugarliciousColors.RangeInRange': 'SugarliciousColors.color(SugarliciousColorRole.RANGE_IN_RANGE)',
    'SugarliciousColors.GraphDivider': 'SugarliciousColors.color(SugarliciousColorRole.GRAPH_DIVIDER)',
    'SugarliciousColors.GraphCurrentOutline': 'SugarliciousColors.color(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)',
    'SugarliciousColors.CgmDotLow': 'SugarliciousColors.color(SugarliciousColorRole.CGM_DOT_LOW)',
    'SugarliciousColors.CgmDotHigh': 'SugarliciousColors.color(SugarliciousColorRole.CGM_DOT_HIGH)',
    'SugarliciousColors.CgmDotInRange': 'SugarliciousColors.color(SugarliciousColorRole.CGM_DOT_IN_RANGE)',
}
for old, new in replacements.items():
    s = s.replace(old, new)
p.write_text(s, encoding='utf-8')

# The original integration replacement removed navigate() together with the old bottom navigation.
# Restore only screen navigation; the old bottom-navigation rendering stays removed.
p = Path('app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt')
s = p.read_text(encoding='utf-8')
if 'private fun navigate(target: DashboardScreen)' not in s:
    anchor = '''    private fun updateTopBar() {\n'''
    navigate = '''    private fun navigate(target: DashboardScreen) {\n        if (screen == target) return\n        screen = target\n        scroll.scrollTo(0, 0)\n        refresh(forceSettingsRender = true)\n    }\n\n'''
    if anchor not in s:
        raise SystemExit('MainActivity updateTopBar anchor missing')
    s = s.replace(anchor, navigate + anchor, 1)
p.write_text(s, encoding='utf-8')

print('post-fix applied')
