from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, got {count}")
    return text.replace(old, new, 1)


# DashboardViews.kt: preferences, callbacks, UI sliders, native slider helper.
p = Path("app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt")
text = p.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import android.widget.LinearLayout\nimport android.widget.Switch\nimport android.widget.TextView\n",
    "import android.widget.LinearLayout\nimport android.widget.SeekBar\nimport android.widget.Switch\nimport android.widget.TextView\n",
    "SeekBar import",
)
text = replace_once(
    text,
    "import app.aapswear.model.TherapyDisplayState\n",
    "import app.aapswear.model.TherapyDisplayState\nimport java.util.Locale\n",
    "Locale import",
)
text = replace_once(
    text,
    "    val cgmDotOutlineWidthDp: Float = 0.95f,\n    val compact: Boolean = true,\n",
    "    val cgmDotOutlineWidthDp: Float = 0.95f,\n    val predictionDotRadiusDp: Float = 1.75f,\n    val predictionDotOutlineWidthDp: Float = 0.70f,\n    val compact: Boolean = true,\n",
    "prediction preference fields",
)
text = replace_once(
    text,
    "                cgmDotOutlineWidthDp =\n                    preferences\n                        .getFloat(\"cgm.dotOutlineWidthDp\", 0.95f)\n                        .coerceIn(0.25f, 3.0f),\n                compact =\n",
    "                cgmDotOutlineWidthDp =\n                    preferences\n                        .getFloat(\"cgm.dotOutlineWidthDp\", 0.95f)\n                        .coerceIn(0.25f, 3.0f),\n                predictionDotRadiusDp =\n                    preferences\n                        .getFloat(\"cgm.prediction.dotRadiusDp\", 1.75f)\n                        .coerceIn(1.0f, 6.0f),\n                predictionDotOutlineWidthDp =\n                    preferences\n                        .getFloat(\"cgm.prediction.dotOutlineWidthDp\", 0.70f)\n                        .coerceIn(0.0f, 3.0f),\n                compact =\n",
    "prediction preference read",
)
text = replace_once(
    text,
    "    val setCgmStream: (String, Boolean) -> Unit = { _, _ -> },\n    val setShowMetabolicGraph: (Boolean) -> Unit,\n",
    "    val setCgmStream: (String, Boolean) -> Unit = { _, _ -> },\n    val setCgmFloat: (String, Float) -> Unit = { _, _ -> },\n    val setShowMetabolicGraph: (Boolean) -> Unit,\n",
    "float callback",
)
text = replace_once(
    text,
    "                        addView(\n                            switchRowCompact(\n                                \"ZeroTemp-Prognose\",\n                                preferences.showCgmPredictionZeroTemp,\n                                View.generateViewId(),\n                            ) { callbacks.setCgmStream(\"cgm.prediction.zeroTemp\", it) },\n                        )\n",
    "                        addView(\n                            switchRowCompact(\n                                \"ZeroTemp-Prognose\",\n                                preferences.showCgmPredictionZeroTemp,\n                                View.generateViewId(),\n                            ) { callbacks.setCgmStream(\"cgm.prediction.zeroTemp\", it) },\n                        )\n                        addView(divider())\n                        addView(\n                            sliderRowCompact(\n                                title = \"Prediction-Punktgröße\",\n                                description = \"Größe der Vorhersagepunkte im CGM-Graph\",\n                                value = preferences.predictionDotRadiusDp,\n                                minimum = 1.0f,\n                                maximum = 6.0f,\n                                decimals = 1,\n                            ) { callbacks.setCgmFloat(\"cgm.prediction.dotRadiusDp\", it) },\n                        )\n                        addView(divider())\n                        addView(\n                            sliderRowCompact(\n                                title = \"Prediction-Konturdicke\",\n                                description = \"0,00 dp blendet die Kontur aus\",\n                                value = preferences.predictionDotOutlineWidthDp,\n                                minimum = 0.0f,\n                                maximum = 3.0f,\n                                decimals = 2,\n                            ) { callbacks.setCgmFloat(\"cgm.prediction.dotOutlineWidthDp\", it) },\n                        )\n",
    "prediction sliders",
)
marker = "    private fun chipRow(\n"
helper = """    private fun sliderRowCompact(
        title: String,
        description: String,
        value: Float,
        minimum: Float,
        maximum: Float,
        decimals: Int,
        callback: (Float) -> Unit,
    ) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = 72.dp
            setPadding(0, 7.dp, 0, 7.dp)

            val valueLabel =
                TextView(context).apply {
                    textSize = 12f
                    gravity = Gravity.END
                    setTextColor(accent)
                    applyChromaticOutline(accent)
                }

            val titleRow =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(value(title, text, 15f, 1))
                            addView(helper(description, 1))
                        },
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ),
                    )
                    addView(
                        valueLabel,
                        LinearLayout.LayoutParams(
                            72.dp,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            addView(titleRow)

            val steps = 1000
            fun progressToValue(progress: Int): Float =
                minimum + (maximum - minimum) * progress.toFloat() / steps.toFloat()
            fun valueToProgress(current: Float): Int =
                (((current.coerceIn(minimum, maximum) - minimum) / (maximum - minimum)) * steps)
                    .toInt()
                    .coerceIn(0, steps)
            fun format(current: Float): String =
                String.format(Locale.getDefault(), \"%.${decimals}f dp\", current)

            valueLabel.text = format(value)
            addView(
                SeekBar(context).apply {
                    max = steps
                    progress = valueToProgress(value)
                    progressTintList = ColorStateList.valueOf(accent)
                    thumbTintList = ColorStateList.valueOf(accent)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            private var currentValue = value.coerceIn(minimum, maximum)

                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                currentValue = progressToValue(progress)
                                valueLabel.text = format(currentValue)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                                callback(currentValue)
                            }
                        },
                    )
                },
                fullWidth(),
            )
        }

"""
text = replace_once(text, marker, helper + marker, "slider helper")
p.write_text(text, encoding="utf-8")

