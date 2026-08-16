package app.aapswear.mobile

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48); setBackgroundColor(Color.rgb(20, 24, 28))
            addView(TextView(this@HealthConnectRationaleActivity).apply { text = "Health Connect"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
            addView(TextView(this@HealthConnectRationaleActivity).apply {
                text = "Sugarlicious nutzt freigegebene Aktivitäts- und Gesundheitsdaten ausschließlich lokal für deine Übersicht und dein Diabetes-Management. Du entscheidest für jeden Datentyp getrennt. Eigene CGM-Werte können als Blutzucker an Health Connect geschrieben werden. Importierte Daten werden niemals als neue Daten zurückgeschrieben. Therapieentscheidungen oder Insulinsteuerung erfolgen nicht."
                textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 24, 0, 0)
            })
        })
    }
}
