package app.aapswear.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aapswear.mobile.ui.theme.SugarliciousColorStore
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.mobile.ui.theme.SugarliciousTheme
import app.aapswear.model.DiagnosticEvent
import app.aapswear.model.DiagnosticSeverity
import app.aapswear.storage.DiagnosticEventStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DiagnosticActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store by lazy { DiagnosticEventStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SugarliciousColors.apply(
            SugarliciousColorStore.load(getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)),
        )
        setContent {
            SugarliciousTheme {
                val events by store.events.collectAsState(initial = emptyList())
                DiagnosticScreen(
                    events = events,
                    onBack = ::finish,
                    onRefreshWatch = ::refreshWatch,
                    onCopy = { copyEvents(events) },
                    onShare = { shareEvents(events) },
                    onClear = { scope.launch { store.clear() } },
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshWatch() {
        scope.launch {
            val nodes = runCatching { requestWatchDiagnostics(applicationContext) }.getOrDefault(0)
            Toast.makeText(
                this@DiagnosticActivity,
                if (nodes > 0) "Watch-Diagnose angefordert" else "Keine Watch erreichbar",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun copyEvents(events: List<DiagnosticEvent>) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Sugarlicious Diagnose", formatDiagnosticEvents(events)))
        Toast.makeText(this, "Diagnose kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun shareEvents(events: List<DiagnosticEvent>) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Sugarlicious Diagnose")
                    .putExtra(Intent.EXTRA_TEXT, formatDiagnosticEvents(events)),
                "Diagnose teilen",
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticScreen(
    events: List<DiagnosticEvent>,
    onBack: () -> Unit,
    onRefreshWatch: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf<DiagnosticSeverity?>(null) }
    val filtered = events.filter { event ->
        (severity == null || event.severity == severity) &&
            (query.isBlank() || listOf(event.code, event.module, event.message, event.origin).any { it.contains(query, ignoreCase = true) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnose") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Zurück") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Code, Modul oder Meldung suchen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = severity == null, onClick = { severity = null }, label = { Text("Alle") })
                FilterChip(selected = severity == DiagnosticSeverity.WARNING, onClick = { severity = DiagnosticSeverity.WARNING }, label = { Text("Warnungen") })
                FilterChip(selected = severity == DiagnosticSeverity.ERROR, onClick = { severity = DiagnosticSeverity.ERROR }, label = { Text("Fehler") })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onRefreshWatch) { Text("Watch abrufen") }
                    OutlinedButton(onClick = onCopy) { Text("Kopieren") }
                    OutlinedButton(onClick = onShare) { Text("Teilen") }
                }
                TextButton(onClick = onClear) { Text("Diagnose löschen") }
            }
            Text("${filtered.size} von ${events.size} Ereignissen · maximal 7 Tage / 1000 Einträge", style = MaterialTheme.typography.bodySmall)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(filtered, key = DiagnosticEvent::id) { event -> DiagnosticEventCard(event) }
            }
        }
    }
}

@Composable
private fun DiagnosticEventCard(event: DiagnosticEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(event.code, fontWeight = FontWeight.Bold)
                Text(event.severity.name, style = MaterialTheme.typography.labelSmall)
            }
            Text("${event.origin} · ${event.module} · ${diagnosticTime(event.occurredAtEpochMs)}", style = MaterialTheme.typography.bodySmall)
            Text(event.message)
            if (event.metadata.isNotEmpty()) {
                Text(event.metadata.entries.joinToString(" · ") { "${it.key}=${it.value}" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun formatDiagnosticEvents(events: List<DiagnosticEvent>): String =
    events.joinToString("\n") { event ->
        buildString {
            append(diagnosticTime(event.occurredAtEpochMs))
            append(" | ${event.origin} | ${event.severity} | ${event.module} | ${event.code} | ${event.message}")
            if (event.metadata.isNotEmpty()) append(" | ${event.metadata.entries.joinToString(",") { "${it.key}=${it.value}" }}")
        }
    }

private fun diagnosticTime(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
