from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"pattern not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"updated {path}")


# Dashboard preferences + settings UI.
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/DashboardViews.kt"
replace_once(
    path,
    """    val graphHours: Int = 3,\n    val liveNotification: Boolean = false,\n    val watchFaceIndex: Int = 1,\n""",
    """    val graphHours: Int = 3,\n    val liveNotification: Boolean = false,\n    val notificationGraphEnabled: Boolean = true,\n    val notificationGraphHours: Int = 3,\n    val watchFaceIndex: Int = 1,\n""",
)
replace_once(
    path,
    """                liveNotification =\n                    preferences.getBoolean(\n                        PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION,\n                        false,\n                    ),\n                watchFaceIndex =\n""",
    """                liveNotification =\n                    preferences.getBoolean(\n                        PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION,\n                        false,\n                    ),\n                notificationGraphEnabled =\n                    preferences.getBoolean(\n                        PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED,\n                        true,\n                    ),\n                notificationGraphHours =\n                    preferences\n                        .getInt(\n                            PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS,\n                            3,\n                        )\n                        .takeIf { it in 1..3 }\n                        ?: 3,\n                watchFaceIndex =\n""",
)
replace_once(
    path,
    """    val setCompact: (Boolean) -> Unit,\n    val setLiveNotification: (Boolean) -> Unit,\n    val setWatchFaceIndex: (Int) -> Unit,\n""",
    """    val setCompact: (Boolean) -> Unit,\n    val setLiveNotification: (Boolean) -> Unit,\n    val setNotificationGraphEnabled: (Boolean) -> Unit,\n    val setNotificationGraphHours: (Int) -> Unit,\n    val setWatchFaceIndex: (Int) -> Unit,\n""",
)
replace_once(
    path,
    """                addView(\n                    switchRowCompact(\n                        \"Live-Benachrichtigung\",\n                        preferences.liveNotification,\n                        R.id.dashboard_live_notification_switch,\n                        callbacks.setLiveNotification,\n                    ),\n                )\n            },\n""",
    """                addView(\n                    switchRowCompact(\n                        \"Live-Benachrichtigung\",\n                        preferences.liveNotification,\n                        R.id.dashboard_live_notification_switch,\n                        callbacks.setLiveNotification,\n                    ),\n                )\n                addView(divider())\n                addView(\n                    switchRowCompact(\n                        \"Graph anzeigen\",\n                        preferences.notificationGraphEnabled,\n                        View.generateViewId(),\n                        callbacks.setNotificationGraphEnabled,\n                    ),\n                )\n                if (preferences.notificationGraphEnabled) {\n                    addView(divider())\n                    addView(\n                        choiceRow(\n                            \"Graph-Zeitraum\",\n                            listOf(\n                                Triple(\"1 h\", preferences.notificationGraphHours == 1) {\n                                    callbacks.setNotificationGraphHours(1)\n                                },\n                                Triple(\"2 h\", preferences.notificationGraphHours == 2) {\n                                    callbacks.setNotificationGraphHours(2)\n                                },\n                                Triple(\"3 h\", preferences.notificationGraphHours == 3) {\n                                    callbacks.setNotificationGraphHours(3)\n                                },\n                            ),\n                        ),\n                    )\n                }\n            },\n""",
)

# MainActivity preference callbacks.
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/MainActivity.kt"
replace_once(
    path,
    """            setCompact = { uiPreferences.edit { putBoolean(\"compact\", it) } },\n            setLiveNotification = ::setLiveNotification,\n            setWatchFaceIndex = {\n""",
    """            setCompact = { uiPreferences.edit { putBoolean(\"compact\", it) } },\n            setLiveNotification = ::setLiveNotification,\n            setNotificationGraphEnabled = { enabled ->\n                uiPreferences.edit {\n                    putBoolean(\n                        PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED,\n                        enabled,\n                    )\n                }\n            },\n            setNotificationGraphHours = { hours ->\n                uiPreferences.edit {\n                    putInt(\n                        PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS,\n                        hours.coerceIn(1, 3),\n                    )\n                }\n            },\n            setWatchFaceIndex = {\n""",
)

