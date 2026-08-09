package app.aapswear.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.aapswear.mobile.ui.theme.SugarliciousColors
import app.aapswear.model.TherapyDisplayState
import app.aapswear.model.Trend
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val watchFaces = listOf(
    "Sugarlicious Analog",
    "Sugarlicious Orbit",
    "Sugarlicious Rings",
    "Sugarlicious Graph",
)
private const val carouselPages = 400

private object GalaxyWatchUltraFrameLoader {
    private val mutex = Mutex()
    private var cached: androidx.compose.ui.graphics.ImageBitmap? = null

    suspend fun load(context: Context): androidx.compose.ui.graphics.ImageBitmap? {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                val svg = context.resources.openRawResource(R.raw.galaxy_watch_ultra_mockup_exact)
                    .bufferedReader()
                    .use { reader -> reader.readText() }
                val marker = "data:image/png;base64,"
                val start = svg.indexOf(marker)
                if (start < 0) return@withContext null
                val dataStart = start + marker.length
                val dataEnd = svg.indexOf('"', dataStart)
                if (dataEnd <= dataStart) return@withContext null
                val bytes = Base64.decode(svg.substring(dataStart, dataEnd), Base64.DEFAULT)
                if (bytes.size < 8 ||
                    bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte() ||
                    bytes[2] != 0x4e.toByte() || bytes[3] != 0x47.toByte()
                ) {
                    return@withContext null
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { bitmap ->
                    bitmap.prepareToDraw()
                }?.asImageBitmap()
            }.also { decoded -> cached = decoded }
        }
    }
}

internal fun carouselTargetPage(currentPage: Int, dragDistance: Float, pageCount: Int = carouselPages): Int {
    if (abs(dragDistance) < 24f) return currentPage.coerceIn(0, pageCount - 1)
    val direction = if (dragDistance < 0f) 1 else -1
    return (currentPage + direction).coerceIn(0, pageCount - 1)
}

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
    val carouselScope = rememberCoroutineScope()
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

        BoxWithConstraints(
            Modifier.fillMaxWidth().height(166.dp),
            contentAlignment = Alignment.Center,
        ) {
            val oneStepSwipe = Modifier.pointerInput(pager.settledPage) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val target = carouselTargetPage(pager.settledPage, dragDistance)
                        if (target != pager.settledPage) {
                            carouselScope.launch { pager.animateScrollToPage(target) }
                        }
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            }
            val faceSize = 100.dp
            val centeredPadding = ((maxWidth - faceSize) / 2).coerceAtLeast(0.dp)
            GalaxyWatchUltraFrame()
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = centeredPadding),
                pageSpacing = 6.dp,
                pageSize = PageSize.Fixed(faceSize),
                userScrollEnabled = false,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val index = page % watchFaces.size
                Box(
                    modifier = Modifier
                        .offset(y = (-5).dp)
                        .graphicsLayer {
                            val distance = abs(
                                (pager.currentPage - page) + pager.currentPageOffsetFraction,
                            ).coerceIn(0f, 1f)
                            val scale = lerp(1f, 0.73f, distance)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.52f, distance)
                        }
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    FaceDial(index, state, now)
                }
            }
            Box(
                Modifier.matchParentSize()
                    .then(oneStepSwipe)
                    .clickable(onClick = onEdit),
            )
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
    val context = LocalContext.current.applicationContext
    val frame by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, context) {
        value = GalaxyWatchUltraFrameLoader.load(context)
    }
    frame?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Galaxy Watch Ultra",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
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
        Modifier.size(100.dp).clip(CircleShape).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            repeat(12) { tick ->
                val angle = Math.toRadians((tick * 30.0) - 90.0)
                val major = tick % 3 == 0
                val inner = radius - if (major) 11.dp.toPx() else 8.dp.toPx()
                val outer = radius - 4.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = if (major) 0.92f else 0.38f),
                    start = Offset(
                        center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner,
                    ),
                    end = Offset(
                        center.x + cos(angle).toFloat() * outer,
                        center.y + sin(angle).toFloat() * outer,
                    ),
                    strokeWidth = (if (major) 2.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
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
