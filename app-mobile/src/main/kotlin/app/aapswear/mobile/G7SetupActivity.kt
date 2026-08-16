package app.aapswear.mobile

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import app.aapswear.protocol.G7SetupCommand
import app.aapswear.protocol.WearProtocol
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.android.gms.wearable.Wearable
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class G7SetupActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var pairingCode: EditText
    private lateinit var status: TextView
    private var scannedSerial: String? = null
    private var scannedGtin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ScrollView(this).apply { addView(content()) })
    }

    private fun content() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24.dp, 28.dp, 24.dp, 28.dp)
        setBackgroundColor(Color.rgb(15, 20, 24))
        addView(label("Dexcom G7 Watch", 25f, Color.WHITE))
        addView(label("Sensor direkt mit der Galaxy Watch verbinden", 15f, Color.rgb(25, 215, 232)))
        addView(label("Beende dafür den G7-Collector in Juggluco. Ein Sensor kann immer nur einen aktiven Collector beliefern.", 13f, Color.LTGRAY))

        pairingCode = EditText(this@G7SetupActivity).apply {
            hint = "4-stelliger Sensorcode"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            textSize = 22f
        }
        addView(pairingCode, rowParams())
        addView(Button(this@G7SetupActivity).apply {
            text = "Data-Matrix-Code scannen"
            setOnClickListener { scanApplicator() }
        }, rowParams())
        addView(Button(this@G7SetupActivity).apply {
            text = "Sensor auf der Watch einrichten"
            setOnClickListener { sendSetup() }
        }, rowParams())
        status = label("Noch nicht übertragen", 13f, Color.LTGRAY)
        addView(status, rowParams())
        addView(label("Der Sensorcode und der Sitzungsschlüssel bleiben verschlüsselt auf der Watch. Sie werden weder in Diagnosen geschrieben noch an Health Connect übertragen.", 11f, Color.GRAY))
    }

    private fun scanApplicator() {
        status.text = "Scanner wird geöffnet …"
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                    ?: barcode.rawBytes?.toString(Charsets.ISO_8859_1)
                val parsed = raw?.let(G7ApplicatorBarcodeParser::parse)
                if (parsed == null) {
                    status.text = "G7-Code nicht erkannt. Bitte den 4-stelligen Code eingeben."
                } else {
                    pairingCode.setText(parsed.pairingCode)
                    scannedSerial = parsed.sensorSerial
                    scannedGtin = parsed.gtin
                    status.text = "G7-Applikator erkannt"
                }
            }
            .addOnCanceledListener { status.text = "Scan abgebrochen" }
            .addOnFailureListener { status.text = "Scanner nicht verfügbar – Code bitte manuell eingeben" }
    }

    private fun sendSetup() {
        val code = pairingCode.text?.toString().orEmpty()
        val command = runCatching { G7SetupCommand(code, scannedSerial, scannedGtin) }.getOrNull()
        if (command == null) {
            status.text = "Bitte den 4-stelligen Sensorcode eingeben"
            return
        }
        status.text = "Wird an die Watch übertragen …"
        scope.launch {
            val sent = withContext(Dispatchers.IO) {
                val nodes = runCatching { Wearable.getNodeClient(this@G7SetupActivity).connectedNodes.await() }.getOrDefault(emptyList())
                nodes.count { node ->
                    runCatching {
                        Wearable.getMessageClient(this@G7SetupActivity)
                            .sendMessage(node.id, WearProtocol.G7_SETUP_PATH, WearProtocol.encodeG7Setup(command))
                            .await()
                    }.isSuccess
                }
            }
            if (sent == 0) {
                status.text = "Keine Watch erreichbar. Bluetooth/WLAN-Verbindung prüfen."
            } else {
                getSharedPreferences("dashboard_ui", MODE_PRIVATE).edit {
                    putString("dataSource", DataSourcePreference.DEXCOM_G7_WATCH.name)
                }
                scope.launch(Dispatchers.IO) { runCatching { publishWatchConfig(applicationContext) } }
                pairingCode.text?.clear()
                status.text = "Einrichtung übertragen. Kopplungsanfrage auf der Watch bestätigen, falls sie erscheint."
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(4.dp, 8.dp, 4.dp, 8.dp)
    }

    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, 8.dp, 0, 0)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

internal object G7ApplicatorBarcodeParser {
    fun parse(input: String): G7SetupCommand? {
        val normalized = input.trim()
            .removePrefix("]d2")
            .replace("^]", "\u001d")
            .replace(Regex("\\((\\d{2,3})\\)"), "$1")
        val fields = parseGs1(normalized)
        val code = fields["240"]?.take(4)
            ?: Regex("240([0-9]{4})").find(normalized)?.groupValues?.get(1)
            ?: return null
        if (code.length != 4 || !code.all(Char::isDigit)) return null
        val gtin = fields["01"]
        if (gtin != null && (gtin.length != 14 || gtin.substring(1, 8) != "0386270")) return null
        return G7SetupCommand(code, fields["21"], gtin)
    }

    private fun parseGs1(value: String): Map<String, String> {
        val fixed = linkedMapOf("01" to 14, "11" to 6, "17" to 6)
        val variable = listOf("240", "250", "21", "10")
        var offset = 0
        val result = linkedMapOf<String, String>()
        while (offset < value.length) {
            if (value[offset] == '\u001d') {
                offset++
                continue
            }
            val ai = (variable + fixed.keys).firstOrNull { value.startsWith(it, offset) } ?: break
            offset += ai.length
            val end = fixed[ai]?.let { (offset + it).coerceAtMost(value.length) }
                ?: value.indexOf('\u001d', offset).takeIf { it >= 0 }
                ?: value.length
            result[ai] = value.substring(offset, end)
            offset = end
        }
        return result
    }
}
