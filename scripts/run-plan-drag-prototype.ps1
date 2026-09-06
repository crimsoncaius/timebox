param([ValidateSet('A', 'B', 'C')][string]$Variant = 'A')
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'android-gradle.ps1') :app:installDebug
if ($LASTEXITCODE -ne 0) { throw 'Prototype build/install failed.' }
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -W -a android.intent.action.VIEW -d "timebox://prototype/plan-drag?variant=$Variant" com.timebox.android
if ($LASTEXITCODE -ne 0) { throw 'Prototype launch failed.' }
