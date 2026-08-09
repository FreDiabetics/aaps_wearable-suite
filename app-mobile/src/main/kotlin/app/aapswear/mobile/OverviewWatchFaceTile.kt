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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val watchFaces = listOf(
    "Sugarlicious Analog",
    "Sugarlicious Orbit",
    "Sugarlicious Rings",
    "Sugarlicious Graph",
)
private const val carouselPages = 400

@Composable
internal fun OverviewWatchFaceTile(
    state: TherapyDisplayState?,
    diagnostics: DiagnosticsSnapshot,
    selectedFaceIndex: Int,
    now: Long,
    onSelectedFace: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val selected = selectedFaceIndex.coerceIn(0, watchFaces.lastIndex)
    val midpoint = carouselPages / 2
    val aligned = midpoint - midpoint % watchFaces.size
    val pager = rememberPagerState(initialPage = aligned + selected, pageCount = { carouselPages })
    LaunchedEffect(pager.settledPage) {
        val index = pager.settledPage % watchFaces.size
        if (index != selected) onSelectedFace(index)
    }

    val connected = diagnostics.reachableWatches > 0
    val error = connected && diagnostics.syncStatus !in listOf(null, "ok", "pending")
    val currentIndex = pager.currentPage % watchFaces.size

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "Galaxy Watch Ultra",
            color = SugarliciousColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Box(Modifier.fillMaxWidth().height(166.dp), contentAlignment = Alignment.Center) {
            GalaxyWatchUltraFrame()
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 118.dp),
                pageSpacing = 2.dp,
                pageSize = PageSize.Fixed(116.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val index = page % watchFaces.size
                val distance = (pager.currentPage - page).absoluteValue
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = if (distance == 0) 1f else 0.73f
                            scaleX = scale
                            scaleY = scale
                            alpha = if (distance == 0) 1f else 0.52f
                        }
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    FaceDial(index, state, now)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = SugarliciousColors.SurfaceHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        error -> "!"
                        connected -> "●"
                        else -> "—"
                    },
                    color = when {
                        error -> SugarliciousColors.Red
                        connected -> SugarliciousColors.Primary
                        else -> SugarliciousColors.TextSecondary
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (connected) {
                    Spacer(Modifier.width(6.dp))
                    Text("Verbunden", color = SugarliciousColors.TextPrimary, fontSize = 10.sp)
                }
            }
        }
        Text(
            watchFaces[currentIndex],
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GalaxyWatchUltraFrame() {
    Box(
        Modifier.size(146.dp)
            .background(Color(0xFF767676), RoundedCornerShape(43.dp))
            .border(2.dp, Color(0xFFAAAAAA), RoundedCornerShape(43.dp)),
    ) {
        Box(
            Modifier.align(Alignment.CenterEnd).offset(x = 7.dp).width(10.dp).height(46.dp)
                .background(Color(0xFF666666), RoundedCornerShape(7.dp))
                .border(1.dp, Color(0xFFB0B0B0), RoundedCornerShape(7.dp)),
        )
        Box(
            Modifier.align(Alignment.CenterEnd).offset(x = 10.dp, y = (-16).dp).size(13.dp)
                .background(Color(0xFFFF6C2C), CircleShape)
                .border(2.dp, Color(0xFF333333), CircleShape),
        )
        Box(
            Modifier.align(Alignment.Center).size(126.dp)
                .background(Color.Black, CircleShape)
                .border(3.dp, Color(0xFF343434), CircleShape),
        )
    }
}

@Composable
private fun FaceDial(index: Int, state: TherapyDisplayState?, now: Long) {
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
    val accent = when (index) {
        1 -> Color(0xFFFF8B60)
        2 -> SugarliciousColors.Primary
        3 -> SugarliciousColors.Secondary
        else -> Color.White
    }
    Box(
        Modifier.size(116.dp).clip(CircleShape).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        repeat(12) { tick ->
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 5.dp)
                    .graphicsLayer { rotationZ = tick * 30f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 5.3f) }
                    .width(if (tick % 3 == 0) 2.dp else 1.dp).height(6.dp)
                    .background(Color.White.copy(alpha = if (tick % 3 == 0) 0.9f else 0.35f), RoundedCornerShape(99.dp)),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), color = Color.White.copy(alpha = 0.68f), fontSize = 7.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(glucose, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp))
                Text(trend, color = SugarliciousColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "IOB ${state?.insulin?.totalIob?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"}",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 5.5.sp,
            )
        }
    }
}
