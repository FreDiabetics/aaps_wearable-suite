from pathlib import Path
p = Path('complications/src/test/kotlin/app/aapswear/complications/TherapyComplicationsTest.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('"+5 \u00b7 0m",', '"0m \u00b7 +5",')
p.write_text(s, encoding='utf-8')
