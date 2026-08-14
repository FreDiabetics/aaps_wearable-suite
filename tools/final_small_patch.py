from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"missing anchor: {label} ({path})")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt",
    """    private var colorSettingsExpanded = false
    private var predictionSettingsExpanded = false
    private var notificationGraphSettingsExpanded = false
""",
    """    private val settingsUiPreferences =
        context.getSharedPreferences("dashboard_settings_ui", Context.MODE_PRIVATE)
    private var colorSettingsExpanded =
        settingsUiPreferences.getBoolean("colors_expanded", false)
    private var predictionSettingsExpanded =
        settingsUiPreferences.getBoolean("predictions_expanded", false)
    private var notificationGraphSettingsExpanded =
        settingsUiPreferences.getBoolean("notification_graph_expanded", false)
""",
    "settings expansion state",
)
replace_once(
    "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt",
    """                        colorSettingsExpanded = !colorSettingsExpanded
                        colorContainer.visibility = if (colorSettingsExpanded) View.VISIBLE else View.GONE
""",
    """                        colorSettingsExpanded = !colorSettingsExpanded
                        settingsUiPreferences.edit().putBoolean("colors_expanded", colorSettingsExpanded).apply()
                        colorContainer.visibility = if (colorSettingsExpanded) View.VISIBLE else View.GONE
""",
    "color section toggle",
)
replace_once(
    "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt",
    """                            predictionSettingsExpanded = !predictionSettingsExpanded
                            predictionContainer.visibility = if (predictionSettingsExpanded) View.VISIBLE else View.GONE
""",
    """                            predictionSettingsExpanded = !predictionSettingsExpanded
                            settingsUiPreferences.edit().putBoolean("predictions_expanded", predictionSettingsExpanded).apply()
                            predictionContainer.visibility = if (predictionSettingsExpanded) View.VISIBLE else View.GONE
""",
    "prediction section toggle",
)
replace_once(
    "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt",
    """                            notificationGraphSettingsExpanded = !notificationGraphSettingsExpanded
                            notificationGraphCustomization.visibility = if (notificationGraphSettingsExpanded) View.VISIBLE else View.GONE
""",
    """                            notificationGraphSettingsExpanded = !notificationGraphSettingsExpanded
                            settingsUiPreferences.edit().putBoolean("notification_graph_expanded", notificationGraphSettingsExpanded).apply()
                            notificationGraphCustomization.visibility = if (notificationGraphSettingsExpanded) View.VISIBLE else View.GONE
""",
    "notification graph section toggle",
)
replace_once(
    "app-mobile/src/main/kotlin/app/aapswear/mobile/OverviewWatchFaceTile.kt",
    "activeComplicationIds.take(3)",
    "activeComplicationIds.take(8)",
    "active complication preview count",
)
replace_once(
    "app-wear/src/main/res/layout/activity_wear.xml",
    'android:layout_height="36dp"\n                        android:layout_marginStart="5dp"',
    'android:layout_height="44dp"\n                        android:layout_marginStart="5dp"',
    "trend container height",
)

layout = Path("app-wear/src/main/res/layout/activity_wear.xml")
text = layout.read_text(encoding="utf-8")
needle = 'android:layout_width="27dp"\n                            android:layout_height="27dp"'
if text.count(needle) != 2:
    raise SystemExit(f"expected two Watch trend arrow size anchors, got {text.count(needle)}")
layout.write_text(
    text.replace(
        needle,
        'android:layout_width="25dp"\n                            android:layout_height="25dp"',
    ),
    encoding="utf-8",
)
