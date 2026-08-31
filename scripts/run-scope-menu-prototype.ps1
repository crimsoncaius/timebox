param(
    [ValidateSet("A", "B", "C")]
    [string]$Variant = "A"
)

$ErrorActionPreference = "Stop"

$androidGradle = Join-Path $PSScriptRoot "android-gradle.ps1"
$androidSdk = "C:\Users\Caius\AppData\Local\Android\Sdk"
$adb = Join-Path $androidSdk "platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $androidGradle)) {
    throw "Android Gradle launcher not found at $androidGradle"
}
if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB not found at $adb"
}

& $androidGradle :app:installDebug
if ($LASTEXITCODE -ne 0) { throw "Debug build or install failed." }

& $adb shell am force-stop com.timebox.android
if ($LASTEXITCODE -ne 0) { throw "Could not stop the existing debug app instance." }

& $adb shell am start -W -a android.intent.action.VIEW `
    -d "timebox://prototype/scope-menu?variant=$Variant" com.timebox.android
if ($LASTEXITCODE -ne 0) { throw "Could not open the prototype activity." }

Write-Output "Opened scope-menu prototype variant $Variant."
