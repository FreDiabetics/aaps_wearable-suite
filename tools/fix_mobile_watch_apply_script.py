from pathlib import Path

p = Path('tools/apply_mobile_watch_ui_polish.py')
s = p.read_text(encoding='utf-8')
old = "s = replace_once(s, old_bottom_style, new_top_style, 'runtime top bar styling')"
new = """s = regex_once(\n    s,\n    r'''        findViewBy<View>\\(R\\.id\\.bottom_navigation\\)\\.background =\\s*GradientDrawable\\(\\)\\.apply \\{.*?\\n            \\}\\nstyleTitle\\(\\)\\n''',\n    new_top_style,\n    'runtime top bar styling',\n)"""
if old not in s:
    raise SystemExit('runtime style patch anchor missing')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('integration script hardened')
