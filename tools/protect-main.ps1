param(
    [string]$Repository = "FreDiabetics/sugarlicious",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) wurde nicht gefunden."
}

gh auth status | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI ist nicht authentifiziert."
}

$body = @{
    required_status_checks = @{
        strict = $true
        contexts = @("verify")
    }
    enforce_admins = $true
    required_pull_request_reviews = @{
        dismiss_stale_reviews = $true
        require_code_owner_reviews = $false
        required_approving_review_count = 0
        require_last_push_approval = $false
    }
    restrictions = $null
    required_linear_history = $false
    allow_force_pushes = $false
    allow_deletions = $false
    block_creations = $false
    required_conversation_resolution = $true
    lock_branch = $false
    allow_fork_syncing = $true
} | ConvertTo-Json -Depth 8

# Fail locally before calling GitHub if request serialization ever becomes invalid.
$null = $body | ConvertFrom-Json

$temp = [System.IO.Path]::GetTempFileName()
try {
    # Windows PowerShell 5.1 writes a BOM for Set-Content -Encoding UTF8.
    # GitHub's JSON parser rejects that BOM when gh api --input sends the file verbatim.
    # Write explicit UTF-8 without BOM so this helper behaves identically in Windows
    # PowerShell 5.1 and modern PowerShell.
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($temp, $body, $utf8NoBom)

    Write-Host "`nAktiviere Branch-Schutz für $Repository/$Branch ..."

    $apiOutput = gh api --method PUT `
        -H "Accept: application/vnd.github+json" `
        -H "X-GitHub-Api-Version: 2026-03-10" `
        "repos/$Repository/branches/$Branch/protection" `
        --input $temp 2>&1

    $apiExit = $LASTEXITCODE

    if ($apiExit -ne 0) {
        $details = ($apiOutput -join [Environment]::NewLine).Trim()
        throw "Branch-Schutz konnte nicht aktiviert werden. GitHub API: $details"
    }

    $protection = gh api `
        -H "Accept: application/vnd.github+json" `
        -H "X-GitHub-Api-Version: 2026-03-10" `
        "repos/$Repository/branches/$Branch/protection" | ConvertFrom-Json

    $contexts = @($protection.required_status_checks.contexts)
    if (-not $protection.enforce_admins.enabled) { throw "Verifikation fehlgeschlagen: Admin-Schutz ist nicht aktiv." }
    if (-not $protection.required_status_checks.strict) { throw "Verifikation fehlgeschlagen: Required status checks sind nicht strict." }
    if ($contexts -notcontains "verify") { throw "Verifikation fehlgeschlagen: Status-Check 'verify' ist nicht verpflichtend." }
    if ($protection.allow_force_pushes.enabled) { throw "Verifikation fehlgeschlagen: Force-Push ist weiterhin erlaubt." }
    if ($protection.allow_deletions.enabled) { throw "Verifikation fehlgeschlagen: Branch-Löschung ist weiterhin erlaubt." }
    if (-not $protection.required_conversation_resolution.enabled) { throw "Verifikation fehlgeschlagen: Conversation resolution ist nicht verpflichtend." }
    if ($null -eq $protection.required_pull_request_reviews) { throw "Verifikation fehlgeschlagen: Pull-Request-Pflicht ist nicht aktiv." }

    Write-Host "`nMAIN-SCHUTZ AKTIV"
    Write-Host "- Änderungen nur über Pull Request"
    Write-Host "- CI-Check 'verify' verpflichtend und Branch muss aktuell sein"
    Write-Host "- Admin/Owner unterliegt den Regeln"
    Write-Host "- Force-Push gesperrt"
    Write-Host "- Branch-Löschung gesperrt"
    Write-Host "- Review-Konversationen müssen gelöst sein"
    Write-Host "- aktuell 0 Pflichtfreigaben, damit das Single-Maintainer-Repository nicht blockiert wird"
    Write-Host "- CODEOWNERS ist vorhanden; bei zweitem Maintainer kann eine Pflichtfreigabe aktiviert werden"
}
finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
