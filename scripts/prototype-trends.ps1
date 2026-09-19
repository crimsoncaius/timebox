# THROWAWAY #10. Build, reserve a managed device, and open the Trends comparison.
param([ValidateSet('A', 'B', 'C')][string]$Variant = 'A')
$ErrorActionPreference = 'Stop'
$prototypeRoot = Split-Path -Parent $PSScriptRoot
Push-Location $prototypeRoot
try {
    if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    & "$PSScriptRoot/android-gradle.ps1" assembleDebug '-PtrendsPrototype=true' '-PreviewApplicationIdSuffix=.trendsprototype'
    if ($LASTEXITCODE -ne 0) { throw 'Prototype build failed' }
    $reservation = python scripts/android-emulator.py acquire --owner 'issue-10: Trends prototype review' | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Emulator acquisition failed; inspect helper status for retained reservations' }
    $prototypeToken = $reservation.token
    try {
        python scripts/android-emulator.py adb $prototypeToken install -r android/app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
        python scripts/android-emulator.py adb $prototypeToken shell am start -n com.timebox.android.trendsprototype/com.timebox.android.MainActivity -d "timebox://chronicle?variant=$Variant"
        if ($LASTEXITCODE -ne 0) { throw 'Launch failed' }
        python scripts/android-emulator.py review $prototypeToken
        Write-Host "Prototype review: $($reservation.serial), token $prototypeToken"
    } catch {
        python scripts/android-emulator.py release $prototypeToken
        throw
    }
} finally { Pop-Location }