# Notification rendering and dedicated time scale.
path = "app-mobile/src/main/kotlin/app/aapswear/mobile/PersistentBridgeService.kt"
replace_once(
    path,
    """import android.os.IBinder\nimport android.widget.RemoteViews\n""",
    """import android.os.IBinder\nimport android.view.View\nimport android.widget.RemoteViews\n""",
)
replace_once(
    path,
    """        val display =\n            notificationDisplay(latestState)\n\n        val collapsedGraph =\n            NotificationGraphRenderer.renderCollapsed(\n                context = this,\n                state = latestState,\n                preferences = uiPreferences,\n            )\n        val expandedGraph =\n            NotificationGraphRenderer.renderExpanded(\n                context = this,\n                state = latestState,\n                preferences = uiPreferences,\n            )\n""",
    """        val notificationGraphEnabled =\n            uiPreferences.getBoolean(\n                PREFERENCE_NOTIFICATION_GRAPH_ENABLED,\n                true,\n            )\n        val display =\n            notificationDisplay(\n                state = latestState,\n                graphEnabled = notificationGraphEnabled,\n            )\n\n        val collapsedGraph =\n            if (notificationGraphEnabled) {\n                NotificationGraphRenderer.renderCollapsed(\n                    context = this,\n                    state = latestState,\n                    preferences = uiPreferences,\n                )\n            } else {\n                null\n            }\n        val expandedGraph =\n            if (notificationGraphEnabled) {\n                NotificationGraphRenderer.renderExpanded(\n                    context = this,\n                    state = latestState,\n                    preferences = uiPreferences,\n                )\n            } else {\n                null\n            }\n""",
)
replace_once(
    path,
    """        graph: Bitmap,\n    ): RemoteViews {\n""",
    """        graph: Bitmap?,\n    ): RemoteViews {\n""",
)
replace_once(
    path,
    """            setImageViewBitmap(\n                R.id.notification_graph,\n                graph,\n            )\n        }\n    }\n\n    private fun notificationDisplay(state: TherapyDisplayState?): NotificationDisplay {\n""",
    """            if (graph != null) {\n                setViewVisibility(\n                    R.id.notification_graph,\n                    View.VISIBLE,\n                )\n                setImageViewBitmap(\n                    R.id.notification_graph,\n                    graph,\n                )\n            } else {\n                setViewVisibility(\n                    R.id.notification_graph,\n                    View.GONE,\n                )\n            }\n        }\n    }\n\n    private fun notificationDisplay(\n        state: TherapyDisplayState?,\n        graphEnabled: Boolean,\n    ): NotificationDisplay {\n""",
)
replace_once(
    path,
    """        val prefix = if (freshness == Freshness.DELAYED) \"Verzögert · \" else \"\"\n        return NotificationDisplay(\"$value $trend\", \"$prefix$unit · $age min alt\")\n""",
    """        val prefix = if (freshness == Freshness.DELAYED) \"Verzögert · \" else \"\"\n        val subtitle =\n            if (graphEnabled) {\n                \"$prefix$unit · $age min alt\"\n            } else {\n                \"$prefix$age min\"\n            }\n        return NotificationDisplay(\"$value $trend\", subtitle)\n""",
)
replace_once(
    path,
    """        const val PREFERENCE_LIVE_NOTIFICATION = \"liveNotification\"\n        const val EXTRA_REQUEST_PROMOTED_ONGOING = \"android.requestPromotedOngoing\"\n""",
    """        const val PREFERENCE_LIVE_NOTIFICATION = \"liveNotification\"\n        const val PREFERENCE_NOTIFICATION_GRAPH_ENABLED = \"notification.graphEnabled\"\n        const val PREFERENCE_NOTIFICATION_GRAPH_HOURS = \"notification.graphHours\"\n        const val EXTRA_REQUEST_PROMOTED_ONGOING = \"android.requestPromotedOngoing\"\n""",
)
replace_once(
    path,
    """            displayHeightDp =\n                COLLAPSED_DISPLAY_HEIGHT_DP,\n            graphHoursOverride = 3,\n        )\n""",
    """            displayHeightDp =\n                COLLAPSED_DISPLAY_HEIGHT_DP,\n            graphHoursOverride = notificationGraphHours(preferences),\n        )\n""",
)
replace_once(
    path,
    """            displayHeightDp =\n                EXPANDED_DISPLAY_HEIGHT_DP,\n        )\n\n    fun render(\n""",
    """            displayHeightDp =\n                EXPANDED_DISPLAY_HEIGHT_DP,\n            graphHoursOverride = notificationGraphHours(preferences),\n        )\n\n    internal fun notificationGraphHours(\n        preferences: SharedPreferences,\n    ): Int =\n        preferences\n            .getInt(\n                PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS,\n                3,\n            )\n            .takeIf { it in 1..3 }\n            ?: 3\n\n    fun render(\n""",
)

