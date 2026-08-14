package app.aapswear.wear

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Forward-compatible watch-face design settings.
 *
 * The selections are intentionally persisted independently from the current WFF payload so the
 * menu structure can ship now and individual watch faces can consume the values later without
 * changing the settings UI again.
 */
class WatchFaceDesignSettingsView(context: Context) : LinearLayout(context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ui = WearDisplayPreferences.read(context).uiColors

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        addView(sectionLabel("WATCHFACE · MATERIAL DESIGN"), fullWidth())
        addView(materialColorGrid(), fullWidth())
        addView(sectionLabel("ZEIGERAUSWAHL"), fullWidth())
        addView(handStyleGrid(), fullWidth())
        addView(
            TextView(context).apply {
                text = "Struktur vorbereitet · die Watchfaces können diese Auswahl schrittweise übernehmen"
                textSize = 7.5f
                gravity = Gravity.CENTER
                setTextColor(ui.textSecondary)
                setPadding(10.dp, 7.dp, 10.dp, 4.dp)
            },
            fullWidth(),
        )
    }

    private fun materialColorGrid(): View {
        val selected = prefs.getInt(KEY_MATERIAL_COLOR, MATERIAL_COLORS.first().second)
        return GridLayout(context).apply {
            columnCount = 3
            setPadding(4.dp, 4.dp, 4.dp, 4.dp)
            MATERIAL_COLORS.forEach { (label, color) ->
                addView(
                    Button(context).apply {
                        text = label
                        textSize = 7.5f
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(2.dp, 0, 2.dp, 0)
                        setTextColor(if (selected == color) Color.BLACK else ui.textPrimary)
                        backgroundTintList = ColorStateList.valueOf(if (selected == color) color else ui.tileBackground)
                        setOnClickListener {
                            prefs.edit().putInt(KEY_MATERIAL_COLOR, color).apply()
                            rebuildParentSettings()
                        }
                    },
                    GridLayout.LayoutParams().apply {
                        width = 0
                        height = 34.dp
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(2.dp, 2.dp, 2.dp, 2.dp)
                    },
                )
            }
        }
    }

    private fun handStyleGrid(): View {
        val selected = prefs.getString(KEY_HAND_STYLE, HAND_STYLES.first()) ?: HAND_STYLES.first()
        return GridLayout(context).apply {
            columnCount = 2
            setPadding(4.dp, 4.dp, 4.dp, 4.dp)
            HAND_STYLES.forEach { style ->
                addView(
                    Button(context).apply {
                        text = style
                        textSize = 8f
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(2.dp, 0, 2.dp, 0)
                        setTextColor(if (selected == style) ui.background else ui.textPrimary)
                        backgroundTintList = ColorStateList.valueOf(if (selected == style) ui.accent else ui.tileBackground)
                        setOnClickListener {
                            prefs.edit().putString(KEY_HAND_STYLE, style).apply()
                            rebuildParentSettings()
                        }
                    },
                    GridLayout.LayoutParams().apply {
                        width = 0
                        height = 34.dp
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(2.dp, 2.dp, 2.dp, 2.dp)
                    },
                )
            }
        }
    }

    private fun sectionLabel(value: String) =
        TextView(context).apply {
            text = value
            textSize = 8f
            setTextColor(ui.textSecondary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(6.dp, 12.dp, 6.dp, 5.dp)
            gravity = Gravity.CENTER
        }

    private fun rebuildParentSettings() {
        (context as? WearSettingsActivity)?.recreate()
    }

    private fun fullWidth() =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PREFS = "watchface_design"
        private const val KEY_MATERIAL_COLOR = "material_color"
        private const val KEY_HAND_STYLE = "hand_style"

        private val MATERIAL_COLORS = listOf(
            "Grün" to 0xFF4CAF50.toInt(),
            "Blau" to 0xFF2196F3.toInt(),
            "Violett" to 0xFF9C27B0.toInt(),
            "Orange" to 0xFFFF9800.toInt(),
            "Rot" to 0xFFF44336.toInt(),
            "Cyan" to 0xFF00BCD4.toInt(),
        )
        private val HAND_STYLES = listOf("Standard", "Minimal", "Klassisch", "Technisch")
    }
}
