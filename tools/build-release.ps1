param(
    [string]$Version = "0.3.0-diy-preview",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradle = Join-Path $projectRoot "gradlew.bat"
$watchFaces = @(
    "aaps-v4", "aaps-v2", "aaps-standard", "aaps-circle", "aaps-digital-style",
    "aaps-big-chart", "aaps-large", "aaps-no-chart", "aaps-cockpit", "aaps-v2-tt-dark",
    "aaps-community", "aimico", "analog-g-watch", "blue-ring", "digital-big-graph",
    "digital-g-watch", "gears", "gota", "lucky-loop-koeln", "p-zero", "robby",
    "simple-digital", "steam-punk"
)

if (-not $SkipBuild) {
    $tasks = @("test", "assembleDebug", ":app-mobile:assembleRelease", ":app-wear:assembleRelease", ":watchfaces:test-wff:assembleRelease") +
        ($watchFaces | ForEach-Object { ":watchfaces:$($_):assembleRelease" })
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed" }

    & (Join-Path $PSScriptRoot "wff-validator\validate.ps1")
    if ($LASTEXITCODE -ne 0) { throw "WFF validation failed" }

    & (Join-Path $PSScriptRoot "verify-codefree-watchfaces.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Code-free APK verification failed" }
}

$distRoot = Join-Path $projectRoot "dist"
$target = Join-Path $distRoot "aaps-wear-watchfaces-$Version"
if (Test-Path -LiteralPath $target) {
    $resolvedTarget = (Resolve-Path -LiteralPath $target).Path
    if (-not $resolvedTarget.StartsWith($distRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace a directory outside dist: $resolvedTarget"
    }
    Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
}
New-Item -ItemType Directory -Force -Path (Join-Path $target "apps"), (Join-Path $target "watchfaces"), (Join-Path $target "docs"), (Join-Path $target "LICENSES") | Out-Null

Copy-Item -LiteralPath (Join-Path $projectRoot "app-mobile\build\outputs\apk\debug\app-mobile-debug.apk") -Destination (Join-Path $target "apps\aaps-display-mobile-debug.apk")
Copy-Item -LiteralPath (Join-Path $projectRoot "app-wear\build\outputs\apk\debug\app-wear-debug.apk") -Destination (Join-Path $target "apps\aaps-display-wear-debug.apk")
foreach ($name in $watchFaces) {
    $source = Join-Path $projectRoot "watchfaces\$name\build\outputs\apk\release\$name-release.apk"
    if (-not (Test-Path -LiteralPath $source)) {
        $source = Get-ChildItem (Join-Path $projectRoot "watchfaces\$name\build\outputs\apk\release") -Filter "*.apk" | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $source -or -not (Test-Path -LiteralPath $source)) { throw "Missing release APK for $name" }
    Copy-Item -LiteralPath $source -Destination (Join-Path $target "watchfaces\$name.apk")
}

@("README.md", "CHANGELOG.md", "NOTICE.md", "LICENSE") | ForEach-Object {
    Copy-Item -LiteralPath (Join-Path $projectRoot $_) -Destination $target
}
Get-ChildItem (Join-Path $projectRoot "docs") -File -Filter "*.md" | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $target "docs")
}
Copy-Item -Path (Join-Path $projectRoot "LICENSES\*") -Destination (Join-Path $target "LICENSES") -Recurse

$report = [ordered]@{
    release = $Version
    generatedAt = (Get-Date).ToString("o")
    androidApsDevCommit = "18101c8a2c0204a08d417f3d5fbac3e9ceae380f"
    watchfaceCount = $watchFaces.Count
    applicationSigning = "debug/development signing only"
    publishingReady = $false
    excluded = @("PinkFloydTheWall: protected third-party motif/brand rights not cleared")
}
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $target "BUILD-REPORT.json") -Encoding utf8NoBOM

$hashLines = Get-ChildItem -LiteralPath $target -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($target.Length + 1).Replace("\", "/")
    "{0}  {1}" -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $relative
}
$hashLines | Set-Content -LiteralPath (Join-Path $target "SHA256SUMS.txt") -Encoding ascii

$zip = Join-Path $distRoot "aaps-wear-watchfaces-$Version.zip"
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $target "*") -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Release bundle: $zip"
