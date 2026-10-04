param(
    [Parameter(Mandatory = $true)][string]$Token,
    [ValidateSet('page', 'switch')][string]$Variant = 'page'
)
$ErrorActionPreference = 'Stop'
$helper = Join-Path $PSScriptRoot 'android-emulator.py'
python $helper adb $Token shell am start -W -a android.intent.action.VIEW -d "timebox://prototype/goal-archive?variant=$Variant" -p com.timebox.android.goalarchive
exit $LASTEXITCODE
