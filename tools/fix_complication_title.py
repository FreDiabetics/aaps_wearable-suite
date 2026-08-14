from pathlib import Path

path = Path("complications/src/main/kotlin/app/aapswear/complications/TherapyComplications.kt")
text = path.read_text()
old = """                    if (presentation?.title != null) {
                        builder.setTitle(
                            PlainComplicationText.Builder(presentation.title).build(),
                        )
                    }
"""
new = """                    presentation?.title?.let { title ->
                        builder.setTitle(
                            PlainComplicationText.Builder(title).build(),
                        )
                    }
"""
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one smart-cast block, found {count}")
path.write_text(text.replace(old, new))
