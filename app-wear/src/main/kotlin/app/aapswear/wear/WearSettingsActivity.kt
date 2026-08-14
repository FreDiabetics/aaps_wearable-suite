package app.aapswear.wear

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import app.aapswear.protocol.WatchGlucoseUnit
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.protocol.WatchGraphStyle
import app.aapswear.protocol.WatchUiColors
import kotlin.math.roundToInt

class WearSettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var scrollView: ScrollView
    private var current = WearDisplayPreferences()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WearDisplayPreferences.read(this)
        buildUi()
    }

    private fun buildUi() {
        val restoreScrollY =
            if (::scrollView.isInitialized) scrollView.scrollY else 0
        current = WearDisplayPreferences.read(this)
        val ui = current.uiColors
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(ui.background)
        }
        scrollView = scroll
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26.dp, 16.dp, 26.dp, 30.dp)
        }
        scroll.addView(
            root,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                Button(this@WearSettingsActivity).apply {
                    text = "‹"
                    textSize = 20f
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(6.dp, 0, 6.dp, 0)
                    setTextColor(ui.textPrimary)
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setOnClickListener { finish() }
                },
                LinearLayout.LayoutParams(36.dp, 36.dp),
            )
            addView(
                TextView(this@WearSettingsActivity).apply {
                    text = "Watch Einstellungen"
                    textSize = 13f
                    setTextColor(ui.textPrimary)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        root.addView(header, fullWidth())

        section("ZEITSKALA")
        val hoursRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        WearDisplayPreferences.allowedGraphHours.forEach { hours ->
            hoursRow.addView(
                Button(this).apply {
                    text = "${hours}h"
                    textSize = 9f
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(1.dp, 0, 1.dp, 0)
                    setTextColor(
                        if (current.graphHours == hours) ui.background else ui.textPrimary,
                    )
                    backgroundTintList =
                        ColorStateList.valueOf(
                            if (current.graphHours == hours) ui.accent else ui.tileBackground,
                        )
                    setOnClickListener {
                        save(current.copy(graphHours = hours))
                    }
                },
                LinearLayout.LayoutParams(0, 34.dp, 1f).apply {
                    marginStart = 1.dp
                    marginEnd = 1.dp
                },
            )
        }
        root.addView(hoursRow, fullWidth())

        root.addView(
            switchRow(
                "Prognosen anzeigen",
                current.showPredictions,
            ) { save(current.copy(showPredictions = it)) },
            cardParams(),
        )
        root.addView(
            switchRow(
                "IOB / COB / Basal anzeigen",
                current.showTherapyStats,
            ) { save(current.copy(showTherapyStats = it)) },
            cardParams(),
        )

        section("GLUKOSE")
        root.addView(
            choiceRow(
                listOf(
                    "Auto" to WatchGlucoseUnit.AAPS,
                    "mg/dL" to WatchGlucoseUnit.MG_DL,
                    "mmol/L" to WatchGlucoseUnit.MMOL_L,
                ),
                current.glucoseUnit,
            ) { save(current.copy(glucoseUnit = it)) },
            cardParams(),
        )

        section("CGM DOTS")
        root.addView(
            sliderCard(
                title = "Punktgröße",
                min = 15,
                max = 60,
                progress = (current.graphStyle.cgmDotRadiusDp * 10f).roundToInt(),
                value = { String.format("%.1f dp", it / 10f) },
            ) { progress ->
                save(
                    current.copy(
                        graphStyle =
                            current.graphStyle.copy(
                                cgmDotRadiusDp = progress / 10f,
                            ),
                    ),
                    rebuild = false,
                )
            },
            cardParams(),
        )
        root.addView(
            switchRow(
                "Kontur",
                current.graphStyle.cgmDotOutlineEnabled,
            ) {
                save(
                    current.copy(
                        graphStyle = current.graphStyle.copy(cgmDotOutlineEnabled = it),
                    ),
                )
            },
            cardParams(),
        )
        root.addView(
            sliderCard(
                title = "Konturbreite",
                min = 25,
                max = 300,
                progress = (current.graphStyle.cgmDotOutlineWidthDp * 100f).roundToInt(),
                value = { String.format("%.2f dp", it / 100f) },
            ) { progress ->
                save(
                    current.copy(
                        graphStyle =
                            current.graphStyle.copy(
                                cgmDotOutlineWidthDp = progress / 100f,
                            ),
                    ),
                    rebuild = false,
                )
            },
            cardParams(),
        )

        section("APP & TILES")
        colorRow("App Hintergrund", current.uiColors.background) {
            updateUiColors { colors -> colors.copy(background = it) }
        }
        colorRow("Tile Hintergrund", current.uiColors.tileBackground) {
            updateUiColors { colors -> colors.copy(tileBackground = it) }
        }
        colorRow("Tile Kontur", current.uiColors.tileBorder) {
            updateUiColors { colors -> colors.copy(tileBorder = it) }
        }
        colorRow("Haupttext", current.uiColors.textPrimary) {
            updateUiColors { colors -> colors.copy(textPrimary = it) }
        }
        colorRow("Sekundärtext", current.uiColors.textSecondary) {
            updateUiColors { colors -> colors.copy(textSecondary = it) }
        }
        colorRow("Akzent", current.uiColors.accent) {
            updateUiColors { colors -> colors.copy(accent = it) }
        }

        section("GLUKOSE FARBEN")
        colorRow("Zuckerwert niedrig", current.uiColors.glucoseLow) {
            updateUiColors { colors -> colors.copy(glucoseLow = it) }
        }
        colorRow("Zuckerwert im Ziel", current.uiColors.glucoseInRange) {
            updateUiColors { colors -> colors.copy(glucoseInRange = it) }
        }
        colorRow("Zuckerwert hoch", current.uiColors.glucoseHigh) {
            updateUiColors { colors -> colors.copy(glucoseHigh = it) }
        }

        section("THERAPIE FARBEN")
        colorRow("IOB", current.uiColors.iob) {
            updateUiColors { colors -> colors.copy(iob = it) }
        }
        colorRow("COB", current.uiColors.cob) {
            updateUiColors { colors -> colors.copy(cob = it) }
        }

        section("GRAPH FARBEN")
        colorRow("Graph Hintergrund", current.graphColors.graphBackground) {
            updateGraphColors { colors -> colors.copy(graphBackground = it) }
        }
        colorRow("Bereich niedrig", current.graphColors.rangeLow) {
            updateGraphColors { colors -> colors.copy(rangeLow = it) }
        }
        colorRow("Bereich im Ziel", current.graphColors.rangeInRange) {
            updateGraphColors { colors -> colors.copy(rangeInRange = it) }
        }
        colorRow("Bereich hoch", current.graphColors.rangeHigh) {
            updateGraphColors { colors -> colors.copy(rangeHigh = it) }
        }
        colorRow("CGM niedrig", current.graphColors.cgmLow) {
            updateGraphColors { colors -> colors.copy(cgmLow = it) }
        }
        colorRow("CGM im Bereich", current.graphColors.cgmInRange) {
            updateGraphColors { colors -> colors.copy(cgmInRange = it) }
        }
        colorRow("CGM hoch", current.graphColors.cgmHigh) {
            updateGraphColors { colors -> colors.copy(cgmHigh = it) }
        }
        colorRow("Linien / Achsen", current.graphColors.divider) {
            updateGraphColors { colors -> colors.copy(divider = it) }
        }
        colorRow("Dot Kontur", current.graphColors.outline) {
            updateGraphColors { colors -> colors.copy(outline = it) }
        }

        section("PROGNOSE FARBEN")
        colorRow("IOB Prognose", current.graphColors.predictionIob) {
            updateGraphColors { colors -> colors.copy(predictionIob = it) }
        }
        colorRow("COB Prognose", current.graphColors.predictionCob) {
            updateGraphColors { colors -> colors.copy(predictionCob = it) }
        }
        colorRow("UAM Prognose", current.graphColors.predictionUam) {
            updateGraphColors { colors -> colors.copy(predictionUam = it) }
        }
        colorRow("ZeroTemp Prognose", current.graphColors.predictionZeroTemp) {
            updateGraphColors { colors -> colors.copy(predictionZeroTemp = it) }
        }

        TextView(this).apply {
            text = "Watch-Einstellungen werden lokal gespeichert. Die Standardfarben entsprechen der Mobile App."
            textSize = 8f
            setTextColor(ui.textSecondary)
            gravity = Gravity.CENTER
            setPadding(6.dp, 12.dp, 6.dp, 0)
            root.addView(this, fullWidth())
        }

        scroll.post {
            scroll.scrollTo(0, restoreScrollY)
        }
    }

    private fun updateGraphColors(
        transform: (WatchGraphColors) -> WatchGraphColors,
    ) {
        save(
            current.copy(
                graphColors = transform(current.graphColors),
            ),
        )
    }

    private fun updateUiColors(
        transform: (WatchUiColors) -> WatchUiColors,
    ) {
        save(
            current.copy(
                uiColors = transform(current.uiColors),
            ),
        )
    }

    private fun section(text: String) {
        root.addView(
            TextView(this).apply {
                this.text = text
                textSize = 8f
                setTextColor(current.uiColors.textSecondary)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(4.dp, 12.dp, 4.dp, 5.dp)
            },
            fullWidth(),
        )
    }

    private fun switchRow(
        title: String,
        checked: Boolean,
        changed: (Boolean) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(11.dp, 5.dp, 8.dp, 5.dp)
            background = cardBackground()
            addView(
                TextView(this@WearSettingsActivity).apply {
                    text = title
                    textSize = 10f
                    setTextColor(current.uiColors.textPrimary)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                Switch(this@WearSettingsActivity).apply {
                    isChecked = checked
                    buttonTintList = ColorStateList.valueOf(current.uiColors.accent)
                    setOnCheckedChangeListener { _, value -> changed(value) }
                },
            )
        }

    private fun choiceRow(
        choices: List<Pair<String, WatchGlucoseUnit>>,
        selected: WatchGlucoseUnit,
        changed: (WatchGlucoseUnit) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(6.dp, 5.dp, 6.dp, 5.dp)
            background = cardBackground()
            choices.forEach { (label, value) ->
                addView(
                    Button(this@WearSettingsActivity).apply {
                        text = label
                        textSize = 9f
                        minWidth = 0
                        minimumWidth = 0
                        setTextColor(
                            if (selected == value) current.uiColors.background else current.uiColors.textPrimary,
                        )
                        backgroundTintList =
                            ColorStateList.valueOf(
                                if (selected == value) current.uiColors.accent else current.uiColors.tileBackground,
                            )
                        setOnClickListener { changed(value) }
                    },
                    LinearLayout.LayoutParams(0, 34.dp, 1f).apply {
                        marginStart = 1.dp
                        marginEnd = 1.dp
                    },
                )
            }
        }

    private fun sliderCard(
        title: String,
        min: Int,
        max: Int,
        progress: Int,
        value: (Int) -> String,
        changed: (Int) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(11.dp, 7.dp, 11.dp, 6.dp)
            background = cardBackground()
            val valueText =
                TextView(this@WearSettingsActivity).apply {
                    text = value(progress)
                    textSize = 9f
                    gravity = Gravity.END
                    setTextColor(current.uiColors.textSecondary)
                }
            addView(
                LinearLayout(this@WearSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        TextView(this@WearSettingsActivity).apply {
                            text = title
                            textSize = 10f
                            setTextColor(current.uiColors.textPrimary)
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(valueText)
                },
                fullWidth(),
            )
            addView(
                SeekBar(this@WearSettingsActivity).apply {
                    this.max = max - min
                    this.progress = progress.coerceIn(min, max) - min
                    progressTintList = ColorStateList.valueOf(current.uiColors.accent)
                    thumbTintList = ColorStateList.valueOf(current.uiColors.accent)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                raw: Int,
                                fromUser: Boolean,
                            ) {
                                if (!fromUser) return
                                val actual = raw + min
                                valueText.text = value(actual)
                                changed(actual)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                        },
                    )
                },
                fullWidth(),
            )
        }

    private fun colorRow(
        title: String,
        color: Int,
        changed: (Int) -> Unit,
    ) {
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(11.dp, 6.dp, 9.dp, 6.dp)
                background = cardBackground()
                addView(
                    TextView(this@WearSettingsActivity).apply {
                        text = title
                        textSize = 10f
                        setTextColor(current.uiColors.textPrimary)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    View(this@WearSettingsActivity).apply {
                        background = colorCircle(color)
                        setOnClickListener {
                            showColorPicker(title, color, changed)
                        }
                    },
                    LinearLayout.LayoutParams(30.dp, 30.dp),
                )
            },
            cardParams(),
        )
    }

    private fun showColorPicker(
        title: String,
        selected: Int,
        changed: (Int) -> Unit,
    ) {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
        }
        COLOR_CHOICES.forEach { color ->
            grid.addView(
                View(this).apply {
                    background = colorCircle(color, color == selected)
                },
                GridLayout.LayoutParams().apply {
                    width = 42.dp
                    height = 42.dp
                    setMargins(4.dp, 4.dp, 4.dp, 4.dp)
                },
            )
        }
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(grid)
                .setNegativeButton("Abbrechen", null)
                .create()
        grid.children().forEach { child ->
            child.setOnClickListener {
                val color = COLOR_CHOICES[grid.indexOfChild(child)]
                changed(color)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun GridLayout.children(): List<View> =
        (0 until childCount).map(::getChildAt)

    private fun save(
        value: WearDisplayPreferences,
        rebuild: Boolean = true,
    ) {
        current = value
        WearDisplayPreferences.saveLocal(this, current)
        if (rebuild) buildUi()
    }

    private fun cardBackground(): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = 16.dp.toFloat()
            setColor(this@WearSettingsActivity.current.uiColors.tileBackground)
            setStroke(1.dp, this@WearSettingsActivity.current.uiColors.tileBorder)
        }

    private fun colorCircle(
        color: Int,
        selected: Boolean = false,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                if (selected) 3.dp else 1.dp,
                if (selected) this@WearSettingsActivity.current.uiColors.accent else this@WearSettingsActivity.current.uiColors.tileBorder,
            )
        }

    private fun fullWidth() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

    private fun cardParams() =
        fullWidth().apply {
            topMargin = 3.dp
            bottomMargin = 3.dp
        }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private val COLOR_CHOICES =
            intArrayOf(
                0xFF181818.toInt(),
                0xFF202020.toInt(),
                0xFF242424.toInt(),
                0xFF404040.toInt(),
                0xFFF5F5F5.toInt(),
                0xFFB5B5B5.toInt(),
                0xFF6DE892.toInt(),
                0xFF54DF30.toInt(),
                0xFF19D7E8.toInt(),
                0xFF52C1FF.toInt(),
                0xFF64BFFF.toInt(),
                0xFF9575CD.toInt(),
                0xFFD69AFF.toInt(),
                0xFFFF5C69.toInt(),
                0xFFFF9D18.toInt(),
                0xFFFFAE1F.toInt(),
                0xFFFFD040.toInt(),
                0xFFF4DE00.toInt(),
                0xFF30DBDE.toInt(),
                0xFF969696.toInt(),
                0xFF000000.toInt(),
            )
    }
}
