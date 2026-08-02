param([string]$Configuration = "release")

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$apks = Get-ChildItem (Join-Path $projectRoot "watchfaces") -Recurse -Filter "*.apk" |
    Where-Object { $_.FullName -match "[\\/]outputs[\\/]apk[\\/]$Configuration[\\/]" }

if (-not $apks) { throw "No $Configuration watchface APKs found" }
foreach ($apk in $apks) {
    $entries = & jar tf $apk.FullName
    if ($entries | Where-Object { $_ -match "(^|/)classes[0-9]*\.dex$" }) {
        throw "$($apk.FullName) contains executable DEX code"
    }
    $hash = (Get-FileHash $apk.FullName -Algorithm SHA256).Hash
    Write-Host "PASS code-free: $($apk.Name) SHA-256 $hash"
}

