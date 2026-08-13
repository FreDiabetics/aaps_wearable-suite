from pathlib import Path
import re

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)

def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, got {count}")
    return updated

def write(path: str, text: str) -> None:
    p = Path(path)
    p.write_text("\n".join(line.rstrip() for line in text.splitlines()) + "\n", encoding="utf-8")
    print(f"updated {path}")

# Dashboard chart: shared/live edge, bold right labels, target value line
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardCharts.kt"
text = Path(path).read_text(encoding="utf-8")
text = replace_once(
    text,
    """        futureWindowMs = next
        notifyChanged()
""",
    """        futureWindowMs = next
        clampPan()
        notifyChanged()
""",
    "future window pan clamp",
)
text = replace_once(
    text,
    """            panMs.coerceIn(
                -24L * HOUR_MS,
                2L * HOUR_MS,
            )
""",
    """            panMs.coerceIn(
                -24L * HOUR_MS,
                0L,
            )
""",
    "future pan upper bound",
)
text = replace_once(
    text,
    """    private var showPredictions = false
    private var showTargetRange = false
    private var showBasal = false
""",
    """    private var showPredictions = false
    private var showTargetRange = false
    private var showTargetValue = false
    private var showBasal = false
""",
    "target value chart field",
)
text = replace_once(
    text,
    """        durationHours: Int,
        showTargetRange: Boolean = false,
        showBasal: Boolean = false,
""",
    """        durationHours: Int,
        showTargetRange: Boolean = false,
        showTargetValue: Boolean = false,
        showBasal: Boolean = false,
""",
    "target value bind parameter",
)
text = replace_once(
    text,
    """        this.showTargetRange =
            showTargetRange
        this.showBasal =
""",
    """        this.showTargetRange =
            showTargetRange
        this.showTargetValue =
            showTargetValue
        this.showBasal =
""",
    "target value bind assignment",
)
text = replace_once(
    text,
    """            val end = viewport.endEpochMs(now)

            val start = end - (viewport.hours * HOUR_MS).toLong()
""",
    """            val liveEdge =
                if (viewport.futureWindowMs == 0L) {
                    state?.glucose?.measuredAtEpochMs
                        ?.coerceAtMost(now)
                        ?: now
                } else {
                    now
                }
            val end = viewport.endEpochMs(liveEdge)

            val start = end - (viewport.hours * HOUR_MS).toLong()
""",
    "CGM live edge",
)
text = replace_once(
    text,
    """            }
            drawGrid(canvas, plot, start, end)
            if (
                showBasal
""",
    """            }
            if (
                showTargetValue
            ) {
                val targetValue =
                    (
                        targetLow +
                            targetHigh
                        ) /
                        2.0
                val targetValueY =
                    mapGlucoseY(
                        targetValue,
                        plot,
                    )
                linePaint.color =
                    withAlpha(
                        SugarliciousColors.argb(
                            SugarliciousColorRole.GRAPH_LABEL,
                        ),
                        210,
                    )
                linePaint.strokeWidth =
                    1.2f.dp
                linePaint.pathEffect =
                    DashPathEffect(
                        floatArrayOf(
                            5f.dp,
                            4f.dp,
                        ),
                        0f,
                    )
                canvas.drawLine(
                    plot.left,
                    targetValueY,
                    plot.right,
                    targetValueY,
                    linePaint,
                )
                linePaint.pathEffect =
                    null
                drawText(
                    canvas,
                    "Ziel ${glucoseLabel(targetValue)}",
                    plot.right - 1f.dp,
                    (
                        targetValueY -
                            4f.dp
                        ).coerceAtLeast(
                        plot.top + 11f.dp,
                    ),
                    9f,
                    SugarliciousColors.argb(
                        SugarliciousColorRole.GRAPH_LABEL,
                    ),
                    Paint.Align.RIGHT,
                )
            }
            drawGrid(canvas, plot, start, end)
            if (
                showBasal
""",
    "current target line",
)
target_x_old = "                x = plot.right - 8f.dp,\n"
if text.count(target_x_old) < 3:
    raise RuntimeError("right-edge target labels: expected at least three old right-edge labels")
