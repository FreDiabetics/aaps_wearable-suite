param(
    [Parameter(Mandatory = $true)]
    [string]$WatchSerial,

    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$faces = @("analog", "orbit", "rings", "graph")

if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) {
    throw "ADB wurde nicht gefunden: $Adb"
}

$connected = & $Adb devices
if ($LASTEXITCODE -ne 0 -or -not ($connected -match "(?m)^$([regex]::Escape($WatchSerial))\s+device(?:\s|$)")) {
    throw "Die Watch '$WatchSerial' ist nicht als aktives ADB-Gerät verbunden."
}

foreach ($face in $faces) {
    $apk = Join-Path $projectRoot "watchfaces\sugarlicious-$face\build\outputs\apk\release\sugarlicious-$face-release.apk"
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "Watchface-APK fehlt: $apk. Zuerst die vier assembleRelease-Tasks ausführen."
    }

    Write-Host "Installiere Sugarlicious $face ..."
    & $Adb -s $WatchSerial install -r $apk
    if ($LASTEXITCODE -ne 0) {
        throw "Installation von Sugarlicious $face fehlgeschlagen."
    }
}

Write-Host "Alle vier Sugarlicious-Watchfaces wurden auf der Watch installiert."
