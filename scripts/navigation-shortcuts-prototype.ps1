param(
    [string]$Token = '43683640e3424ed0ac4bfef0b81de463',
    [ValidateSet('A', 'B', 'C', 'D')][string]$Variant = 'A'
)
$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'android-emulator.py') adb $Token shell am start -W -a android.intent.action.VIEW -d "timebox://prototype/navigation-shortcuts?variant=$Variant" -n com.timebox.android/.MainActivity
exit $LASTEXITCODE
