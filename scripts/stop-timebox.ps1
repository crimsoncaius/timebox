[CmdletBinding()]
param(
    [switch]$KeepDatabase,
    [switch]$KeepAndroid,
    [switch]$Json
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot "Timebox.Launch.psm1") -Force

$repositoryRoot = Get-TimeboxRepositoryRoot
$frontendRoot = Join-Path $repositoryRoot "frontend"
$androidRoot = Join-Path $repositoryRoot "android"
$stopped = [ordered]@{ frontend = $false; api = $false; android = $false; database = $false }

$stopped.frontend = Stop-TimeboxVerifiedProcesses -Purpose frontend -Port 5176 -RepositoryRoot $repositoryRoot
if ($stopped.frontend) {
    Invoke-TimeboxPortObservation -Project $frontendRoot -Purpose "dev" -State "unbound" -Detail "Repository stop script verified TCP 5176 unbound after stopping the identified Timebox Vite process."
}
$stopped.api = Stop-TimeboxVerifiedProcesses -Purpose api -Port 8001 -RepositoryRoot $repositoryRoot
if ($stopped.api) {
    Invoke-TimeboxPortObservation -Project $repositoryRoot -Purpose "API" -State "unbound" -Detail "Repository stop script verified TCP 8001 unbound after stopping the identified Timebox API processes."
}

if (-not $KeepAndroid) {
    $emulatorProcess = Assert-TimeboxListenerIdentity -Purpose emulator -Port 5554 -RepositoryRoot $repositoryRoot
    if ($emulatorProcess) {
        $sdkRoot = Get-TimeboxSdkRoot -RepositoryRoot $repositoryRoot
        $adb = Join-Path $sdkRoot "platform-tools\adb.exe"
        & $adb -s emulator-5554 emu kill | Out-Null
        $deadline = (Get-Date).AddSeconds(30)
        while ((Get-Date) -lt $deadline -and (Get-TimeboxListener -Port 5554)) {
            Start-Sleep -Milliseconds 500
        }
        if (Get-TimeboxListener -Port 5554) { throw "Pixel_9a did not release TCP 5554." }
        $stopped.android = $true
        Invoke-TimeboxPortObservation -Project $androidRoot -Purpose "emulator-console" -State "unbound" -Detail "Repository stop script gracefully stopped verified Pixel_9a and observed TCP 5554 unbound."
        Invoke-TimeboxPortObservation -Project $androidRoot -Purpose "emulator-adb" -State "unbound" -Detail "Repository stop script gracefully stopped verified Pixel_9a and observed TCP 5555 unbound."
    }
}

if (-not $KeepDatabase) {
    $containerJson = docker inspect timebox-postgres 2>$null
    if ($LASTEXITCODE -eq 0) {
        $container = ($containerJson | ConvertFrom-Json)[0]
        $bindings = $container.HostConfig.PortBindings.'5432/tcp'
        $verified = $container.Config.Image -eq "postgres:16-alpine" -and
            ($bindings | Where-Object { $_.HostPort -eq "15433" })
        if (-not $verified) { throw "Refusing to stop an unverified timebox-postgres container." }
        if ($container.State.Running) {
            docker stop timebox-postgres | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not stop timebox-postgres." }
            $stopped.database = $true
            Invoke-TimeboxPortObservation -Project $repositoryRoot -Purpose "database" -State "unbound" -Detail "Repository stop script stopped the verified timebox-postgres container and observed TCP 15433 unbound."
        }
    }
}

if ($Json) {
    $stopped | ConvertTo-Json
} else {
    Write-Output "Timebox stop complete: $($stopped | ConvertTo-Json -Compress)"
}
