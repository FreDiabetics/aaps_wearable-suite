package app.aapswear.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.Freshness
import app.aapswear.model.FreshnessPolicy
import app.aapswear.model.TherapyDisplayState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SugarliciousWatchScreen(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    now: Long,
    onSyncNow: () -> Unit,
) {
    val connected = diagnostics.reachableWatches > 0
    val syncOk = diagnostics.syncStatus == "ok"
    val freshness = FreshnessPolicy.classify(state?.glucose?.measuredAtEpochMs, now)
    val bridgeColor = when {
        connected && syncOk -> SugarliciousColors.Primary
        connected -> SugarliciousColors.Yellow
        else -> SugarliciousColors.Red
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BridgeHero(
            connected = connected,
            syncOk = syncOk,
            watchCount = diagnostics.reachableWatches,
            bridgeColor = bridgeColor,
            lastSyncAt = diagnostics.lastSyncAt,
            onSyncNow = onSyncNow,
        )

        BridgeRoute(
            aapsAvailable = diagnostics.sourceVersion != null,
            watchConnected = connected,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WatchMetric(
                Modifier.weight(1f),
                "LETZTER SYNC",
                formatTime(diagnostics.lastSyncAt),
                syncStatusText(diagnostics.syncStatus),
                bridgeColor,
            )
            WatchMetric(
                Modifier.weight(1f),
                "CGM-ALTER",
                ageText(state?.glucose?.measuredAtEpochMs, now),
                freshnessText(freshness),
                freshnessColor(freshness),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WatchMetric(
                Modifier.weight(1f),
                "AAPS",
                diagnostics.sourceVersion ?: "—",
                diagnostics.sourceContract ?: "keine Quelle",
                if (diagnostics.sourceVersion != null) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
            )
            WatchMetric(
                Modifier.weight(1f),
                "WEAR-DATEN",
                "${state?.capabilities?.size ?: 0}",
                "Fähigkeiten im Payload",
                SugarliciousColors.Secondary,
            )
        }

        DataPayloadCard(state)

        NightscoutBridgeCard(diagnostics)
        ComplicationStudio(state = state)
    }
}

@Composable
private fun BridgeHero(
    connected: Boolean,
    syncOk: Boolean,
    watchCount: Int,
    bridgeColor: Color,
    lastSyncAt: Long,
    onSyncNow: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(bridgeColor, CircleShape))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "GALAXY WATCH BRIDGE",
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                )
                Text(
                    when {
                        connected && syncOk -> "Watch verbunden"
                        connected -> "Watch erreichbar"
                        else -> "Keine Watch erreichbar"
                    },
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SugarliciousColors.SurfaceHigh,
            ) {
                Text(
                    text = if (watchCount == 1) "1 Watch" else "$watchCount Watches",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = bridgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Text(
            text = if (lastSyncAt > 0L) {
                "Letzte erfolgreiche Übertragung: ${formatDateTime(lastSyncAt)}"
            } else {
                "Noch keine erfolgreiche Übertragung protokolliert."
            },
            color = SugarliciousColors.TextSecondary,
            fontSize = 11.sp,
        )

        Button(
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SugarliciousColors.Primary,
                contentColor = SugarliciousColors.OnPrimary,
            ),
        ) {
            Text("JETZT AN WATCH ÜBERTRAGEN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BridgeRoute(
    aapsAvailable: Boolean,
    watchConnected: Boolean,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.75f), shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "DATENWEG",
            color = SugarliciousColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteNode("AndroidAPS", aapsAvailable, Modifier.weight(1f))
            RouteArrow()
            RouteNode("Sugarlicious", true, Modifier.weight(1f))
            RouteArrow()
            RouteNode("Galaxy Watch", watchConnected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RouteNode(
    title: String,
    active: Boolean,
    modifier: Modifier,
) {
    val color = if (active) SugarliciousColors.Primary else SugarliciousColors.TextSecondary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SugarliciousColors.SurfaceHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                color = SugarliciousColors.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (active) "bereit" else "offline",
                color = color,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun RouteArrow() {
    Text(
        "›",
        modifier = Modifier.width(18.dp),
        color = SugarliciousColors.TextSecondary,
        fontSize = 22.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WatchMetric(
    modifier: Modifier,
    title: String,
    value: String,
    sub: String,
    accent: Color,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .height(88.dp)
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.75f), shape)
            .padding(11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(title, color = SugarliciousColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            value,
            color = SugarliciousColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            sub,
            color = accent,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DataPayloadCard(state: TherapyDisplayState?) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.75f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            "ÜBERTRAGENER ANZEIGESTATUS",
            color = SugarliciousColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
        ChipLine(
            listOf(
                "Glukose & Trend" to (state?.glucose != null),
                "IOB" to (state?.insulin != null),
                "COB" to (state?.carbs != null),
                "Basal" to (state?.basal != null),
            ),
        )
        ChipLine(
            listOf(
                "Loop" to (state?.loop != null),
                "Pumpe" to (state?.pump != null),
                "Profil" to (state?.profile != null),
                "Prognosen" to !state?.glucosePredictions.isNullOrEmpty(),
            ),
        )
        Text(
            "Sugarlicious verändert keine Therapiedaten. Die Smartphone-App dient als read-only Bridge für Wear OS.",
            color = SugarliciousColors.TextSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun ChipLine(items: List<Pair<String, Boolean>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (label, available) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                color = SugarliciousColors.SurfaceHigh,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    color = if (available) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NightscoutBridgeCard(diagnostics: DiagnosticsSnapshot) {
    val configured = NightscoutConfigStore.isConfigured(androidx.compose.ui.platform.LocalContext.current)
    val statusColor = when (diagnostics.historyBackfillStatus) {
        "ok" -> SugarliciousColors.Primary
        "error" -> SugarliciousColors.Red
        else -> SugarliciousColors.TextSecondary
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SugarliciousColors.Surface, shape)
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.75f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("NIGHTSCOUT BACKFILL", color = SugarliciousColors.TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (configured) "${diagnostics.historyBackfillPointCount} historische CGM-Punkte" else "Nicht eingerichtet",
                color = SugarliciousColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = "AAPS bleibt live",
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
        )
    }
}

private fun ageText(timestamp: Long?, now: Long): String =
    timestamp?.let { "${((now - it).coerceAtLeast(0L) / 60_000L)} min" } ?: "—"

private fun freshnessText(freshness: Freshness): String = when (freshness) {
    Freshness.CURRENT -> "aktuell"
    Freshness.DELAYED -> "verzögert"
    Freshness.STALE -> "veraltet"
    Freshness.NO_DATA -> "keine Daten"
}

private fun freshnessColor(freshness: Freshness): Color = when (freshness) {
    Freshness.CURRENT -> SugarliciousColors.Primary
    Freshness.DELAYED -> SugarliciousColors.Yellow
    Freshness.STALE, Freshness.NO_DATA -> SugarliciousColors.Red
}

private fun syncStatusText(status: String?): String = when (status) {
    "ok" -> "erfolgreich"
    "pending" -> "läuft"
    "unavailable" -> "nicht erreichbar"
    null -> "noch nicht synchronisiert"
    else -> status
}

private fun formatTime(timestamp: Long): String =
    if (timestamp <= 0L) "—" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private fun formatDateTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
