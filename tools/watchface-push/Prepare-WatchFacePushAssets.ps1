$ErrorActionPreference = 'Stop'

$root = (Get-Location).Path
$generated = Join-Path $root 'app-wear\build\generated\watchfacePushAssets\watchfaces'
$toolDir = Join-Path $root 'build\watchface-push\tools'

Write-Host 'Building Sugarlicious Watch Face Push packages...'

& .\gradlew `
    :watchfaces:sugarlicious-analog:assembleRelease `
    :watchfaces:sugarlicious-orbit:assembleRelease `
    :watchfaces:sugarlicious-rings:assembleRelease `
    :watchfaces:sugarlicious-graph:assembleRelease `
    :watchfaces:sugarlicious-digital:assembleRelease `
    :watchfaces:aaps-big-chart:assembleRelease :watchfaces:aaps-circle:assembleRelease `
    :watchfaces:aaps-cockpit:assembleRelease :watchfaces:aaps-community:assembleRelease `
    :watchfaces:aaps-digital-style:assembleRelease :watchfaces:aaps-large:assembleRelease `
    :watchfaces:aaps-no-chart:assembleRelease :watchfaces:aaps-standard:assembleRelease `
    :watchfaces:aaps-v2:assembleRelease :watchfaces:aaps-v2-tt-dark:assembleRelease `
    :watchfaces:aaps-v4:assembleRelease :watchfaces:aimico:assembleRelease `
    :watchfaces:analog-g-watch:assembleRelease :watchfaces:blue-ring:assembleRelease `
    :watchfaces:digital-big-graph:assembleRelease :watchfaces:digital-g-watch:assembleRelease `
    :watchfaces:gears:assembleRelease :watchfaces:gota:assembleRelease `
    :watchfaces:lucky-loop-koeln:assembleRelease :watchfaces:p-zero:assembleRelease `
    :watchfaces:robby:assembleRelease :watchfaces:simple-digital:assembleRelease `
    :watchfaces:steam-punk:assembleRelease `
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
    @{ Module='sugarlicious-graph';  Out='sugarlicious_graph'  },
    @{ Module='sugarlicious-digital'; Out='sugarlicious_digital' },
    @{ Module='aaps-big-chart'; Out='aaps_big_chart' },
    @{ Module='aaps-circle'; Out='aaps_circle' },
    @{ Module='aaps-cockpit'; Out='aaps_cockpit' },
    @{ Module='aaps-community'; Out='aaps_community' },
    @{ Module='aaps-digital-style'; Out='aaps_digital_style' },
    @{ Module='aaps-large'; Out='aaps_large' },
    @{ Module='aaps-no-chart'; Out='aaps_no_chart' },
    @{ Module='aaps-standard'; Out='aaps_standard' },
    @{ Module='aaps-v2'; Out='aaps_v2' },
    @{ Module='aaps-v2-tt-dark'; Out='aaps_v2_tt_dark' },
    @{ Module='aaps-v4'; Out='aaps_v4' },
    @{ Module='aimico'; Out='aimico' },
    @{ Module='analog-g-watch'; Out='analog_g_watch' },
    @{ Module='blue-ring'; Out='blue_ring' },
    @{ Module='digital-big-graph'; Out='digital_big_graph' },
    @{ Module='digital-g-watch'; Out='digital_g_watch' },
    @{ Module='gears'; Out='gears' },
    @{ Module='gota'; Out='gota' },
    @{ Module='lucky-loop-koeln'; Out='lucky_loop_koeln' },
    @{ Module='p-zero'; Out='p_zero' },
    @{ Module='robby'; Out='robby' },
    @{ Module='simple-digital'; Out='simple_digital' },
    @{ Module='steam-punk'; Out='steam_punk' }
)

foreach ($face in $faces) {
    $apk =
        Get-ChildItem ".\watchfaces\$($face.Module)\build\outputs\apk\release\*.apk" |
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
