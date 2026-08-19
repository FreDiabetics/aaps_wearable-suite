package app.aapswear.g7watch

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SessionManager
import app.aapswear.g7.G7SetupPayload
import app.aapswear.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class G7WatchActivity : Activity() {
    private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryRequestPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND
        requestMissingPermissions()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (batteryRequestPending) {
            batteryRequestPending = false
            val unrestricted = G7BackgroundAccess.isBatteryUnrestricted(this)
            if (unrestricted) {
                Toast.makeText(this, "Dauerbetrieb ist uneingeschränkt", Toast.LENGTH_SHORT).show()
                recordBackgroundDiagnostic(
                    "G7-BG-200",
                    "Battery optimization exemption granted for G7 Watch Collector",
                    DiagnosticSeverity.INFO,
                )
            } else {
                Toast.makeText(
                    this,
                    "Dauerbetrieb nicht freigegeben – Akkuoptimierung ist weiterhin aktiv",
                    Toast.LENGTH_LONG,
                ).show()
                recordBackgroundDiagnostic(
                    "G7-BG-403",
                    "Battery optimization exemption was not granted for G7 Watch Collector",
                    DiagnosticSeverity.WARNING,
                )
            }
        }
        render()
    }

    private fun render() {
        val state = G7SensorStateStore(this).read()
        val credentials = G7CredentialStore(this).read()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22.dp, 24.dp, 22.dp, 34.dp)
            setBackgroundColor(BACKGROUND)
        }

        content.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_g7_sensor)
                contentDescription = "G7 Sensor"
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            LinearLayout.LayoutParams(82.dp, 82.dp).apply {
                bottomMargin = 4.dp
            },
        )
        content.addView(label("G7 Direct to Watch", 21f, TEXT_PRIMARY, bold = true))
        content.addView(
            label("by Sugarlicious", 11f, TEXT_SECONDARY, bold = true).apply {
                letterSpacing = 0.08f
            },
        )
        content.addView(statusPill(state.collectorEnabled, state.lastError != null))

        val reading = state.lastReading
        content.addView(
            card().apply {
                addView(sectionLabel("AKTUELLER WERT"))
                addView(label(reading?.let { "${it.glucoseMgDl.toInt()} mg/dL" } ?: "—", 28f, TEXT_PRIMARY, bold = true))
                addView(label(reading?.let { "Empfangen ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.receivedAtEpochMs))}" } ?: "Noch kein lokaler G7-Wert", 11f, TEXT_SECONDARY))
            },
            cardParams(),
        )

        if (state.sensor != null || credentials != null || reading != null) {
            val sensor = state.sensor
            content.addView(
                card().apply {
                    addView(sectionLabel("SENSOR-DOKUMENTATION"))
                    addView(valueRow("Sensorcode", credentials?.pairingCode ?: "—"))
                    addView(valueRow("GTIN", credentials?.gtin ?: "—"))
                    addView(valueRow("Seriennummer", credentials?.sensorSerial ?: "—"))
                    addView(valueRow("Sensor-ID", sensor?.sensorId ?: reading?.sensorId ?: "—"))
                    addView(valueRow("Session-ID", sensor?.sessionId ?: reading?.sessionId ?: "—"))
                    addView(valueRow("BLE-Name", sensor?.deviceName ?: "—"))
                    addView(valueRow("BLE-Adresse", sensor?.deviceAddress ?: "—"))
                    addView(valueRow("Sensorstatus", sensor?.state?.name ?: "—"))
                    addView(divider())
                    addView(valueRow("Abgeleiteter Start", formatTimestamp(sensor?.sensorStartEpochMs ?: reading?.sensorStartEpochMs)))
                    addView(valueRow("Reguläres Ende", formatTimestamp(sensor?.sensorEndEpochMs ?: reading?.sensorEndEpochMs)))
                    addView(valueRow("Kulanzende", formatTimestamp(sensor?.graceEndEpochMs ?: reading?.graceEndEpochMs)))
                    addView(valueRow("Letzter Sensorzähler", reading?.rawSourceTimestamp?.let { "$it s" } ?: "—"))
                    addView(valueRow("Alter des Werts", reading?.sensorAgeSeconds?.let { "$it s" } ?: "—"))
                    addView(valueRow("Sequenz", reading?.sequenceNumber?.toString() ?: "—"))
                    addView(valueRow("Trendrate", reading?.trendRateMgDlPerMinute?.let { String.format(Locale.US, "%.1f mg/dL/min", it) } ?: "—"))
                    addView(valueRow("Vorhersage", reading?.predictedMgDl?.let { "${it.toInt()} mg/dL" } ?: "—"))
                    addView(valueRow("Nur Anzeige", reading?.displayOnly?.toString() ?: "—"))
                    addView(valueRow("Protokollstatus", reading?.protocolStatusCode?.toString() ?: "—"))
                    addView(valueRow("Kalibrierstatus", reading?.calibrationStateCode?.toString() ?: "—"))
                    addView(valueRow("Reserviertes Feld", reading?.reservedField?.toString() ?: "—"))
                    addView(label("Sensorcode und Sessiondaten sind nur lokal gespeichert; der Sensorcode liegt verschlüsselt im Android Keystore.", 9f, TEXT_SECONDARY).apply { gravity = Gravity.START })
                },
                cardParams(),
            )
        }

        content.addView(
            card().apply {
                addView(sectionLabel("VERBINDUNG"))
                addView(valueRow("Sensor", state.sensor?.deviceName ?: "Nicht eingerichtet"))
                addView(valueRow("Phase", state.protocolState.displayName()))
                addView(valueRow("Status", state.sessionState.displayName()))
                state.lastError?.let { error ->
                    addView(divider())
                    addView(label(error.code, 12f, ERROR, bold = true).apply { gravity = Gravity.START })
                    addView(label(error.safeMessage, 12f, TEXT_PRIMARY).apply { gravity = Gravity.START })
                }
            },
            cardParams(),
        )

        content.addView(
            card().apply {
                addView(sectionLabel("APP-BETRIEB"))
                val nearbyAllowed =
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                val notificationsAllowed =
                    Build.VERSION.SDK_INT < 33 ||
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                val batteryUnrestricted = isBatteryUnrestricted()
                val exactReconnectAllowed = canScheduleExactReconnects()
                addView(valueRow("Geräte in der Nähe", if (nearbyAllowed) "Erlaubt" else "Freigeben"))
                addView(valueRow("Benachrichtigungen", if (notificationsAllowed) "Erlaubt" else "Freigeben"))
                addView(valueRow("Akku-Optimierung", if (batteryUnrestricted) "Uneingeschränkt" else "Optimiert"))
                addView(valueRow("Präzise Sensor-Abfragen", if (exactReconnectAllowed) "Erlaubt" else "Freigeben"))
                if (!nearbyAllowed || !notificationsAllowed) {
                    addView(
                        actionButton("Berechtigungen freigeben", primary = false) {
                            requestMissingPermissions()
                        },
                        buttonParams(top = 10),
                    )
                }
                if (!batteryUnrestricted) {
                    addView(
                        actionButton("Dauerbetrieb freigeben", primary = false) {
                            requestBatteryExemption()
                        },
                        buttonParams(top = 10),
                    )
                }
                if (!exactReconnectAllowed) {
                    addView(
                        actionButton("Präzise Sensor-Abfragen freigeben", primary = false) {
                            requestExactAlarmAccess()
                        },
                        buttonParams(top = 10),
                    )
                }
                addView(
                    label(
                        "Der Collector verbindet sich nur mit dem eingerichteten G7-Sensor. Präzise Sensor-Abfragen halten den 5-Minuten-Reconnect zeitnah, ohne einen dauerhaften BLE-Scan zu verwenden.",
                        9f,
                        TEXT_SECONDARY,
                    ).apply { gravity = Gravity.START },
                )
            },
            cardParams(),
        )

        content.addView(
            setupCard(state.sensor != null),
            cardParams(),
        )

        content.addView(
            actionButton(
                if (state.collectorEnabled) "Collector stoppen" else "Collector starten",
                primary = !state.collectorEnabled,
            ) {
                if (state.collectorEnabled) G7CollectorService.stop(this) else G7CollectorService.start(this)
                content.postDelayed({ render() }, 350L)
            },
            buttonParams(),
        )
        content.addView(label("Nur einen direkten G7-Collector gleichzeitig verwenden. Juggluco oder xDrip vorher beenden.", 10f, TEXT_SECONDARY))

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BACKGROUND)
            addView(content)
        })
    }

    private fun setupCard(configured: Boolean): LinearLayout = card().apply {
        addView(sectionLabel(if (configured) "SENSOR NEU EINRICHTEN" else "SENSOR EINRICHTEN"))
        addView(label("Vierstelliger Code vom G7-Applikator", 12f, TEXT_SECONDARY).apply { gravity = Gravity.START })
        val codeInput = EditText(this@G7WatchActivity).apply {
            hint = "0000"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_SECONDARY)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(14.dp, 9.dp, 14.dp, 9.dp)
            background = rounded(FIELD, BORDER, 16f)
        }
        addView(codeInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 8.dp
        })
        addView(actionButton("Sensorcode speichern", primary = true) {
            val code = codeInput.text?.toString().orEmpty()
            val payload = runCatching { G7SetupPayload(code) }.getOrNull()
            if (payload == null) {
                codeInput.error = "4 Ziffern erforderlich"
                return@actionButton
            }
            G7CredentialStore(this@G7WatchActivity).saveSetup(payload)
            val sensorId = "G7-${UUID.randomUUID().toString().take(8)}"
            val sensor = G7Sensor(sensorId, sensorId, "Dexcom G7")
            G7SensorStateStore(this@G7WatchActivity).save(
                G7SessionManager(G7SensorStateStore(this@G7WatchActivity).read()).prepareInitialSetup(sensor),
            )
            codeInput.text?.clear()
            postDelayed({ render() }, 350L)
        }, buttonParams(top = 10))
        addView(
            label(
                "Das Speichern startet keine Sensorsuche. Zum Verbinden anschließend bewusst „Collector starten“ wählen.",
                9f,
                TEXT_SECONDARY,
            ).apply { gravity = Gravity.START },
        )
    }

    private fun statusPill(active: Boolean, hasError: Boolean): TextView {
        val color = when {
            hasError -> ERROR
            active -> ACCENT
            else -> TEXT_SECONDARY
        }
        return label(
            when {
                hasError -> "●  PRÜFEN"
                active -> "●  AKTIV"
                else -> "○  INAKTIV"
            },
            11f,
            color,
            bold = true,
        ).apply {
            background = rounded(Color.argb(36, Color.red(color), Color.green(color), Color.blue(color)), color, 18f)
            setPadding(14.dp, 6.dp, 14.dp, 6.dp)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp
                bottomMargin = 4.dp
            }
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp, 14.dp, 16.dp, 14.dp)
        background = rounded(SURFACE, BORDER, 22f)
    }

    private fun valueRow(title: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 11f, TEXT_SECONDARY).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label(value, 11f, TEXT_PRIMARY, bold = true).apply {
            gravity = Gravity.END
            maxLines = 2
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(BORDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
            setMargins(0, 8.dp, 0, 8.dp)
        }
    }

    private fun sectionLabel(value: String) = label(value, 10f, ACCENT, bold = true).apply {
        gravity = Gravity.START
        letterSpacing = 0.11f
    }

    private fun actionButton(textValue: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 13f
        minHeight = 46.dp
        minimumHeight = 46.dp
        setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (primary) Color.rgb(9, 25, 15) else TEXT_PRIMARY)
        backgroundTintList = ColorStateList.valueOf(if (primary) ACCENT else SURFACE_HIGH)
        setOnClickListener { action() }
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(3.dp, 3.dp, 3.dp, 3.dp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(1.dp, stroke)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun cardParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, 8.dp, 0, 0)
    }

    private fun buttonParams(top: Int = 8) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        setMargins(0, top.dp, 0, 0)
    }

    private fun requestMissingPermissions() {
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_SCAN)
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) render()
    }

    private fun isBatteryUnrestricted(): Boolean = G7BackgroundAccess.isBatteryUnrestricted(this)

    private fun canScheduleExactReconnects(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun requestBatteryExemption() {
        if (G7BackgroundAccess.isBatteryUnrestricted(this)) {
            Toast.makeText(this, "Dauerbetrieb ist bereits uneingeschränkt", Toast.LENGTH_SHORT).show()
            render()
            return
        }

        batteryRequestPending = true
        if (G7BackgroundAccess.openBatterySettings(this)) return

        batteryRequestPending = false
        Toast.makeText(
            this,
            "Akku-Einstellungen konnten auf dieser Watch nicht geöffnet werden",
            Toast.LENGTH_LONG,
        ).show()
        recordBackgroundDiagnostic(
            "G7-BG-404",
            "Battery optimization settings could not be opened for G7 Watch Collector",
            DiagnosticSeverity.ERROR,
        )
    }

    private fun recordBackgroundDiagnostic(
        code: String,
        message: String,
        severity: DiagnosticSeverity,
    ) {
        diagnosticScope.launch {
            applicationContext.recordG7Diagnostic(
                code,
                message,
                severity,
            )
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName")),
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        }
    }

    override fun onDestroy() {
        diagnosticScope.cancel()
        super.onDestroy()
    }

    private fun app.aapswear.g7.G7ProtocolState.displayName(): String = when (this) {
        app.aapswear.g7.G7ProtocolState.SCANNING -> "Sensor suchen"
        app.aapswear.g7.G7ProtocolState.CONNECTING -> "Verbinden"
        app.aapswear.g7.G7ProtocolState.DISCOVERING_SERVICES -> "Dienste prüfen"
        app.aapswear.g7.G7ProtocolState.ENABLING_NOTIFICATIONS -> "Datenkanäle öffnen"
        app.aapswear.g7.G7ProtocolState.AUTHENTICATION_START,
        app.aapswear.g7.G7ProtocolState.AUTHENTICATING,
        -> "Authentifizieren"
        app.aapswear.g7.G7ProtocolState.BONDING -> "Koppeln"
        app.aapswear.g7.G7ProtocolState.REQUESTING_GLUCOSE -> "Wert anfordern"
        app.aapswear.g7.G7ProtocolState.RECEIVING_GLUCOSE -> "Wert empfangen"
        app.aapswear.g7.G7ProtocolState.WAITING_FOR_NEXT_READING -> "Nächsten Wert abwarten"
        app.aapswear.g7.G7ProtocolState.ERROR -> "Fehler"
        else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

    private fun app.aapswear.g7.G7SessionState.displayName(): String =
        name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private fun formatTimestamp(timestamp: Long?): String =
        timestamp?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "—"

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val PERMISSION_REQUEST = 7
        val BACKGROUND = Color.rgb(24, 24, 24)
        val SURFACE = Color.rgb(36, 36, 36)
        val SURFACE_HIGH = Color.rgb(48, 48, 48)
        val FIELD = Color.rgb(30, 30, 30)
        val BORDER = Color.rgb(64, 64, 64)
        val TEXT_PRIMARY = Color.rgb(245, 245, 245)
        val TEXT_SECONDARY = Color.rgb(181, 181, 181)
        val ACCENT = Color.rgb(109, 232, 146)
        val ERROR = Color.rgb(255, 92, 105)
    }
}
