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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

internal val sugarliciousWatchFaceNames = listOf(
    "Sugarlicious Analog",
    "Sugarlicious Orbit",
    "Sugarlicious Rings",
    "Sugarlicious Graph",
)
private const val carouselPages = 400
private val carouselHeight = 224.dp
private val carouselFaceSize = 135.dp
private val carouselPageSpacing = 8.dp
private val carouselFaceVerticalOffset = (-7).dp

private object GalaxyWatchUltraFrameLoader {
    private val mutex = Mutex()
    @Volatile
    private var cached: androidx.compose.ui.graphics.ImageBitmap? = null

    fun cachedOrNull(): androidx.compose.ui.graphics.ImageBitmap? = cached

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

internal fun carouselPageVisibility(distanceFromCenter: Float): Float = when {
    distanceFromCenter <= 1f -> 1f
    distanceFromCenter >= 1.25f -> 0f
    else -> (1.25f - distanceFromCenter) / 0.25f
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
    val selected = selectedFaceIndex.coerceIn(0, sugarliciousWatchFaceNames.lastIndex)
    val midpoint = carouselPages / 2
    val aligned = midpoint - midpoint % sugarliciousWatchFaceNames.size
    val pager = rememberPagerState(initialPage = aligned + selected, pageCount = { carouselPages })
    val carouselScope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) {
        val index = pager.settledPage % sugarliciousWatchFaceNames.size
        if (index != selected) onSelectedFace(index)
    }

