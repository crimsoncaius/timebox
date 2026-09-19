param([ValidateSet('A', 'B', 'C')][string]$Variant = 'A')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
& ./scripts/android-gradle.ps1 assembleDebug
if ($LASTEXITCODE) { throw 'Prototype build failed' }
$reservation = python scripts/android-emulator.py acquire --owner 'issue-228 prototype review' | ConvertFrom-Json
if ($LASTEXITCODE) { throw 'Could not reserve emulator' }
$token = $reservation.token
try {
    python scripts/android-emulator.py adb $token install -r android/app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE) { throw 'Install failed' }
    python scripts/android-emulator.py adb $token shell am start -a android.intent.action.VIEW -d "timebox://prototype/ongoing-block?variant=$Variant" -n com.timebox.android/.ui.day.prototype.OngoingBlockPrototypeActivity
    if ($LASTEXITCODE) { throw 'Launch failed' }
    python scripts/android-emulator.py review $token
} catch {
    python scripts/android-emulator.py release $token
    throw
}
