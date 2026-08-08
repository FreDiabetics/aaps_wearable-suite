package app.aapswear.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val watchFaces = listOf("AAPS", "AAPS V2", "AAPS V2 Dark")
private const val carouselPages = 300

@Composable
internal fun OverviewWatchFaceTile(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    selectedFaceIndex: Int,
    now: Long,
    onSelectedFace: (Int) -> Unit,
    onEdit: () -> Unit,
    onSync: () -> Unit,
) {
    val selected = selectedFaceIndex.coerceIn(0, watchFaces.lastIndex)
    val midpoint = carouselPages / 2
    val aligned = midpoint - midpoint % watchFaces.size
    val pager = rememberPagerState(
        initialPage = aligned + selected,
        pageCount = { carouselPages },
    )

    LaunchedEffect(pager.settledPage) {
        val index = pager.settledPage % watchFaces.size
        if (index != selected) onSelectedFace(index)
    }

    val connected = diagnostics.reachableWatches > 0
    val syncOk = diagnostics.syncStatus == "ok"
    val statusColor = when {
        connected && syncOk -> SugarliciousColors.Primary
        connected -> SugarliciousColors.Yellow
        else -> SugarliciousColors.Red
    }
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(SugarliciousColors.SurfaceHigh, SugarliciousColors.Surface)),
                shape,
            )
            .border(1.dp, SugarliciousColors.Border.copy(alpha = 0.85f), shape)
            .clip(shape)
            .padding(vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "GALAXY WATCH",
                    color = SugarliciousColors.TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.55.sp,
                )
                Text(
                    when {
                        connected && syncOk -> "Watch verbunden"
                        connected -> "Watch erreichbar"
                        else -> "Keine Watch erreichbar"
                    },
                    color = SugarliciousColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PillAction("BEARBEITEN", onEdit)
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentPadding = PaddingValues(horizontal = 88.dp),
            pageSpacing = 8.dp,
            pageSize = PageSize.Fixed(168.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val index = page % watchFaces.size
            val distance = (pager.currentPage - page).absoluteValue
            Column(
                modifier = Modifier.graphicsLayer {
                    val scale = if (distance == 0) 1f else 0.78f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (distance == 0) 1f else 0.46f
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GalaxyWatchPreview(index, state, now)
                Spacer(Modifier.height(3.dp))
                Text(
                    watchFaces[index],
                    color = if (distance == 0) SugarliciousColors.TextPrimary else SugarliciousColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(
                Modifier.weight(1f),
                "SYNC",
                if (diagnostics.lastSyncAt > 0L) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(diagnostics.lastSyncAt)) else "—",
                statusColor,
            )
            StatusChip(
                Modifier.weight(1.2f),
                "NIGHTSCOUT",
                when (diagnostics.historyBackfillStatus) {
                    "ok" -> "${diagnostics.historyBackfillPointCount} Punkte"
                    "not_configured" -> "nicht aktiv"
                    else -> diagnostics.historyBackfillStatus ?: "—"
                },
                if (diagnostics.historyBackfillStatus == "ok") SugarliciousColors.Primary else SugarliciousColors.TextSecondary,
            )
            StatusChip(
                Modifier.weight(0.72f),
                "HANDY",
                state?.device?.phoneBatteryPercent?.let { "$it%" } ?: "—",
                SugarliciousColors.TextSecondary,
            )
            Surface(
                modifier = Modifier.height(38.dp).clickable(onClick = onSync),
                shape = RoundedCornerShape(14.dp),
                color = SugarliciousColors.Primary,
            ) {
                Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                    Text("SENDEN", color = SugarliciousColors.OnPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "${watchFaces[pager.currentPage % watchFaces.size]} · nach links/rechts wischen",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            color = SugarliciousColors.TextSecondary,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun GalaxyWatchPreview(index: Int, state: TherapyDisplayState?, now: Long) {
    val faceBackground = when (index) {
        0 -> Color(0xFF111315)
        1 -> Color(0xFF090B0C)
        else -> Color.Black
    }
    val glucose = state?.glucose?.valueMgDl?.roundToInt()?.toString() ?: "—"
    val trend = when (state?.glucose?.trend) {
        Trend.DOUBLE_UP -> "⇈"
        Trend.SINGLE_UP -> "↑"
        Trend.FORTY_FIVE_UP -> "↗"
        Trend.FLAT -> "→"
        Trend.FORTY_FIVE_DOWN -> "↘"
        Trend.SINGLE_DOWN -> "↓"
        Trend.DOUBLE_DOWN -> "⇊"
        else -> "·"
    }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now))

    Box(Modifier.size(width = 138.dp, height = 126.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(42.dp).height(126.dp)
                .background(SugarliciousColors.SurfaceRaised, RoundedCornerShape(20.dp))
                .border(1.dp, SugarliciousColors.Border, RoundedCornerShape(20.dp)),
        )
        Box(
            Modifier.size(108.dp)
                .background(SugarliciousColors.SurfaceSelected, CircleShape)
                .border(3.dp, SugarliciousColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(faceBackground),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(time, color = Color.White.copy(alpha = 0.66f), fontSize = 7.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(glucose, color = if (index == 2) SugarliciousColors.Primary else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(2.dp))
                    Text(trend, color = SugarliciousColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "IOB ${state?.insulin?.totalIob?.let { String.format("%.1f", it) } ?: "—"} · COB ${state?.carbs?.cobGrams?.roundToInt() ?: 0}",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 5.sp,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 7.dp).width(7.dp).height(24.dp)
                .background(SugarliciousColors.SurfaceRaised, RoundedCornerShape(999.dp))
                .border(1.dp, SugarliciousColors.Border, RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun StatusChip(modifier: Modifier, label: String, value: String, accent: Color) {
    Column(
        modifier = modifier.height(38.dp).background(SugarliciousColors.SurfaceRaised, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = SugarliciousColors.TextSecondary, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = accent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PillAction(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = SugarliciousColors.SurfaceSelected,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = SugarliciousColors.Primary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        )
    }
}
