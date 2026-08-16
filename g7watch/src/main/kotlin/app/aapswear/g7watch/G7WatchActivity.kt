package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class G7WatchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 7)
        }
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val state = G7SensorStateStore(this).read()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(34, 42, 34, 42)
            setBackgroundColor(Color.rgb(5, 11, 16))
        }
        content.addView(label("Dexcom G7 Collector", 22f, Color.WHITE))
        content.addView(label(if (state.collectorEnabled) "AKTIV" else "INAKTIV", 16f, Color.rgb(25, 215, 232)))
        content.addView(tile("Sensor", state.sensor?.deviceName ?: "Noch nicht eingerichtet"))
        content.addView(tile("Status", state.sessionState.name.replace('_', ' ')))
        content.addView(tile("Glukose", state.lastReading?.let { "${it.glucoseMgDl.toInt()} mg/dL" } ?: "–"))
        content.addView(tile("Pending Sync", G7ReadingDatabase(this).query("synced=0").size.toString()))
        content.addView(tile("Letzter Fehler", state.lastError?.safeMessage ?: "Keiner"))
        content.addView(Button(this).apply {
            text = "Sensor einrichten"
            isEnabled = false
            setOnClickListener { }
        })
        content.addView(Button(this).apply {
            text = if (state.collectorEnabled) "Collector stoppen" else "Collector starten"
            setOnClickListener {
                if (state.collectorEnabled) G7CollectorService.stop(this@G7WatchActivity) else G7CollectorService.start(this@G7WatchActivity)
                postDelayed({ render() }, 300L)
            }
        })
        content.addView(label("G7-Ersteinrichtung und Authentifizierung sind noch durch TODO(G7-AUTH) blockiert. Es werden keine Messwerte simuliert.", 11f, Color.LTGRAY))
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun tile(title: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 14, 20, 14)
        setBackgroundColor(Color.rgb(18, 28, 34))
        addView(label(title, 11f, Color.LTGRAY))
        addView(label(value, 15f, Color.WHITE))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) }
    }

    private fun label(textValue: String, size: Float, color: Int) = TextView(this).apply {
        text = textValue; textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(4, 4, 4, 4)
    }
}
