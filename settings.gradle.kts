pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "aaps-wear-watchfaces"

include(
    ":core-model",
    ":data-source-api",
    ":data-source-aaps",
    ":wear-protocol",
    ":wear-storage",
    ":complications",
    ":app-mobile",
    ":app-wear",
    ":watchfaces:test-wff",
    ":watchfaces:aaps-v4",
    ":watchfaces:aaps-v2",
    ":watchfaces:aaps-circle",
    ":watchfaces:aaps-digital-style",
    ":watchfaces:aaps-standard",
    ":watchfaces:aaps-big-chart",
    ":watchfaces:aaps-large",
    ":watchfaces:aaps-no-chart",
    ":watchfaces:aaps-cockpit",
    ":watchfaces:aaps-v2-tt-dark",
    ":watchfaces:aaps-community",
    ":watchfaces:aimico",
    ":watchfaces:analog-g-watch",
    ":watchfaces:blue-ring",
    ":watchfaces:digital-big-graph",
    ":watchfaces:digital-g-watch",
    ":watchfaces:gears",
    ":watchfaces:gota",
    ":watchfaces:lucky-loop-koeln",
    ":watchfaces:p-zero",
    ":watchfaces:robby",
    ":watchfaces:simple-digital",
    ":watchfaces:steam-punk",
    ":tools:aaps-cwf-parser",
    ":tools:wff-generator",
    ":tools:screenshot-comparator",
)
