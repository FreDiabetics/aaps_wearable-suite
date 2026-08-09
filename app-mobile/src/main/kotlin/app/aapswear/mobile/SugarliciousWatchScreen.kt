package app.aapswear.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.TherapyDisplayState

/**
 * Friendly watch overview. Technical transport and diagnostic details deliberately stay out of
 * the normal user flow; the complication studio is the only detailed destination on this page.
 */
@Composable
internal fun SugarliciousWatchScreen(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    preferences: DashboardUiPreferences,
    now: Long,
    onSyncNow: () -> Unit,
    onSelectedFace: (Int) -> Unit,
) {
    val connected = diagnostics.reachableWatches > 0
    val hasProblem = connected && diagnostics.syncStatus !in listOf(null, "ok", "pending")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            color = SugarliciousColors.Surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            color = when {
                                hasProblem -> SugarliciousColors.Red
                                connected -> SugarliciousColors.Primary
                                else -> SugarliciousColors.TextSecondary
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (connected) "Watch verbunden" else "Keine Watch verbunden",
                    modifier = Modifier.weight(1f),
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 14.sp,
                )
                Button(
                    onClick = onSyncNow,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SugarliciousColors.SurfaceHigh),
                ) {
                    Text("Aktualisieren", color = SugarliciousColors.Primary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        OverviewWatchFaceTile(
            state = state,
            diagnostics = diagnostics,
            selectedFaceIndex = preferences.watchFaceIndex,
            now = now,
            onSelectedFace = onSelectedFace,
            onEdit = {},
        )
        Spacer(Modifier.size(10.dp))
        ComplicationStudio(state = state)
    }
}
