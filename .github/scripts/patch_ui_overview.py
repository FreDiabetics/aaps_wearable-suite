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

# Dashboard preferences + settings
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt"
text = Path(path).read_text(encoding="utf-8")
text = replace_once(
    text,
    """    val showCgmGraph: Boolean = true,
    val showCgmTargetRange: Boolean = false,
    val showCgmBasal: Boolean = false,
""",
    """    val showCgmGraph: Boolean = true,
    val showCgmTargetRange: Boolean = true,
    val showCgmTargetValue: Boolean = false,
    val showCgmBasal: Boolean = false,
""",
    "CGM target preference fields",
)
text = replace_once(
    text,
    """                showCgmTargetRange =
                    preferences.getBoolean(
                        "cgm.targetRange",
                        false,
                    ),
                showCgmBasal =
""",
    """                showCgmTargetRange =
                    preferences.getBoolean(
                        "cgm.targetRange",
                        true,
                    ),
                showCgmTargetValue =
                    preferences.getBoolean(
                        "cgm.targetValue",
                        false,
                    ),
                showCgmBasal =
""",
    "CGM target preference read",
)
text = replace_once(
    text,
    """                    addView(
                        switchRowCompact(
                            "Zielbereich",
                            preferences.showCgmTargetRange,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetRange", it) },
                    )
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Basal",
""",
    """                    addView(
                        switchRowCompact(
                            "Zielbereich",
                            preferences.showCgmTargetRange,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetRange", it) },
                    )
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Aktueller Zielwert",
                            preferences.showCgmTargetValue,
                            View.generateViewId(),
                        ) { callbacks.setCgmStream("cgm.targetValue", it) },
                    )
                    addView(divider())
                    addView(
                        switchRowCompact(
                            "Basal",
""",
    "current target settings toggle",
)
write(path, text)

# Requested default baseline migration
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt"
text = Path(path).read_text(encoding="utf-8")
marker = "        content = findViewById(R.id.dashboard_content)\n"
migration = """        if (!uiPreferences.getBoolean("overviewDefaultsMigratedV2", false)) {
            uiPreferences.edit {
                putBoolean("showCgmGraph", true)
                putBoolean("showDetails", true)
                putBoolean("showMetabolicGraph", false)
                putBoolean("showPredictions", false)
                putBoolean("cgm.targetRange", true)
                putBoolean("cgm.targetValue", false)
                putBoolean("cgm.basal", false)
                putBoolean("cgm.activity", false)
                putBoolean("cgm.prediction.iob", false)
                putBoolean("cgm.prediction.cob", false)
                putBoolean("cgm.prediction.uam", false)
                putBoolean("cgm.prediction.zeroTemp", false)
                putBoolean("overviewDefaultsMigratedV2", true)
            }
        }
"""
text = replace_once(text, marker, migration + marker, "V2 requested defaults migration")
write(path, text)

# Shared overview viewport, no future for baseline, target line binding
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/SugarliciousOverviewScreen.kt"
text = Path(path).read_text(encoding="utf-8")
text = replace_once(
    text,
    """    val metabolicChartViewport =
        remember {
            ChartViewport(
                preferences.graphHours,
            )
        }
""",
    """    val metabolicChartViewport =
        cgmChartViewport
""",
    "shared overview viewport",
)
text = replace_once(
    text,
    """    val metabolicFutureWindowMs =
        if (
            preferences.showMetabolicGraph
        ) {
            90L * 60_000L
        } else {
            0L
        }
""",
    """    val metabolicFutureWindowMs =
        if (
            preferences.showMetabolicGraph &&
            preferences.anyCgmPredictionEnabled
        ) {
            predictionFutureWindowMs
        } else {
            0L
        }
""",
    "metabolic future baseline behavior",
)
text = replace_once(
    text,
    """        cgmChartViewport.setHours(
            preferences.graphHours.toFloat(),
            resetPan = true,
        )
        metabolicChartViewport.setHours(
            preferences.graphHours.toFloat(),
            resetPan = true,
        )
""",
    """        cgmChartViewport.setHours(
            preferences.graphHours.toFloat(),
            resetPan = true,
        )
""",
    "shared viewport hours update",
)
text = replace_once(
    text,
    """    LaunchedEffect(
        predictionFutureWindowMs,
    ) {
        cgmChartViewport.setFutureWindow(
            predictionFutureWindowMs,
        )
    }

    LaunchedEffect(
        metabolicFutureWindowMs,
    ) {
        metabolicChartViewport.setFutureWindow(
            metabolicFutureWindowMs,
        )
    }
""",
    """    LaunchedEffect(
        predictionFutureWindowMs,
        metabolicFutureWindowMs,
    ) {
        cgmChartViewport.setFutureWindow(
            maxOf(
                predictionFutureWindowMs,
                metabolicFutureWindowMs,
            ),
        )
    }
""",
    "shared viewport future update",
)
text = replace_once(
    text,
    """                showTargetRange =
                    preferences.showCgmTargetRange,
                showBasal =
""",
    """                showTargetRange =
                    preferences.showCgmTargetRange,
                showTargetValue =
                    preferences.showCgmTargetValue,
                showBasal =
""",
    "target value chart binding",
)
write(path, text)

