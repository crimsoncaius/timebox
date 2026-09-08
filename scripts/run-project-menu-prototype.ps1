param([ValidateSet('A', 'B', 'C')][string]$Variant = 'A')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\android-gradle.ps1" :app:installDebug
if ($LASTEXITCODE -ne 0) { throw 'Prototype build/install failed' }
& 'C:\Users\Caius\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell am start -W -a android.intent.action.VIEW -d "timebox://prototype/project-menu?variant=$Variant" com.timebox.android
