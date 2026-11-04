param(
    [string]$Destination = (Join-Path $PSScriptRoot '..\app\src\main\assets\detector\efficientdet_lite0_int8.tflite')
)

$ErrorActionPreference = 'Stop'
$source = 'https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite'
$expectedSha256 = '0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB'
$expectedBytes = 4602795
$resolved = [System.IO.Path]::GetFullPath($Destination)
$directory = Split-Path -Parent $resolved
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$temporary = "$resolved.download"
try {
    Invoke-WebRequest -Uri $source -OutFile $temporary
    $actualBytes = (Get-Item -LiteralPath $temporary).Length
    $actualSha256 = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash
    if ($actualBytes -ne $expectedBytes -or $actualSha256 -ne $expectedSha256) {
        throw "EfficientDet model mismatch: bytes=$actualBytes SHA256=$actualSha256"
    }
    Move-Item -LiteralPath $temporary -Destination $resolved -Force
    Write-Output "Verified EfficientDet-Lite0 INT8 model: $resolved"
} finally {
    Remove-Item -LiteralPath $temporary -ErrorAction SilentlyContinue
}