# Tests for the new independent notification settings.
path = "app-mobile/src/test/kotlin/app/aapswear/mobile/MainActivityTest.kt"
replace_once(
    path,
    """    @Test fun `fresh install uses requested overview and CGM defaults`() {\n""",
    """    @Test fun `notification graph defaults are independent from dashboard graph`() {\n        val context = ApplicationProvider.getApplicationContext<android.content.Context>()\n        val preferences = context.getSharedPreferences(\"dashboard_ui\", android.content.Context.MODE_PRIVATE)\n        preferences.edit()\n            .clear()\n            .putInt(\"graphHours\", 24)\n            .putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED, false)\n            .putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 2)\n            .commit()\n\n        val ui = DashboardUiPreferences.read(preferences)\n\n        assertEquals(24, ui.graphHours)\n        assertFalse(ui.notificationGraphEnabled)\n        assertEquals(2, ui.notificationGraphHours)\n    }\n\n    @Test fun `fresh install uses requested overview and CGM defaults`() {\n""",
)
replace_once(
    path,
    """        assertFalse(ui.showMetabolicGraph)\n\n        controller.pause().stop().destroy()\n""",
    """        assertFalse(ui.showMetabolicGraph)\n        assertTrue(ui.notificationGraphEnabled)\n        assertEquals(3, ui.notificationGraphHours)\n\n        controller.pause().stop().destroy()\n""",
)

path = "app-mobile/src/test/kotlin/app/aapswear/mobile/PersistentBridgeServiceTest.kt"
replace_once(
    path,
    """    @Test\n    @Config(sdk = [35])\n    fun `boot receiver requests persistent service restart`() {\n""",
    """    @Test\n    @Config(sdk = [35])\n    fun `notification graph accepts only one two or three hours`() {\n        val context = ApplicationProvider.getApplicationContext<android.content.Context>()\n        val preferences = context.getSharedPreferences(\"dashboard_ui\", android.content.Context.MODE_PRIVATE)\n\n        preferences.edit().clear().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 1).commit()\n        assertEquals(1, NotificationGraphRenderer.notificationGraphHours(preferences))\n\n        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 2).commit()\n        assertEquals(2, NotificationGraphRenderer.notificationGraphHours(preferences))\n\n        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 3).commit()\n        assertEquals(3, NotificationGraphRenderer.notificationGraphHours(preferences))\n\n        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 6).commit()\n        assertEquals(3, NotificationGraphRenderer.notificationGraphHours(preferences))\n    }\n\n    @Test\n    @Config(sdk = [35])\n    fun `boot receiver requests persistent service restart`() {\n""",
)

print("notification graph options integrated")
