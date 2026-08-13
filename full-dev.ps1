param(
    [switch]$Test,
    [switch]$NoPull
)

$ErrorActionPreference = "Stop"

if (-not $NoPull) {
    $dirty = @(git status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw "git status failed" }
    if ($dirty.Count -gt 0) {
        throw "Local changes exist. Commit or stash them first, or use -NoPull intentionally."
    }
    git pull --ff-only
    if ($LASTEXITCODE -ne 0) { throw "git pull failed" }
}

Write-Host "Preparing Watch Face Push assets..."
& .\tools\watchface-push\Prepare-WatchFacePushAssets.ps1
if (-not $?) { throw "Watch Face Push asset preparation failed" }

if ($Test) {
    & .\dev.ps1 all -NoPull -Test
} else {
    & .\dev.ps1 all -NoPull
}
if (-not $?) { throw "Mobile/Wear step failed" }

Write-Host ""
Write-Host "OK: WFP assets, Mobile and Wear are installed."