    val syncStatus = diagnostics.syncStatus
    val connected =
        diagnostics.reachableWatches > 0 ||
            syncStatus == "ok"
    val pending =
        !connected &&
            syncStatus == "pending"
    val error =
        syncStatus !in listOf(
            null,
            "ok",
            "pending",
        )
    val currentIndex = pager.currentPage % sugarliciousWatchFaceNames.size

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
            Modifier.fillMaxWidth().height(carouselHeight).clipToBounds(),
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
            val centeredPadding = ((maxWidth - carouselFaceSize) / 2).coerceAtLeast(0.dp)
            GalaxyWatchUltraFrame()
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = centeredPadding),
                pageSpacing = carouselPageSpacing,
                pageSize = PageSize.Fixed(carouselFaceSize),
                userScrollEnabled = false,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val index = page % sugarliciousWatchFaceNames.size
                Box(
                    modifier = Modifier
                        .offset(y = carouselFaceVerticalOffset)
                        .graphicsLayer {
                            val rawDistance = abs(
                                (pager.currentPage - page) + pager.currentPageOffsetFraction,
                            )
                            val distance = rawDistance.coerceIn(0f, 1f)
                            val scale = lerp(1f, 0.73f, distance)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.52f, distance) * carouselPageVisibility(rawDistance)
                        }
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    FaceDial(
                        index = index,
                        state = state,
                        now = now,
                        modifier = Modifier.size(carouselFaceSize),
                    )
                }
            }
            Box(
                Modifier.matchParentSize()
                    .then(oneStepSwipe)
                    .clickable(onClick = onEdit),
            )
        }

        val statusColor =
            when {
                connected ->
                    SugarliciousColors.Primary

                pending ->
                    SugarliciousColors.Yellow

                error ->
                    SugarliciousColors.Red

                else ->
                    SugarliciousColors.Red
            }

        val statusText =
            when {
                connected ->
                    "Verbunden"

                pending ->
                    "Verbindung wird geprüft"

                else ->
                    "Nicht verbunden"
            }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = statusColor.copy(alpha = 0.14f),
            border =
                androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = statusColor.copy(alpha = 0.72f),
                ),
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Image(
                    painter =
                        painterResource(
                            R.drawable.ic_watch_status,
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                alpha = 1f
                            },
                    colorFilter =
                        androidx.compose.ui.graphics.ColorFilter.tint(
                            statusColor,
                        ),
                )

                Spacer(
                    Modifier.width(6.dp),
                )

                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            sugarliciousWatchFaceNames[currentIndex],
            color = SugarliciousColors.TextSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GalaxyWatchUltraFrame() {
    val context = LocalContext.current.applicationContext
    val frame by produceState(
        initialValue = GalaxyWatchUltraFrameLoader.cachedOrNull(),
        key1 = context,
    ) {
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
internal fun FaceDial(
    index: Int,
    state: TherapyDisplayState?,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val glucose =
        state?.glucose
            ?.valueMgDl
            ?.roundToInt()
            ?.toString()
            ?: "—"

    val trend =
        when (
            state?.glucose
                ?.trend
        ) {
            Trend.DOUBLE_UP ->
                "⇈"

            Trend.SINGLE_UP ->
                "↑"

            Trend.FORTY_FIVE_UP ->
                "↗"

            Trend.FLAT ->
                "→"

            Trend.FORTY_FIVE_DOWN ->
                "↘"

            Trend.SINGLE_DOWN ->
                "↓"

            Trend.DOUBLE_DOWN ->
                "⇊"

            else ->
                "·"
        }

    val accent =
        when (index) {
            1 ->
                Color(
                    0xFF19D7E8,
                )

            2 ->
                Color(
                    0xFFFF8B60,
                )

            3 ->
                SugarliciousColors.Primary

            else ->
                Color.White
        }

    val calendar =
        java.util.Calendar
            .getInstance()
            .apply {
                timeInMillis =
                    now
            }

    val hour =
        calendar.get(
            java.util.Calendar.HOUR,
        )

    val minute =
        calendar.get(
            java.util.Calendar.MINUTE,
        )

    val second =
        calendar.get(
            java.util.Calendar.SECOND,
        )

    Box(
        modifier
            .clip(
                CircleShape,
            )
            .background(
                Color.Black,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Canvas(
            Modifier.fillMaxSize(),
        ) {
            val radius =
                size.minDimension /
                    2f

            if (
                index == 1 ||
                index == 2
            ) {
                drawCircle(
                    color =
                        accent.copy(
                            alpha =
                                if (
                                    index == 1
                                ) {
                                    0.18f
                                } else {
                                    0.11f
                                },
                        ),
                    radius =
                        radius -
                            8.dp.toPx(),
                    center =
                        center,
                    style =
                        androidx.compose.ui.graphics.drawscope.Stroke(
                            width =
                                if (
                                    index == 2
                                ) {
                                    7.dp.toPx()
                                } else {
                                    4.dp.toPx()
                                },
                        ),
                )
            }

            repeat(
                60,
            ) { tick ->
                val angle =
                    Math.toRadians(
                        tick *
                            6.0 -
                            90.0,
                    )

                val major =
                    tick % 5 ==
                        0

                val inner =
                    radius -
                        if (major) {
                            13.dp.toPx()
                        } else {
                            7.dp.toPx()
                        }

                val outer =
                    radius -
                        3.dp.toPx()

                drawLine(
                    color =
                        if (major) {
                            Color.White.copy(
                                alpha =
                                    0.86f,
                            )
                        } else {
                            Color.White.copy(
                                alpha =
                                    0.22f,
                            )
                        },
                    start =
                        Offset(
                            x =
                                center.x +
                                    cos(
                                        angle,
                                    ).toFloat() *
                                    inner,
                            y =
                                center.y +
                                    sin(
                                        angle,
                                    ).toFloat() *
                                    inner,
                        ),
                    end =
                        Offset(
                            x =
                                center.x +
                                    cos(
                                        angle,
                                    ).toFloat() *
                                    outer,
                            y =
                                center.y +
                                    sin(
                                        angle,
                                    ).toFloat() *
                                    outer,
                        ),
                    strokeWidth =
                        if (major) {
                            1.7.dp.toPx()
                        } else {
                            0.7.dp.toPx()
                        },
                    cap =
                        StrokeCap.Round,
                )
            }

            fun handEnd(
                angleDegrees: Double,
                length: Float,
            ): Offset {
                val angle =
                    Math.toRadians(
                        angleDegrees -
                            90.0,
                    )

                return Offset(
                    x =
                        center.x +
                            cos(
                                angle,
                            ).toFloat() *
                            length,
                    y =
                        center.y +
                            sin(
                                angle,
                            ).toFloat() *
                            length,
                )
            }

            val hourAngle =
                (
                    hour %
                        12
                    ) *
                    30.0 +
                    minute /
                    60.0 *
                    30.0

            val minuteAngle =
                minute *
                    6.0 +
                    second /
                    60.0 *
                    6.0

            val secondAngle =
                second *
                    6.0

            drawLine(
                color =
                    Color.White,
                start =
                    center,
                end =
                    handEnd(
                        hourAngle,
                        radius *
                            0.48f,
                    ),
                strokeWidth =
                    5.dp.toPx(),
                cap =
                    StrokeCap.Round,
            )

            drawLine(
                color =
                    Color.White,
                start =
                    center,
                end =
                    handEnd(
                        minuteAngle,
                        radius *
                            0.70f,
                    ),
                strokeWidth =
                    3.dp.toPx(),
                cap =
                    StrokeCap.Round,
            )

            drawLine(
                color =
                    accent,
                start =
                    handEnd(
                        secondAngle +
                            180.0,
                        radius *
                            0.16f,
                    ),
                end =
                    handEnd(
                        secondAngle,
                        radius *
                            0.78f,
                    ),
                strokeWidth =
                    1.2.dp.toPx(),
                cap =
                    StrokeCap.Round,
            )

            drawCircle(
                color =
                    accent,
                radius =
                    4.dp.toPx(),
                center =
                    center,
            )

            drawCircle(
                color =
                    Color.Black,
                radius =
                    1.8.dp.toPx(),
                center =
                    center,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomCenter,
                    )
                    .padding(
                        bottom =
                            20.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    glucose,
                    color =
                        accent,
                    fontSize =
                        15.sp,
                    fontWeight =
                        FontWeight.Bold,
                )

                Spacer(
                    Modifier.width(
                        2.dp,
                    ),
                )

                Text(
                    trend,
                    color =
                        SugarliciousColors.Primary,
                    fontSize =
                        9.sp,
                    fontWeight =
                        FontWeight.Bold,
                )
            }

            Text(
                "IOB ${
                    state?.insulin
                        ?.totalIob
                        ?.let {
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                it,
                            )
                        }
                        ?: "—"
                }",
                color =
                    Color.White.copy(
                        alpha =
                            0.58f,
                    ),
                fontSize =
                    5.5.sp,
            )
        }
    }
}