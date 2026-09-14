param([string]$Owner = "dark-theme-screenshots-$PID")
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$helper = Join-Path $PSScriptRoot "android-emulator.py"

& (Join-Path $PSScriptRoot "android-gradle.ps1") :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$reservationText = & python $helper acquire --owner $Owner
if ($LASTEXITCODE -ne 0) { throw "Could not reserve an emulator. Inspect emulator helper status." }
$reservation = ($reservationText | Select-Object -Last 1) | ConvertFrom-Json

function Invoke-ReservedAdb {
    & python $helper adb $reservation.token @args
    if ($LASTEXITCODE -ne 0) { throw "Reserved ADB operation failed ($LASTEXITCODE)." }
}

try {
    $appApk = Join-Path $repositoryRoot "android\app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $repositoryRoot "android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    Invoke-ReservedAdb install -r $appApk
    Invoke-ReservedAdb install -r $testApk
    $result = Invoke-ReservedAdb shell am instrument -w `
        -e class com.timebox.android.ui.visual.DarkThemeScreenshotTest `
        com.timebox.android.test/androidx.test.runner.AndroidJUnitRunner
    $result | Write-Output
    if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Screenshot instrumentation did not report success."
    }

    $outputDirectory = Join-Path $repositoryRoot "artifacts\android-dark-theme"
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    Invoke-ReservedAdb pull "/sdcard/Android/data/com.timebox.android/files/visual-regression/." $outputDirectory
    Write-Output "Dark-theme screenshots written to $outputDirectory"
} finally {
    & python $helper release $reservation.token
    if ($LASTEXITCODE -ne 0) { Write-Warning "Reservation retained; inspect emulator helper status." }
}
