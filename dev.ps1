param(
    [Parameter(Position = 0)]
    [ValidateSet("mobile", "wear", "g7", "all", "wfp")]
    [string]$Target = "mobile",
    [switch]$Test,
    [switch]$NoPull
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$phoneUsb = "R3GL30M0HYX"
$phoneWifi = "adb-R3GL30M0HYX-gUIExC._adb-tls-connect._tcp"
$watchWifi = "adb-RFAY12MBZ8X-AVH2AE._adb-tls-connect._tcp"

function Assert-LastExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

function Resolve-AdbSerial([string]$Preferred, [string]$Fallback) {
    $connected = @(
        adb devices |
            Select-Object -Skip 1 |
            ForEach-Object {
                if ($_ -match '^([^\s]+)\s+device(?:\s|$)') { $matches[1] }
            }
    )
    if ($Preferred -in $connected) { return $Preferred }
    if ($Fallback -in $connected) { return $Fallback }
    return $Preferred
}

function Test-WatchFacePushAssetsStale {
    $generated = ".\app-wear\build\generated\watchfacePushAssets\watchfaces"
    $required = @(
        "sugarlicious_analog.apk",
        "sugarlicious_analog_token.txt",
        "sugarlicious_orbit.apk",
        "sugarlicious_orbit_token.txt",
        "sugarlicious_rings.apk",
        "sugarlicious_rings_token.txt",
        "sugarlicious_graph.apk",
        "sugarlicious_graph_token.txt",
        "sugarlicious_digital.apk",
        "sugarlicious_digital_token.txt",
        "..\default_watchface.apk"
    )

    $defaultTokenResource =
        ".\app-wear\build\generated\watchfacePushRes\values\default_watchface_token.xml"
    if (-not (Test-Path $defaultTokenResource)) { return $true }

    foreach ($name in $required) {
        if (-not (Test-Path (Join-Path $generated $name))) { return $true }
    }

    $watchFaceSources = @(
        Get-ChildItem .\watchfaces\sugarlicious-analog -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-orbit -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-rings -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-graph -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-digital -Recurse -File
    ) | Where-Object {
        $_.FullName -notmatch '[\\/](build|\.gradle)[\\/]'
    }

    $sourceNewest = @(
        $watchFaceSources
        Get-Item .\tools\watchface-push\Prepare-WatchFacePushAssets.ps1
    ) |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    $assetOldest = @(
        $required | ForEach-Object { Get-Item (Join-Path $generated $_) }
        Get-Item $defaultTokenResource
    ) |
        Sort-Object LastWriteTime |
        Select-Object -First 1

    return $sourceNewest.LastWriteTime -gt $assetOldest.LastWriteTime
}

if (-not $NoPull) {
    $dirty = @(git status --porcelain)
    Assert-LastExitCode "git status"
    if ($dirty.Count -gt 0) {
        throw "Local changes exist. Commit or stash them first, or use -NoPull intentionally."
    }
    Write-Host "Syncing GitHub..."
    git pull --ff-only
    Assert-LastExitCode "git pull"
}

$effectiveTarget = $Target
$needsWatchFaceAssets = $Target -in @("wear", "all", "wfp")
if ($needsWatchFaceAssets -and (($Target -eq "wfp") -or (Test-WatchFacePushAssetsStale))) {
    Write-Host "Preparing current Watch Face Push assets..."
    & .\tools\watchface-push\Prepare-WatchFacePushAssets.ps1
    if (-not $?) { throw "Watch Face Push asset preparation failed" }
}
if ($Target -eq "wfp") {
    $effectiveTarget = "wear"
}

[string[]]$gradleTasks = @()
if ($effectiveTarget -eq "mobile") {
    if ($Test) { $gradleTasks += ":app-mobile:testDebugUnitTest" }
    $gradleTasks += ":app-mobile:assembleDebug"
} elseif ($effectiveTarget -eq "wear") {
    if ($Test) { $gradleTasks += ":app-wear:testDebugUnitTest" }
    $gradleTasks += ":app-wear:assembleDebug"
} elseif ($effectiveTarget -eq "g7") {
    if ($Test) {
        $gradleTasks += ":dexcom-g7:test"
        $gradleTasks += ":g7watch:testDebugUnitTest"
    }
    $gradleTasks += ":g7watch:assembleDebug"
} elseif ($effectiveTarget -eq "all") {
    if ($Test) {
        $gradleTasks += ":app-mobile:testDebugUnitTest"
        $gradleTasks += ":app-wear:testDebugUnitTest"
        $gradleTasks += ":complications:testDebugUnitTest"
        $gradleTasks += ":dexcom-g7:test"
        $gradleTasks += ":g7watch:testDebugUnitTest"
    }
    $gradleTasks += ":app-mobile:assembleDebug"
    $gradleTasks += ":app-wear:assembleDebug"
    $gradleTasks += ":g7watch:assembleDebug"
} else {
    throw "Unsupported target: $effectiveTarget"
}

Write-Host "Running Gradle tasks:"
foreach ($task in $gradleTasks) { Write-Host "  $task" }
& .\gradlew.bat @gradleTasks
Assert-LastExitCode "Gradle"

if (($effectiveTarget -eq "mobile") -or ($effectiveTarget -eq "all")) {
    $phone = Resolve-AdbSerial $phoneUsb $phoneWifi
    $mobileApk = Get-ChildItem .\app-mobile\build\outputs\apk\debug\*.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $mobileApk) { throw "Mobile APK not found" }
    Write-Host "Installing Mobile on $phone..."
    adb -s $phone install -r $mobileApk.FullName
    Assert-LastExitCode "Mobile install"
    adb -s $phone shell am force-stop app.aapswear
    Assert-LastExitCode "Mobile force-stop"
    adb -s $phone shell monkey -p app.aapswear 1 | Out-Null
    Assert-LastExitCode "Mobile start"
}

if (($effectiveTarget -eq "wear") -or ($effectiveTarget -eq "g7") -or ($effectiveTarget -eq "all")) {
    $watch = Resolve-AdbSerial $watchWifi $watchWifi
    if (($effectiveTarget -eq "g7") -or ($effectiveTarget -eq "all")) {
        $g7WatchApk = Get-ChildItem .\g7watch\build\outputs\apk\debug\*.apk |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $g7WatchApk) { throw "G7 Watch Collector APK not found" }
        Write-Host "Installing G7 Watch Collector on $watch..."
        adb -s $watch install -r $g7WatchApk.FullName
        Assert-LastExitCode "G7 Watch Collector install"
    }

    if (($effectiveTarget -ne "wear") -and ($effectiveTarget -ne "all")) {
        Write-Host ""
        Write-Host "OK: $effectiveTarget built and installed."
        exit 0
    }

    $wearApk = Get-ChildItem .\app-wear\build\outputs\apk\debug\*.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $wearApk) { throw "Wear APK not found" }
    Write-Host "Installing Wear on $watch..."
    adb -s $watch install -r $wearApk.FullName
    Assert-LastExitCode "Wear install"
    adb -s $watch shell am force-stop app.aapswear
    Assert-LastExitCode "Wear force-stop"
    adb -s $watch shell monkey -p app.aapswear 1 | Out-Null
    Assert-LastExitCode "Wear start"
}

Write-Host ""
Write-Host "OK: $effectiveTarget built and installed."
