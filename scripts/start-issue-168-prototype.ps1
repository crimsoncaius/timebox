# Throwaway native Android prototype. Builds an isolated APK and retains a managed review device.
param([string]$ReservationToken = '')
$ErrorActionPreference = 'Stop'
$prototypeRoot = Split-Path $PSScriptRoot -Parent
$helper = Join-Path $PSScriptRoot 'android-emulator.py'
if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
Push-Location (Join-Path $prototypeRoot 'android')
try {
    & .\gradlew.bat :app:assembleDebug -PrecommendationPrototype=true --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Prototype build failed.' }
} finally { Pop-Location }
Push-Location $prototypeRoot
$retained = $false
try {
    if (-not $ReservationToken) {
        $reservation = & python $helper acquire --owner 'issue-168: native Android prototype review'
        if ($LASTEXITCODE -ne 0) { throw 'Emulator reservation failed.' }
        $ReservationToken = ($reservation | ConvertFrom-Json).token
    }
    & python $helper adb $ReservationToken install -r android/app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Prototype installation failed.' }
    & python $helper adb $ReservationToken shell am start -S -n com.timebox.android.prototype168/com.timebox.android.ui.battleplan.prototype.TaskTypeRecommendationPrototypeActivity
    if ($LASTEXITCODE -ne 0) { throw 'Prototype launch failed.' }
    & python $helper review $ReservationToken
    if ($LASTEXITCODE -ne 0) { throw 'Could not retain review device.' }
    $retained = $true
    Write-Output "Review token: $ReservationToken"
} finally {
    if ($ReservationToken -and -not $retained) { & python $helper release $ReservationToken }
    Pop-Location
}
