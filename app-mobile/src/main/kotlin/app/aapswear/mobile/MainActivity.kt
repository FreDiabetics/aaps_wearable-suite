package app.aapswear.mobile
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var text: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        text = TextView(this).also {
            it.setPadding(48, 64, 48, 48)
            setContentView(it)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (!::text.isInitialized) return
        val preferences = getSharedPreferences("diagnostics", MODE_PRIVATE)
        val receivedAt = preferences.getLong("received", 0L)
        val measuredAt = preferences.getLong("measurement", 0L)
        val syncStatus = when (preferences.getString("lastSyncStatus", null)) {
            "ok" -> "übertragen"
            "pending" -> "wird übertragen"
            "unavailable" -> "keine Uhr erreichbar"
            "invalid_payload" -> "letzte Nachricht ungültig"
            else -> "noch nicht versucht"
        }

        text.text = """
            AAPS Display Bridge

            Quelle: AndroidAPS Status-Broadcast
            Erkannte AAPS-Version: ${preferences.getString("sourceVersion", null) ?: "nicht ermittelbar"}
            Datenvertrag: ${preferences.getString("contract", null) ?: "—"}
            Letzter gültiger Empfang: ${receivedAt.asDateTime()}
            Messzeitpunkt: ${measuredAt.asDateTime()}
            Erreichbare Uhren: ${preferences.getInt("reachableWatches", 0)}
            Synchronisation: $syncStatus

            Übertragung: lokal über Wear Data Layer
            Modus: strikt read-only
        """.trimIndent()
    }

    private fun Long.asDateTime(): String =
        if (this <= 0L) "—" else DateFormat.getDateTimeInstance().format(Date(this))
}
