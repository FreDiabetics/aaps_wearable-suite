$ErrorActionPreference = 'Stop'

$root = (Get-Location).Path
$generated = Join-Path $root 'app-wear\build\generated\watchfacePushAssets\watchfaces'
$toolDir = Join-Path $root 'build\watchface-push\tools'

Write-Host 'Building Sugarlicious Watch Face Push packages...'

& .\gradlew `
    :watchfaces:sugarlicious-analog:assembleDebug `
    :watchfaces:sugarlicious-orbit:assembleDebug `
    :watchfaces:sugarlicious-rings:assembleDebug `
    :watchfaces:sugarlicious-graph:assembleDebug `
    prepareWatchFaceValidatorCli

if ($LASTEXITCODE -ne 0) {
    throw 'Watchface build or validator setup failed.'
}

$validator =
    Get-ChildItem $toolDir -Filter 'validator-push-cli-*.jar' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

if (-not $validator) {
    throw "Validator CLI not found in $toolDir"
}

Remove-Item $generated -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $generated -Force | Out-Null

$faces = @(
    @{ Module='sugarlicious-analog'; Out='sugarlicious_analog' },
    @{ Module='sugarlicious-orbit';  Out='sugarlicious_orbit'  },
    @{ Module='sugarlicious-rings';  Out='sugarlicious_rings'  },
    @{ Module='sugarlicious-graph';  Out='sugarlicious_graph'  }
)

foreach ($face in $faces) {
    $apk =
        Get-ChildItem ".\watchfaces\$($face.Module)\build\outputs\apk\debug\*.apk" |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

    if (-not $apk) {
        throw "APK missing for $($face.Module)"
    }

    $destApk =
        Join-Path $generated "$($face.Out).apk"

    Copy-Item $apk.FullName $destApk -Force

    Write-Host "Validating $($face.Module)..."

    $validatorOutput =
        & java -jar $validator.FullName `
            "--apk_path=$destApk" `
            "--package_name=app.aapswear" 2>&1 |
            Out-String

    if ($LASTEXITCODE -ne 0) {
        Write-Host $validatorOutput
        throw "Watch Face Push validation failed for $($face.Module)"
    }

    $match =
        [regex]::Match(
            $validatorOutput,
            '(?im)(?:Validation token:|generated token:)\s*([^\r\n]+)'
        )

    if (-not $match.Success) {
        Write-Host $validatorOutput
        throw "Validation token missing for $($face.Module)"
    }

    $tokenPath =
        Join-Path $generated "$($face.Out)_token.txt"

    [System.IO.File]::WriteAllText(
        $tokenPath,
        $match.Groups[1].Value.Trim(),
        (New-Object System.Text.UTF8Encoding($false))
    )

    Write-Host "Prepared $($face.Out)"
}

Write-Host ''
Write-Host 'OK: Watch Face Push assets prepared.'
Write-Host "Assets: $generated"
