param(
    [Parameter(Position = 0)]
    [ValidateSet("mobile", "wear", "all", "wfp")]
    [string]$Target = "mobile",
    [switch]$Test,
    [switch]$NoPull
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$phone = "adb-R3GL30M0HYX-gUIExC._adb-tls-connect._tcp"
$watch = "adb-RFAY12MBZ8X-AVH2AE._adb-tls-connect._tcp"

function Assert-LastExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
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
if ($Target -eq "wfp") {
    Write-Host "Preparing Watch Face Push assets..."
    & .\tools\watchface-push\Prepare-WatchFacePushAssets.ps1
    if (-not $?) { throw "Watch Face Push asset preparation failed" }
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
    $mobileApk = Get-ChildItem .\app-mobile\build\outputs\apk\debug\*.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $mobileApk) { throw "Mobile APK not found" }
    Write-Host "Installing Mobile..."
    adb -s $phone install -r $mobileApk.FullName
    Assert-LastExitCode "Mobile install"
    adb -s $phone shell am force-stop app.aapswear
    Assert-LastExitCode "Mobile force-stop"
    adb -s $phone shell monkey -p app.aapswear 1 | Out-Null
    Assert-LastExitCode "Mobile start"
}

if (($effectiveTarget -eq "wear") -or ($effectiveTarget -eq "all")) {
    $wearApk = Get-ChildItem .\app-wear\build\outputs\apk\debug\*.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $wearApk) { throw "Wear APK not found" }
    Write-Host "Installing Wear..."
    adb -s $watch install -r $wearApk.FullName
    Assert-LastExitCode "Wear install"
    adb -s $watch shell am force-stop app.aapswear
    Assert-LastExitCode "Wear force-stop"
    adb -s $watch shell monkey -p app.aapswear 1 | Out-Null
    Assert-LastExitCode "Wear start"
}

Write-Host ""
Write-Host "OK: $effectiveTarget built and installed."
