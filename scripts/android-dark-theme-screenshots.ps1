$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$localProperties = Join-Path $repositoryRoot "android\local.properties"
if (-not (Test-Path -LiteralPath $localProperties)) {
    throw "Android local.properties was not found at $localProperties"
}

$sdkLine = Get-Content -LiteralPath $localProperties |
    Where-Object { $_ -match '^sdk\.dir=' } |
    Select-Object -First 1
if (-not $sdkLine) {
    throw "sdk.dir is not configured in $localProperties"
}

$androidSdk = ($sdkLine -replace '^sdk\.dir=', '').Replace('/', '\')
$adb = Join-Path $androidSdk "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb was not found at $adb"
}

& (Join-Path $PSScriptRoot "android-gradle.ps1") :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$appApk = Join-Path $repositoryRoot "android\app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repositoryRoot "android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
& $adb install -r $appApk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb install -r $testApk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $adb shell am instrument -w `
    -e class com.timebox.android.ui.visual.DarkThemeScreenshotTest `
    com.timebox.android.test/androidx.test.runner.AndroidJUnitRunner
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$outputDirectory = Join-Path $repositoryRoot "artifacts\android-dark-theme"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
& $adb pull "/sdcard/Android/data/com.timebox.android/files/visual-regression/." $outputDirectory
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Output "Dark-theme screenshots written to $outputDirectory"
