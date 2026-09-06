[CmdletBinding()]
param(
    [switch]$SkipAndroid,
    [switch]$Json
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot "Timebox.Launch.psm1") -Force

$repositoryRoot = Get-TimeboxRepositoryRoot
$frontendRoot = Join-Path $repositoryRoot "frontend"
$androidRoot = Join-Path $repositoryRoot "android"
$launchDirectory = Get-TimeboxLaunchDirectory
$databaseUrl = Assert-TimeboxDatabaseUrl -RepositoryRoot $repositoryRoot
$env:DATABASE_URL = $databaseUrl
$backup = $null
$migration = "not-required"
$databaseInvariant = "schema-current"

$containerJson = docker inspect timebox-postgres 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "The registered timebox-postgres container does not exist."
}
$container = ($containerJson | ConvertFrom-Json)[0]
if ($container.Config.Image -ne "postgres:16-alpine") {
    throw "timebox-postgres uses the unexpected image $($container.Config.Image)."
}
$bindings = $container.HostConfig.PortBindings.'5432/tcp'
if (-not ($bindings | Where-Object { $_.HostPort -eq "15433" })) {
    throw "timebox-postgres is not published on the registered TCP port 15433."
}
if (-not $container.State.Running) {
    docker start timebox-postgres | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not start timebox-postgres." }
}

$databaseDeadline = (Get-Date).AddSeconds(30)
do {
    docker exec timebox-postgres pg_isready -U timebox -d timebox *> $null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $databaseDeadline)
if ($LASTEXITCODE -ne 0) { throw "timebox-postgres did not become ready." }
Invoke-TimeboxPortObservation -Project $repositoryRoot -Purpose "database" -State "verified-running" -Detail "Repository launcher verified timebox-postgres on TCP 15433 and pg_isready accepted connections."

$alembicState = Get-TimeboxAlembicState -RepositoryRoot $repositoryRoot
if ($alembicState.Current -ne $alembicState.Head) {
    $history = Invoke-TimeboxNative -FilePath (Join-Path $repositoryRoot "backend\.venv\Scripts\alembic.exe") -ArgumentList @(
        "-c", (Join-Path $repositoryRoot "backend\alembic.ini"), "history", "-r", "$($alembicState.Current):$($alembicState.Head)"
    )
    $cutoverIncluded = ($history.Output -join "`n") -match "->\s+015_definitive_legacy_cutover(?:\s|\(|,)"
    $before = Get-TimeboxDatabaseEvidence
    $backup = New-TimeboxDatabaseBackup

    if (Get-TimeboxListener -Port 8001) {
        Stop-TimeboxVerifiedProcesses -Purpose api -Port 8001 -RepositoryRoot $repositoryRoot | Out-Null
    }
    try {
        Invoke-TimeboxNative -FilePath (Join-Path $repositoryRoot "backend\.venv\Scripts\alembic.exe") -ArgumentList @(
            "-c", (Join-Path $repositoryRoot "backend\alembic.ini"), "upgrade", "head"
        ) | Out-Null
        $after = Get-TimeboxDatabaseEvidence
        Compare-TimeboxDatabaseEvidence -Before $before -After $after -CutoverIncluded:$cutoverIncluded | Out-Null
        if ($cutoverIncluded) {
            $databaseInvariant = Test-TimeboxCutoverInvariants
        } else {
            $databaseInvariant = "identities-and-mutable-counts-preserved"
        }
        $migration = "applied"
        $alembicState = Get-TimeboxAlembicState -RepositoryRoot $repositoryRoot
        if ($alembicState.Current -ne $alembicState.Head) {
            throw "Alembic did not reach head after migration."
        }
    } catch {
        throw "Migration failed; API remains stopped. Verified backup: $($backup.Path). $($_.Exception.Message)"
    }
}

$apiProcess = Assert-TimeboxListenerIdentity -Purpose api -Port 8001 -RepositoryRoot $repositoryRoot
if (-not $apiProcess) {
    $apiProcess = Start-Process -FilePath (Join-Path $repositoryRoot "backend\.venv\Scripts\uvicorn.exe") -ArgumentList @(
        "--app-dir", "backend", "app.main:app", "--reload", "--reload-dir", "backend",
        "--host", "127.0.0.1", "--port", "8001"
    ) -WorkingDirectory $repositoryRoot -RedirectStandardOutput (Join-Path $launchDirectory "api.stdout.log") -RedirectStandardError (Join-Path $launchDirectory "api.stderr.log") -WindowStyle Hidden -PassThru
}
$readyResponse = Wait-TimeboxHttp -Uri "http://127.0.0.1:8001/ready" -TimeoutSeconds 30
$readyBody = $readyResponse.Content | ConvertFrom-Json
if ($readyResponse.StatusCode -ne 200 -or $readyBody.status -ne "ready") {
    throw "API readiness check failed."
}
$today = (Invoke-RestMethod -Uri "http://127.0.0.1:8001/health" -TimeoutSec 5).today
Invoke-RestMethod -Uri "http://127.0.0.1:8001/days/$today" -TimeoutSec 10 | Out-Null
Invoke-TimeboxPortObservation -Project $repositoryRoot -Purpose "API" -State "verified-running" -Detail "Repository launcher verified this workspace API on TCP 8001; /ready and /days/$today succeeded."

