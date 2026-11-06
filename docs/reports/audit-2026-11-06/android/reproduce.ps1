$ErrorActionPreference = 'Stop'
$auditRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$probeTarget = Join-Path $auditRoot 'android/app/src/test/java/com/timebox/android/ui/battleplan/AuditReproTest.kt'
if (Test-Path -LiteralPath $probeTarget) { throw "Refusing to replace existing file: $probeTarget" }
$previousAndroidHome = $env:ANDROID_HOME
try {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'AuditReproTest.kt') -Destination $probeTarget
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    & (Join-Path $auditRoot 'scripts/android-gradle.ps1') testDebugUnitTest --tests '*AuditReproTest'
    $probeExitCode = $LASTEXITCODE
    Copy-Item -LiteralPath (Join-Path $auditRoot 'android/app/build/test-results/testDebugUnitTest/TEST-com.timebox.android.ui.battleplan.AuditReproTest.xml') -Destination (Join-Path $PSScriptRoot 'AuditReproTest-results.xml')
} finally {
    Remove-Item -LiteralPath $probeTarget
    $env:ANDROID_HOME = $previousAndroidHome
}
exit $probeExitCode
