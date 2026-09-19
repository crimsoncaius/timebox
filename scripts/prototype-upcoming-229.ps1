# Throwaway issue 229 prototype. Builds, reserves a device, and retains it for review.
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/android-gradle.ps1" :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$reservation = python "$PSScriptRoot/android-emulator.py" acquire --owner 'issue-229 upcoming prototype' | ConvertFrom-Json
$token = $reservation.token
try {
    python "$PSScriptRoot/android-emulator.py" adb $token install -r "$PSScriptRoot/../android/app/build/outputs/apk/debug/app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
    python "$PSScriptRoot/android-emulator.py" adb $token shell am start -a android.intent.action.VIEW -d 'timebox://prototype/recurring-details?flow=details\&layout=upcoming\&mode=scheduled\&variant=A' com.timebox.android
    if ($LASTEXITCODE -ne 0) { throw 'Launch failed' }
    python "$PSScriptRoot/android-emulator.py" review $token
    Write-Host "Review device: $($reservation.serial); token: $token"
} catch {
    python "$PSScriptRoot/android-emulator.py" release $token
    throw
}
