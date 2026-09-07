param([ValidateSet("A", "B", "C", "D")][string]$Variant = "D")
$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "android-gradle.ps1") :app:installDebug
if ($LASTEXITCODE -ne 0) { throw "Debug build or install failed." }
$prototypeAdb = "C:\Users\Caius\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $prototypeAdb shell am start -S -W -a android.intent.action.VIEW -d "timebox://prototype/move-project?variant=$Variant" -n com.timebox.android/.ui.battleplan.prototype.MoveProjectPrototypeActivity
if ($LASTEXITCODE -ne 0) { throw "Could not launch prototype." }