# MainActivity.kt: persist float settings through normal preference refresh.
p = Path("app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt")
text = p.read_text(encoding="utf-8")
text = replace_once(
    text,
    "            setCgmStream = { key, enabled ->\n                uiPreferences.edit {\n                    putBoolean(\n                        key,\n                        enabled,\n                    )\n                }\n            },\n            setShowMetabolicGraph =",
    "            setCgmStream = { key, enabled ->\n                uiPreferences.edit {\n                    putBoolean(\n                        key,\n                        enabled,\n                    )\n                }\n            },\n            setCgmFloat = { key, value ->\n                uiPreferences.edit {\n                    putFloat(\n                        key,\n                        value,\n                    )\n                }\n            },\n            setShowMetabolicGraph =",
    "float callback persistence",
)
p.write_text(text, encoding="utf-8")

# Overview: pass prediction styling into chart.
p = Path("app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt")
text = p.read_text(encoding="utf-8")
text = replace_once(
    text,
    "                cgmDotOutlineWidthDp =\n                    preferences.cgmDotOutlineWidthDp,\n            )\n",
    "                cgmDotOutlineWidthDp =\n                    preferences.cgmDotOutlineWidthDp,\n                predictionDotRadiusDp =\n                    preferences.predictionDotRadiusDp,\n                predictionDotOutlineWidthDp =\n                    preferences.predictionDotOutlineWidthDp,\n            )\n",
    "overview chart binding",
)
p.write_text(text, encoding="utf-8")

# DashboardCharts.kt: replace hard-coded prediction radii with configurable settings.
p = Path("app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt")
text = p.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    private var cgmDotOutlineWidthDp = 0.95f\n\n    fun bind(\n",
    "    private var cgmDotOutlineWidthDp = 0.95f\n    private var predictionDotRadiusDp = 1.75f\n    private var predictionDotOutlineWidthDp = 0.70f\n\n    fun bind(\n",
    "prediction chart fields",
)
text = replace_once(
    text,
    "        cgmDotOutlineWidthDp: Float = 0.95f,\n    ) {\n",
    "        cgmDotOutlineWidthDp: Float = 0.95f,\n        predictionDotRadiusDp: Float = 1.75f,\n        predictionDotOutlineWidthDp: Float = 0.70f,\n    ) {\n",
    "prediction bind params",
)
text = replace_once(
    text,
    "        this.cgmDotOutlineWidthDp =\n            cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f)\n\n        if (\n",
    "        this.cgmDotOutlineWidthDp =\n            cgmDotOutlineWidthDp.coerceIn(0.25f, 3.0f)\n        this.predictionDotRadiusDp =\n            predictionDotRadiusDp.coerceIn(1.0f, 6.0f)\n        this.predictionDotOutlineWidthDp =\n            predictionDotOutlineWidthDp.coerceIn(0.0f, 3.0f)\n\n        if (\n",
    "prediction bind assignment",
)
text = replace_once(
    text,
    "            fillPaint.color = withAlpha(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE), 190)\n            drawChromaticCircle(canvas, x, y, 2.45f.dp, fillPaint)\n            fillPaint.color = color\n            drawChromaticCircle(canvas, x, y, 1.75f.dp, fillPaint)\n",
    "            val dotRadius = predictionDotRadiusDp.dp\n            fillPaint.color = color\n            drawChromaticCircle(canvas, x, y, dotRadius, fillPaint)\n            if (predictionDotOutlineWidthDp > 0f) {\n                val outlineWidth = predictionDotOutlineWidthDp.dp\n                dotOutlinePaint.color =\n                    withAlpha(SugarliciousColors.argb(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE), 190)\n                dotOutlinePaint.strokeWidth = outlineWidth\n                drawChromaticCircle(\n                    canvas,\n                    x,\n                    y,\n                    dotRadius + outlineWidth / 2f,\n                    dotOutlinePaint,\n                )\n            }\n",
    "prediction drawing",
)
p.write_text(text, encoding="utf-8")

for path in [
    Path("app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt"),
    Path("app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt"),
    Path("app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt"),
    Path("app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt"),
]:
    value = path.read_text(encoding="utf-8")
    path.write_text("\n".join(line.rstrip() for line in value.splitlines()) + "\n", encoding="utf-8")