$frontendProcess = Assert-TimeboxListenerIdentity -Purpose frontend -Port 5176 -RepositoryRoot $repositoryRoot
if (-not $frontendProcess) {
    $env:VITE_API_PROXY_TARGET = "http://127.0.0.1:8001"
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $vite = Join-Path $frontendRoot "node_modules\vite\bin\vite.js"
    if (-not (Test-Path -LiteralPath $vite)) { throw "Frontend dependencies are not installed." }
    $frontendProcess = Start-Process -FilePath $node -ArgumentList @(
        $vite, "--host=127.0.0.1", "--port=5176"
    ) -WorkingDirectory $frontendRoot -RedirectStandardOutput (Join-Path $launchDirectory "frontend.stdout.log") -RedirectStandardError (Join-Path $launchDirectory "frontend.stderr.log") -WindowStyle Hidden -PassThru
}
$frontendResponse = Wait-TimeboxHttp -Uri "http://127.0.0.1:5176/" -TimeoutSeconds 30
if ($frontendResponse.StatusCode -ne 200) { throw "Frontend root did not return HTTP 200." }
Invoke-RestMethod -Uri "http://127.0.0.1:5176/api/days/$today" -TimeoutSec 10 | Out-Null
Invoke-TimeboxPortObservation -Project $frontendRoot -Purpose "dev" -State "verified-running" -Detail "Repository launcher verified this workspace Vite process on TCP 5176 and /api/days/$today through the API proxy."

$androidStatus = "skipped"
if (-not $SkipAndroid) {
    $sdkRoot = Get-TimeboxSdkRoot -RepositoryRoot $repositoryRoot
    $adb = Join-Path $sdkRoot "platform-tools\adb.exe"
    $emulator = Join-Path $sdkRoot "emulator\emulator.exe"
    & $adb start-server | Out-Null
    $deviceReady = (& $adb devices) -match '^emulator-5554\s+device$'
    if (-not $deviceReady) {
        if (Get-TimeboxListener -Port 5554) {
            Assert-TimeboxListenerIdentity -Purpose emulator -Port 5554 -RepositoryRoot $repositoryRoot | Out-Null
        } else {
            Start-Process -FilePath $emulator -ArgumentList @("-avd", "Pixel_9a") -WorkingDirectory $repositoryRoot | Out-Null
        }
    }
    $bootDeadline = (Get-Date).AddMinutes(3)
    do {
        $online = (& $adb devices) -match '^emulator-5554\s+device$'
        $booted = if ($online) { (& $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null).Trim() } else { "" }
        if ($booted -eq "1") { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $bootDeadline)
    if ($booted -ne "1") { throw "Pixel_9a did not finish booting." }

    & (Join-Path $repositoryRoot "scripts\android-gradle.ps1") ":app:installDebug"
    if ($LASTEXITCODE -ne 0) { throw "Android debug installation failed." }
    & $adb -s emulator-5554 shell am start -W -n "com.timebox.android/.MainActivity" | Out-Null
    Start-Sleep -Seconds 1
    $foreground = & $adb -s emulator-5554 shell dumpsys activity activities |
        Select-String 'topResumedActivity=.*com\.timebox\.android/\.MainActivity'
    if (-not $foreground) { throw "Timebox Android activity is not foregrounded." }
    Assert-TimeboxListenerIdentity -Purpose emulator -Port 5554 -RepositoryRoot $repositoryRoot | Out-Null
    Invoke-TimeboxPortObservation -Project $androidRoot -Purpose "adb-server" -State "verified-running" -Detail "Repository launcher verified the Android SDK adb server on TCP 5037 and emulator-5554 online."
    Invoke-TimeboxPortObservation -Project $androidRoot -Purpose "emulator-console" -State "verified-running" -Detail "Repository launcher verified boot-completed Pixel_9a on TCP 5554."
    Invoke-TimeboxPortObservation -Project $androidRoot -Purpose "emulator-adb" -State "verified-running" -Detail "Repository launcher verified Pixel_9a ADB transport on TCP 5555 and Timebox MainActivity foregrounded."
    $androidStatus = "foreground"
}

$status = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    repository = $repositoryRoot
    surfaces = [ordered]@{
        frontend = [ordered]@{ status = "ready"; url = "http://127.0.0.1:5176/" }
        api = [ordered]@{ status = "ready"; url = "http://127.0.0.1:8001" }
        android = [ordered]@{ status = $androidStatus; avd = if ($SkipAndroid) { $null } else { "Pixel_9a" } }
    }
    database = [ordered]@{
        status = "ready"
        port = 15433
        alembicCurrent = $alembicState.Current
        alembicHead = $alembicState.Head
        migration = $migration
        invariant = $databaseInvariant
        backup = $backup
    }
}
$statusPath = Write-TimeboxStatus -Status $status

if ($Json) {
    $status | ConvertTo-Json -Depth 8
} else {
    Write-Output "Timebox ready: web http://127.0.0.1:5176/, API http://127.0.0.1:8001, Android $androidStatus."
    Write-Output "Alembic $($alembicState.Current); database invariant $databaseInvariant; status $statusPath"
    if ($backup) { Write-Output "Verified backup $($backup.Path) ($($backup.SizeBytes) bytes, SHA-256 $($backup.Sha256))" }
}
