param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$size = 450
$sourceSize = 512.0
$scale = $size / $sourceSize

$white = [Windows.Media.Brushes]::White
$red = [Windows.Media.SolidColorBrush]::new([Windows.Media.Color]::FromArgb(255, 255, 0, 0))
$grey = [Windows.Media.SolidColorBrush]::new([Windows.Media.Color]::FromArgb(255, 188, 188, 188))
$black = [Windows.Media.Brushes]::Black
$secondGeometry = [Windows.Media.Geometry]::Parse(
    "M258,264.25 v31.75 h-4 v-31.75 c-3.73,-0.9 -6.5,-4.25 -6.5,-8.25 s2.77,-7.35 6.5,-8.25 V6 h4 v241.75 c3.73,0.9 6.5,4.25 6.5,8.25 s-2.77,7.35 -6.5,8.25 Z"
)

function Write-HandPng {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [ValidateSet("hour", "minute", "second")]
        [string]$Kind
    )

    $visual = [Windows.Media.DrawingVisual]::new()
    $drawing = $visual.RenderOpen()
    $drawing.PushTransform([Windows.Media.ScaleTransform]::new($scale, $scale))

    if ($Kind -eq "hour" -or $Kind -eq "minute") {
        $drawing.DrawRectangle($white, $null, [Windows.Rect]::new(252.75, 224.44, 6.5, 29.56))
        $top = if ($Kind -eq "hour") { 113.57 } else { 34.47 }
        $height = if ($Kind -eq "hour") { 114.0 } else { 193.1 }
        $drawing.DrawRoundedRectangle(
            $white,
            $null,
            [Windows.Rect]::new(243.0, $top, 26.0, $height),
            13.0,
            13.0
        )
    }

    if ($Kind -eq "minute") {
        $drawing.DrawEllipse($grey, $null, [Windows.Point]::new(256.0, 256.0), 12.0, 12.0)
    }

    if ($Kind -eq "second") {
        $drawing.DrawGeometry($red, $null, $secondGeometry)
        $drawing.DrawEllipse($black, $null, [Windows.Point]::new(256.0, 256.0), 4.0, 4.0)
    }

    $drawing.Pop()
    $drawing.Close()

    $bitmap = [Windows.Media.Imaging.RenderTargetBitmap]::new(
        $size,
        $size,
        96.0,
        96.0,
        [Windows.Media.PixelFormats]::Pbgra32
    )
    $bitmap.Render($visual)

    $encoder = [Windows.Media.Imaging.PngBitmapEncoder]::new()
    $encoder.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Create, [IO.FileAccess]::Write)
    try {
        $encoder.Save($stream)
    } finally {
        $stream.Dispose()
    }
}

$faces = @("analog", "orbit", "rings", "graph")
foreach ($face in $faces) {
    $destination = Join-Path $root "watchfaces\sugarlicious-$face\src\main\res\drawable-nodpi"
    Write-HandPng -Path (Join-Path $destination "hour_hand.png") -Kind hour
    Write-HandPng -Path (Join-Path $destination "minute_hand.png") -Kind minute
    Write-HandPng -Path (Join-Path $destination "second_hand.png") -Kind second
}

Write-Host "Rendered uploaded Sugarlicious hand geometry for all analog faces."
