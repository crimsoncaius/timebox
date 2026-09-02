param(
    [ValidateSet("A", "B", "C")]
    [string]$Variant = "A"
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidGradle = Join-Path $PSScriptRoot "android-gradle.ps1"
$localProperties = Join-Path $repositoryRoot "android\local.properties"

$sdkLine = Get-Content -LiteralPath $localProperties |
    Where-Object { $_ -match '^sdk\.dir=' } |
    Select-Object -First 1
if (-not $sdkLine) { throw "sdk.dir is not configured in $localProperties" }

$androidSdk = ($sdkLine -replace '^sdk\.dir=', '').Replace('/', '\')
$adb = Join-Path $androidSdk "platform-tools\adb.exe"

& $androidGradle :app:installDebug
if ($LASTEXITCODE -ne 0) { throw "Debug build or install failed." }

& $adb shell am start -W -a android.intent.action.VIEW `
    -d "timebox://prototype/one-line-day-title?variant=$Variant" com.timebox.android
if ($LASTEXITCODE -ne 0) { throw "Could not open the one-line title prototype." }

Write-Output "Opened one-line Day title prototype variant $Variant."
