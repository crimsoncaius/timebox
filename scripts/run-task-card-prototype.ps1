param([ValidateSet('A','B','C','D','E','F','G')][string]$Variant = 'E')
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'android-gradle.ps1') :app:installDebug
if ($LASTEXITCODE -ne 0) { throw 'Prototype build/install failed.' }
& 'C:\Users\Caius\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell am start -W -a android.intent.action.VIEW -d "timebox://prototype/task-cards?variant=$Variant" com.timebox.android
if ($LASTEXITCODE -ne 0) { throw 'Prototype launch failed.' }
