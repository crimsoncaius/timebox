param(
    [ValidateSet("A", "B", "C")]
    [string]$Variant = "A"
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repositoryRoot "android"
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$androidSdk = "C:\Users\Caius\AppData\Local\Android\Sdk"
$adb = Join-Path $androidSdk "platform-tools\adb.exe"
$androidStudioJdk = "C:\Program Files\Android\Android Studio\jbr"

if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}
if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB not found at $adb"
}
if (-not (Test-Path -LiteralPath (Join-Path $androidStudioJdk "bin\java.exe"))) {
    throw "Android Studio JDK not found at $androidStudioJdk"
}

Push-Location $androidRoot
try {
    & $gradleWrapper "-Dorg.gradle.java.home=$androidStudioJdk" :app:installDebug
    if ($LASTEXITCODE -ne 0) { throw "Debug build or install failed." }
} finally {
    Pop-Location
}

& $adb shell am start -W -a android.intent.action.VIEW `
    -d "timebox://prototype/day-header?variant=$Variant" com.timebox.android
if ($LASTEXITCODE -ne 0) { throw "Could not open the prototype activity." }

Write-Output "Opened Day header prototype variant $Variant."
