package app.aapswear.mobile

import android.widget.Toast

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.TherapyDisplayState
import kotlinx.coroutines.launch

internal data class SugarliciousWatchFaceCard(
    val name: String,
    val style: String,
    val slots: Int,
    val features: List<String>,
)

internal val sugarliciousWatchFaceCards = listOf(
    SugarliciousWatchFaceCard(
        name = "Sugarlicious Analog",
        style = "Analog",
        slots = 8,
        features = listOf("Graph", "AOD"),
    ),
    SugarliciousWatchFaceCard(
        name = "Sugarlicious Orbit",
        style = "Analog",
        slots = 4,
        features = listOf("Glukosering", "Graph", "AOD"),
    ),
    SugarliciousWatchFaceCard(
        name = "Sugarlicious Rings",
        style = "Analog",
        slots = 4,
        features = listOf("Glukosering", "Graph", "AOD"),
    ),
    SugarliciousWatchFaceCard(
        name = "Sugarlicious Graph",
        style = "Analog",
        slots = 4,
        features = listOf("Großer Graph", "AOD"),
    ),
)

/**
 * Friendly watch overview. Technical transport and diagnostic details deliberately stay out of
 * the normal user flow; the complication studio is the only detailed destination on this page.
 */
@Composable
internal fun SugarliciousWatchScreen(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    onSelectedFace: (Int) -> Unit,
    onNavigate: (DashboardScreen) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .menuSwipeNavigation(
                screen = DashboardScreen.WATCH,
                onNavigate = onNavigate,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sugarliciousWatchFaceCards.forEachIndexed { index, face ->
            SugarliciousWatchFaceCard(
                face = face,
                index = index,
                state = state,
                selected = preferences.watchFaceIndex == index,
                onSelected = { onSelectedFace(index) },
            )
        }

        ComplicationStudio(state = state)
    }
}

@Composable
private fun SugarliciousWatchFaceCard(
    face: SugarliciousWatchFaceCard,
    index: Int,
    state: TherapyDisplayState?,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = SugarliciousColors.Border.copy(alpha = 0.58f),
                shape = shape,
            )
            .clickable(onClick = onSelected),
        shape = shape,
        color = if (selected) SugarliciousColors.SurfaceSelected else SugarliciousColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FaceDial(
                index = index,
                state = state,
                modifier = Modifier.size(104.dp),
            )
            Spacer(Modifier.width(15.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = face.name,
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    WatchFaceFeaturePill(face.style)
                    WatchFaceFeaturePill("${face.slots} Slots")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    face.features.forEach { WatchFaceFeaturePill(it) }
                }
                Text(
                    text = if (selected) "Vorschau ausgewählt" else "Für Vorschau antippen",
                    color = if (selected) SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
                    fontSize = 9.sp,
                )
                val applyContext = LocalContext.current
                val applyScope = rememberCoroutineScope()

                Button(
                    onClick = {
                        onSelected()

                        applyScope.launch {
                            val nodes =
                                runCatching {
                                    requestWatchFaceApply(
                                        applyContext.applicationContext,
                                        index,
                                    )
                                }.getOrDefault(0)

                            if (nodes == 0) {
                                Toast.makeText(
                                    applyContext,
                                    "Watch nicht erreichbar",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                SugarliciousColors.SurfaceHigh,
                            contentColor =
                                SugarliciousColors.Primary,
                        ),
                ) {
                    Text(
                        "AUF UHR",
                        fontSize = 9.sp,
                        fontWeight =
                            androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchFaceFeaturePill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SugarliciousColors.SurfaceHigh,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = SugarliciousColors.TextSecondary,
            fontSize = 8.sp,
        )
    }
}
