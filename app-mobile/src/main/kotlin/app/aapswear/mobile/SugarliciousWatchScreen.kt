package app.aapswear.mobile

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

internal val sugarliciousWatchFaceCards =
    listOf(
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

@Composable
internal fun SugarliciousWatchScreen(
    state: TherapyDisplayState?,
    preferences: DashboardUiPreferences,
    onSelectedFace: (Int) -> Unit,
    onNavigate: (DashboardScreen) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var runtimeStatus by remember { mutableStateOf(WatchRuntimeStatusStore.read(appContext)) }
    var activeFaceIndex by remember {
        mutableStateOf<Int?>(runtimeStatus.activeSugarliciousFaceIndex ?: preferences.watchFaceIndex)
    }
    var editingFaceIndex by remember {
        mutableStateOf(runtimeStatus.activeSugarliciousFaceIndex ?: preferences.watchFaceIndex)
    }
    var facePresets by remember { mutableStateOf(WatchFacePresetStore.readAll(appContext)) }

    DisposableEffect(appContext) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                val updated = WatchRuntimeStatusStore.read(appContext)
                runtimeStatus = updated
                activeFaceIndex = updated.activeSugarliciousFaceIndex
            }
        WatchRuntimeStatusStore.registerListener(appContext, listener)
        onDispose {
            WatchRuntimeStatusStore.unregisterListener(appContext, listener)
        }
    }

    LaunchedEffect(appContext) {
        runCatching { requestWatchRuntimeStatus(appContext) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sugarliciousWatchFaceCards.indices.chunked(2).forEach { indices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                indices.forEach { index ->
                    WatchFaceTile(
                        modifier = Modifier.weight(1f),
                        face = sugarliciousWatchFaceCards[index],
                        index = index,
                        state = state,
                        activeComplicationIds = facePresets[index],
                        selected = activeFaceIndex == index,
                        onSelected = {
                            editingFaceIndex = index
                            val activated = WatchFacePresetStore.activate(appContext, index)
                            facePresets = facePresets.toMutableList().also { it[index] = activated }
                            onSelectedFace(index)
                        },
                    )
                }

                if (indices.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        key(editingFaceIndex) {
            CompositionLocalProvider(LocalSugarliciousTrendArrowMaxSize provides 8.dp) {
                ComplicationStudio(
                    state = state,
                    onPresetChanged = { updated ->
                        WatchFacePresetStore.save(appContext, editingFaceIndex, updated)
                        facePresets =
                            facePresets.toMutableList().also { presets ->
                                presets[editingFaceIndex] = updated
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchFaceTile(
    modifier: Modifier,
    face: SugarliciousWatchFaceCard,
    index: Int,
    state: TherapyDisplayState?,
    activeComplicationIds: List<Int>,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(24.dp)

    Surface(
        modifier =
            modifier
                .aspectRatio(1f)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color =
                        if (selected) {
                            SugarliciousColors.Primary
                        } else {
                            SugarliciousColors.Border.copy(alpha = 0.58f)
                        },
                    shape = shape,
                )
                .clickable {
                    onSelected()
                    scope.launch {
                        val appContext = context.applicationContext
                        val preset = WatchFacePresetStore.activate(appContext, index)
                        runCatching {
                            syncComplicationPreset(appContext, preset)
                        }

                        val nodes =
                            runCatching {
                                requestWatchFaceApply(
                                    appContext,
                                    index,
                                )
                            }.getOrDefault(0)

                        if (nodes == 0) {
                            Toast.makeText(
                                context,
                                "Watch nicht erreichbar",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
        shape = shape,
        color =
            if (selected) {
                SugarliciousColors.SurfaceSelected
            } else {
                SugarliciousColors.Surface
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FaceDial(
                index = index,
                state = state,
                activeComplicationIds = activeComplicationIds,
                modifier = Modifier.size(116.dp),
            )

            Text(
                text = face.name,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                color = SugarliciousColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
