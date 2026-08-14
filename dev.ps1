param(
    [Parameter(Position = 0)]
    [ValidateSet("mobile", "wear", "all", "wfp")]
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
        "sugarlicious_graph_token.txt"
    )

    foreach ($name in $required) {
        if (-not (Test-Path (Join-Path $generated $name))) { return $true }
    }

    $sourceNewest = @(
        Get-ChildItem .\watchfaces\sugarlicious-analog -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-orbit -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-rings -Recurse -File
        Get-ChildItem .\watchfaces\sugarlicious-graph -Recurse -File
        Get-Item .\tools\watchface-push\Prepare-WatchFacePushAssets.ps1
    ) |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    $assetOldest = $required |
        ForEach-Object { Get-Item (Join-Path $generated $_) } |
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
} elseif ($effectiveTarget -eq "all") {
    if ($Test) {
        $gradleTasks += ":app-mobile:testDebugUnitTest"
        $gradleTasks += ":app-wear:testDebugUnitTest"
        $gradleTasks += ":complications:testDebugUnitTest"
    }
    $gradleTasks += ":app-mobile:assembleDebug"
    $gradleTasks += ":app-wear:assembleDebug"
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

if (($effectiveTarget -eq "wear") -or ($effectiveTarget -eq "all")) {
    $watch = Resolve-AdbSerial $watchWifi $watchWifi
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
