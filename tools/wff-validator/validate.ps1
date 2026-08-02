param(
    [string]$ValidatorCommit = "702e9bdf050df800a6469cd2155c5c123fd54cb7",
    [string[]]$WatchfaceFiles
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$cacheRoot = Join-Path ([System.IO.Path]::GetTempPath()) "aaps-wff-validator-$ValidatorCommit"
$validatorRoot = Join-Path $cacheRoot "third_party\wff"
$validatorJar = Join-Path $validatorRoot "specification\validator\build\libs\wff-validator.jar"

if (-not (Test-Path (Join-Path $cacheRoot ".git"))) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    & git -C $cacheRoot init
    & git -C $cacheRoot remote add origin https://github.com/google/watchface.git
    & git -C $cacheRoot sparse-checkout init --cone
    & git -C $cacheRoot sparse-checkout set third_party/wff
    & git -C $cacheRoot fetch --depth 1 origin $ValidatorCommit
    & git -C $cacheRoot checkout --detach FETCH_HEAD
}

$actualCommit = (& git -C $cacheRoot rev-parse HEAD).Trim()
if ($actualCommit -ne $ValidatorCommit) {
    throw "Validator cache has commit $actualCommit, expected $ValidatorCommit"
}

if (-not (Test-Path $validatorJar)) {
    $gradle = if ($IsWindows) { Join-Path $projectRoot "gradlew.bat" } else { Join-Path $projectRoot "gradlew" }
    & $gradle -p $validatorRoot :specification:validator:executable-jar
    if ($LASTEXITCODE -ne 0) { throw "Building the official WFF validator failed" }
}

if (-not $WatchfaceFiles) {
    $WatchfaceFiles = Get-ChildItem (Join-Path $projectRoot "watchfaces") -Recurse -Filter watchface.xml |
        Where-Object { $_.FullName -match "[\\/]src[\\/]main[\\/]res[\\/]raw[\\/]watchface\.xml$" } |
        ForEach-Object { $_.FullName }
}
if (-not $WatchfaceFiles) { throw "No WFF watchface.xml files found" }

$output = & java -jar $validatorJar 1 $WatchfaceFiles 2>&1
$output | ForEach-Object { Write-Host $_ }
if (($output | Out-String) -match "FAILED") {
    throw "At least one Watch Face Format document failed schema validation"
}

