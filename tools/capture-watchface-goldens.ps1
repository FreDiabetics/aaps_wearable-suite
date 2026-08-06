param(
    [string]$Serial = "emulator-5556",
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$OutputDirectory = "docs\test-artifacts\wear-os-6\watchfaces",
    [string[]]$Names = @(),
    [switch]$Reinstall,
    [switch]$InjectSyntheticState
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Adb)) {
    throw "adb was not found at $Adb"
}

$watchFaces = [ordered]@{
    "aaps-v4" = "app.aapswear.watchface.aapsv4"
    "aaps-v2" = "app.aapswear.watchface.aapsv2"
    "aaps-standard" = "app.aapswear.watchface.aapsstandard"
    "aaps-circle" = "app.aapswear.watchface.aapscircle"
    "aaps-digital-style" = "app.aapswear.watchface.aapsdigitalstyle"
    "aaps-big-chart" = "app.aapswear.watchface.aapsbigchart"
    "aaps-large" = "app.aapswear.watchface.aapslarge"
    "aaps-no-chart" = "app.aapswear.watchface.aapsnochart"
    "aaps-cockpit" = "app.aapswear.watchface.aapscockpit"
    "aaps-v2-tt-dark" = "app.aapswear.watchface.aapsv2ttdark"
    "aaps-community" = "app.aapswear.watchface.aapscommunity"
    "aimico" = "app.aapswear.watchface.aimico"
    "analog-g-watch" = "app.aapswear.watchface.analoggwatch"
    "blue-ring" = "app.aapswear.watchface.bluering"
    "digital-big-graph" = "app.aapswear.watchface.digitalbiggraph"
    "digital-g-watch" = "app.aapswear.watchface.digitalgwatch"
    "gears" = "app.aapswear.watchface.gears"
    "gota" = "app.aapswear.watchface.gota"
    "lucky-loop-koeln" = "app.aapswear.watchface.luckyloopkoeln"
    "p-zero" = "app.aapswear.watchface.pzero"
    "robby" = "app.aapswear.watchface.robby"
    "simple-digital" = "app.aapswear.watchface.simpledigital"
    "steam-punk" = "app.aapswear.watchface.steampunk"
    "sugarlicious-digital" = "app.aapswear.watchface.sugarlicious.digital"
    "sugarlicious-analog" = "app.aapswear.watchface.sugarlicious.analog"
}

if ($Names.Count -gt 0) {
    $selected = [ordered]@{}
    foreach ($name in $Names) {
        if (-not $watchFaces.Contains($name)) { throw "Unknown watch face name: $name" }
        $selected[$name] = $watchFaces[$name]
    }
    $watchFaces = $selected
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$state = (& $Adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($state -ne "device") {
    throw "Device $Serial is not ready (state: $state)"
}

& $Adb -s $Serial shell dumpsys battery unplug | Out-Null
& $Adb -s $Serial shell settings put global ambient_enabled 1 | Out-Null
& $Adb -s $Serial shell input keyevent 224 | Out-Null

$results = foreach ($entry in $watchFaces.GetEnumerator()) {
    $name = $entry.Key
    $packageName = $entry.Value

    if ($Reinstall) {
        $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
        $apk = Get-ChildItem (Join-Path $projectRoot "watchfaces\$name\build\outputs\apk\release") -Filter "*.apk" |
            Select-Object -First 1 -ExpandProperty FullName
        if (-not $apk) { throw "Release APK is missing for $name" }
        & $Adb -s $Serial uninstall $packageName | Out-Null
        $install = (& $Adb -s $Serial install $apk 2>&1 | Out-String).Trim()
        if ($install -notmatch "Success") { throw "Installing $name failed: $install" }
    }

    $installed = (& $Adb -s $Serial shell pm path $packageName 2>&1 | Out-String).Trim()
    if (-not $installed.StartsWith("package:")) {
        throw "Watch face package is not installed: $packageName"
    }

    $activation = (& $Adb -s $Serial shell am broadcast `
        -a com.google.android.wearable.app.DEBUG_SURFACE `
        --es operation set-watchface `
        --es watchFaceId $packageName 2>&1 | Out-String).Trim()
    if ($activation -notmatch "result=1") {
        throw "Watch face activation failed for $packageName`: $activation"
    }

    # Close any launcher/app overlay. The declarative runtime can briefly render
    # a black transition surface after activation, so wait for the stable frame.
    & $Adb -s $Serial shell input keyevent 4 | Out-Null
    Start-Sleep -Milliseconds 400
    & $Adb -s $Serial shell input keyevent 4 | Out-Null
    if ($InjectSyntheticState) {
        & $Adb -s $Serial shell am broadcast `
            -n app.aapswear/app.aapswear.wear.DebugStateReceiver `
            -a app.aapswear.DEBUG_INJECT_STATE `
            --es mode current | Out-Null
    }
    Start-Sleep -Seconds 7

    $activePath = Join-Path $OutputDirectory "$name-active.png"
    & $Adb -s $Serial exec-out screencap -p > $activePath

    & $Adb -s $Serial shell input keyevent 223 | Out-Null
    Start-Sleep -Seconds 3
    $powerState = (& $Adb -s $Serial shell dumpsys power | Select-String -Pattern "mWakefulness=" | Select-Object -First 1).Line.Trim()
    $displayState = (& $Adb -s $Serial shell dumpsys display | Select-String -Pattern "Display State=" | Select-Object -First 1).Line.Trim()
    if ($powerState -notmatch "Dozing" -or $displayState -notmatch "DOZE") {
        throw "Ambient mode was not reached for $name ($powerState; $displayState)"
    }

    $ambientPath = Join-Path $OutputDirectory "$name-ambient.png"
    & $Adb -s $Serial exec-out screencap -p > $ambientPath
    & $Adb -s $Serial shell input keyevent 224 | Out-Null
    Start-Sleep -Seconds 1

    [pscustomobject]@{
        WatchFace = $name
        Package = $packageName
        ActiveSha256 = (Get-FileHash -LiteralPath $activePath -Algorithm SHA256).Hash
        AmbientSha256 = (Get-FileHash -LiteralPath $ambientPath -Algorithm SHA256).Hash
        AmbientState = "$powerState; $displayState"
    }
}

$reportName = if ($Names.Count -gt 0) {
    "capture-report-$($Names -join '-').json"
} else {
    "capture-report.json"
}
$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory $reportName) -Encoding utf8
$results | Format-Table -AutoSize
