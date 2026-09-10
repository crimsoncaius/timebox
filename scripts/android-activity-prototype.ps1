$ErrorActionPreference = 'Stop'
$prototypeRoot = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& (Join-Path $PSScriptRoot 'android-gradle.ps1') assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Prototype build failed' }
& $adbPath install -r (Join-Path $prototypeRoot 'android/app/build/outputs/apk/debug/app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Prototype installation failed' }
& $adbPath shell am start -n com.timebox.android.activityprototype/com.timebox.android.ui.day.prototype.ActivityTrackingPrototypeActivity