text = text.replace(
    target_x_old,
    "                x = plot.right - 1f.dp,\n",
    2,
)
zero_label_pattern = (
    r'\n\s*drawText\(\s*canvas,\s*"0",\s*plot\.right -\s*8f\.dp,\s*'
    r'zeroY -\s*4f\.dp,\s*8f,\s*SugarliciousColors\.argb\(\s*'
    r'SugarliciousColorRole\.GRAPH_LABEL,\s*\),\s*Paint\.Align\.RIGHT,\s*\)\n'
)
text = regex_once(text, zero_label_pattern, "\n", "remove CGM zero label")
text = replace_once(
    text,
    """    private fun drawTargetLabel(canvas: Canvas, value: String, x: Float, y: Float) =
        drawText(canvas, value, x, y, 9f, Color.WHITE, Paint.Align.RIGHT)
""",
    """    private fun drawTargetLabel(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
    ) {
        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG,
            ).apply {
                textSize =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        10f,
                        resources.displayMetrics,
                    )
                color =
                    SugarliciousColors.argb(
                        SugarliciousColorRole.GRAPH_LABEL,
                    )
                textAlign =
                    Paint.Align.RIGHT
                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD
            }

        canvas.drawText(
            value,
            x,
            y,
            paint,
        )
    }
""",
    "bold target labels",
)
text = replace_once(
    text,
    """            val end =
                viewport.endEpochMs(
                    System.currentTimeMillis(),
                )
            val start =
""",
    """            val now =
                System.currentTimeMillis()
            val liveEdge =
                if (viewport.futureWindowMs == 0L) {
                    state?.glucose?.measuredAtEpochMs
                        ?.coerceAtMost(now)
                        ?: now
                } else {
                    now
                }
            val end =
                viewport.endEpochMs(
                    liveEdge,
                )
            val start =
""",
    "metabolic live edge",
)
write(path, text)

# Tests for requested defaults and live future edge
path = "app-mobile/src/test/kotlin/app/aapswear/mobile/MainActivityTest.kt"
text = Path(path).read_text(encoding="utf-8")
text = replace_once(
    text,
    """    @Test fun `fresh install defaults to CGM dots only`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        val ui = DashboardUiPreferences.read(preferences)
        assertTrue(ui.showCgmGraph)
        assertFalse(ui.showCgmTargetRange)
        assertFalse(ui.showCgmBasal)
        assertFalse(ui.showCgmActivity)
        assertFalse(ui.anyCgmPredictionEnabled)
        assertFalse(ui.showMetabolicGraph)

        controller.pause().stop().destroy()
    }
""",
    """    @Test fun `fresh install uses requested overview and CGM defaults`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        val ui = DashboardUiPreferences.read(preferences)
        assertTrue(ui.showCgmGraph)
        assertTrue(ui.showDetails)
        assertTrue(ui.showCgmTargetRange)
        assertFalse(ui.showCgmTargetValue)
        assertFalse(ui.showCgmBasal)
        assertFalse(ui.showCgmActivity)
        assertFalse(ui.anyCgmPredictionEnabled)
        assertFalse(ui.showMetabolicGraph)

        controller.pause().stop().destroy()
    }
""",
    "fresh install default test",
)
write(path, text)

path = "app-mobile/src/test/kotlin/app/aapswear/mobile/DashboardChartsTest.kt"
text = Path(path).read_text(encoding="utf-8")
marker = "    private fun render(view: View, height: Int): Bitmap {\n"
test = """    @Test fun `viewport cannot pan beyond configured future edge`() {
        val viewport = ChartViewport(6)
        val now = 10_000_000L

        viewport.pan(-420f, 420f)

        assertEquals(0L, viewport.panMs)
        assertEquals(now, viewport.endEpochMs(now))

        viewport.setFutureWindow(60L * 60_000L)
        viewport.pan(-420f, 420f)

        assertEquals(0L, viewport.panMs)
        assertEquals(
            now + 60L * 60_000L,
            viewport.endEpochMs(now),
        )
    }

"""
text = replace_once(text, marker, test + marker, "viewport live edge test")
write(path, text)

# Overview watch model name
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/OverviewWatchFaceTile.kt"
text = Path(path).read_text(encoding="utf-8")
text = regex_once(
    text,
    r'("Galaxy Watch Ultra",\s*color = SugarliciousColors\.TextPrimary,\s*fontSize = )16\.sp,',
    r'\g<1>20.sp,',
    "larger watch model name",
)
write(path, text)

