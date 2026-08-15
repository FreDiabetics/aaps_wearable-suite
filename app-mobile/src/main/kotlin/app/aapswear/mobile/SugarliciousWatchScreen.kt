package app.aapswear.mobile

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
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
        SugarliciousWatchFaceCard(
            name = "Sugarlicious Digital",
            style = "Digital",
            slots = 1,
            features = listOf("Glukose", "AOD"),
        ),
    )

internal data class LegacyWatchFaceCard(val name: String, val previewRes: Int)

internal val legacyWatchFaceCards = listOf(
    LegacyWatchFaceCard("AAPS BigChart", R.drawable.legacy_aaps_big_chart),
    LegacyWatchFaceCard("AAPS Circle", R.drawable.legacy_aaps_circle),
    LegacyWatchFaceCard("AAPS Cockpit", R.drawable.legacy_aaps_cockpit),
    LegacyWatchFaceCard("AAPS Community", R.drawable.legacy_aaps_community),
    LegacyWatchFaceCard("AAPS Digital Style", R.drawable.legacy_aaps_digital_style),
    LegacyWatchFaceCard("AAPS Large", R.drawable.legacy_aaps_large),
    LegacyWatchFaceCard("AAPS NoChart", R.drawable.legacy_aaps_no_chart),
    LegacyWatchFaceCard("AAPS Standard", R.drawable.legacy_aaps_standard),
    LegacyWatchFaceCard("AAPS V2", R.drawable.legacy_aaps_v2),
    LegacyWatchFaceCard("AAPS V2 TT DarkOnly", R.drawable.legacy_aaps_v2_tt_dark),
    LegacyWatchFaceCard("AAPS V4", R.drawable.legacy_aaps_v4),
    LegacyWatchFaceCard("AIMICO", R.drawable.legacy_aimico),
    LegacyWatchFaceCard("Analog G-Watch", R.drawable.legacy_analog_g_watch),
    LegacyWatchFaceCard("Blue Ring", R.drawable.legacy_blue_ring),
    LegacyWatchFaceCard("Digital Big Graph", R.drawable.legacy_digital_big_graph),
    LegacyWatchFaceCard("Digital G-Watch", R.drawable.legacy_digital_g_watch),
    LegacyWatchFaceCard("Gears", R.drawable.legacy_gears),
    LegacyWatchFaceCard("Gota", R.drawable.legacy_gota),
    LegacyWatchFaceCard("LuckyLoopKoeln", R.drawable.legacy_lucky_loop_koeln),
    LegacyWatchFaceCard("P-Zero", R.drawable.legacy_p_zero),
    LegacyWatchFaceCard("Robby", R.drawable.legacy_robby),
    LegacyWatchFaceCard("Simple Digital", R.drawable.legacy_simple_digital),
    LegacyWatchFaceCard("AAPS SteamPunk", R.drawable.legacy_steam_punk),
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
    val scope = rememberCoroutineScope()
    var runtimeStatus by remember { mutableStateOf(WatchRuntimeStatusStore.read(appContext)) }
    var activeFaceIndex by remember {
        mutableStateOf<Int?>(runtimeStatus.activeSugarliciousFaceIndex ?: preferences.watchFaceIndex)
    }
    var editingFaceIndex by remember {
        mutableStateOf(runtimeStatus.activeSugarliciousFaceIndex ?: preferences.watchFaceIndex)
    }
    var facePresets by remember { mutableStateOf(WatchFacePresetStore.readAll(appContext)) }
    var showLegacyFaces by remember { mutableStateOf(false) }

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
        WatchMenuHeader(
            onBack = { onNavigate(DashboardScreen.OVERVIEW) },
            onSettings = { onNavigate(DashboardScreen.SETTINGS) },
        )

        Text(
            text = "Ziffernblätter",
            modifier = Modifier.fillMaxWidth(),
            color = SugarliciousColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
        )
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
                        activeComplicationIds = facePresets.getOrElse(index) { emptyList() },
                        selected = activeFaceIndex == index,
                        onSelected = {
                            editingFaceIndex = index
                            val activated = WatchFacePresetStore.activate(appContext, index)
                            facePresets =
                                WatchFacePresetStore.readAll(appContext).toMutableList().also {
                                    it[index] = activated
                                }
                            onSelectedFace(index)
                        },
                    )
                }

                if (indices.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        TextButton(
            onClick = { showLegacyFaces = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Alte AAPS-Watchfaces anzeigen") }

        Text(
            text = "Complications",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = SugarliciousColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
        )

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

    if (showLegacyFaces) {
        AlertDialog(
            onDismissRequest = { showLegacyFaces = false },
            title = { Text("Alte AAPS-Watchfaces") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(legacyWatchFaceCards) { index, face ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    val nodes = runCatching { requestWatchFaceApply(appContext, 5 + index) }.getOrDefault(0)
                                    Toast.makeText(
                                        context,
                                        if (nodes > 0) "${face.name} wird an die Watch gesendet" else "Watch nicht erreichbar",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            color = SugarliciousColors.SurfaceHigh,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Image(
                                    painter = painterResource(face.previewRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(58.dp),
                                )
                                Text(face.name, color = SugarliciousColors.TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLegacyFaces = false }) { Text("Schließen") } },
        )
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
