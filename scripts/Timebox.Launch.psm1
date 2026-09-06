Set-StrictMode -Version Latest

function Get-TimeboxRepositoryRoot {
    return [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
}

function Get-TimeboxRequiredDatabaseUrl {
    return "postgresql://timebox:timebox@127.0.0.1:15433/timebox"
}

function Get-TimeboxLaunchDirectory {
    $path = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "Temp\timebox-launch"
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    return $path
}

function Get-TimeboxListener {
    param([Parameter(Mandatory)][int]$Port)

    return Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Test-TimeboxProcessIdentity {
    param(
        [Parameter(Mandatory)][ValidateSet("api", "frontend", "emulator")][string]$Purpose,
        [Parameter(Mandatory)]$Process,
        [string]$RepositoryRoot = (Get-TimeboxRepositoryRoot)
    )

    $commandLine = [string]$Process.CommandLine
    $escapedRoot = [regex]::Escape([System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\'))

    switch ($Purpose) {
        "api" {
            return $commandLine -match $escapedRoot -and
                $commandLine -match [regex]::Escape("backend\.venv\Scripts\uvicorn.exe") -and
                $commandLine -match "--port(?:=|\s+)8001(?:\s|$)"
        }
        "frontend" {
            return $commandLine -match $escapedRoot -and
                $commandLine -match [regex]::Escape("frontend\node_modules") -and
                $commandLine -match "vite" -and
                $commandLine -match "--port(?:=|\s+)5176(?:\s|$)"
        }
        "emulator" {
            return [string]$Process.Name -eq "qemu-system-x86_64.exe" -and
                $commandLine -match "-avd\s+Pixel_9a(?:\s|$)"
        }
    }
}

function Assert-TimeboxListenerIdentity {
    param(
        [Parameter(Mandatory)][ValidateSet("api", "frontend", "emulator")][string]$Purpose,
        [Parameter(Mandatory)][int]$Port,
        [string]$RepositoryRoot = (Get-TimeboxRepositoryRoot)
    )

    $listener = Get-TimeboxListener -Port $Port
    if (-not $listener) {
        return $null
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if (-not $process -or -not (Test-TimeboxProcessIdentity -Purpose $Purpose -Process $process -RepositoryRoot $RepositoryRoot)) {
        throw "TCP $Port is occupied by an unverified process."
    }
    return $process
}

function Stop-TimeboxVerifiedProcesses {
    param(
        [Parameter(Mandatory)][ValidateSet("api", "frontend")][string]$Purpose,
        [Parameter(Mandatory)][int]$Port,
        [string]$RepositoryRoot = (Get-TimeboxRepositoryRoot)
    )

    $listenerProcess = Assert-TimeboxListenerIdentity -Purpose $Purpose -Port $Port -RepositoryRoot $RepositoryRoot
    if (-not $listenerProcess) {
        return $false
    }

    $allProcesses = @(Get-CimInstance Win32_Process)
    $matches = @($allProcesses | Where-Object {
        Test-TimeboxProcessIdentity -Purpose $Purpose -Process $_ -RepositoryRoot $RepositoryRoot
    })
    $tree = @(Get-TimeboxProcessTree -Processes $allProcesses -RootProcessIds $matches.ProcessId)
    foreach ($processId in $tree) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }

    $deadline = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $deadline -and (Get-TimeboxListener -Port $Port)) {
        Start-Sleep -Milliseconds 250
    }
    if (Get-TimeboxListener -Port $Port) {
        throw "Verified Timebox $Purpose did not release TCP $Port."
    }
    return $true
}

function Get-TimeboxProcessTree {
    param(
        [Parameter(Mandatory)][object[]]$Processes,
        [Parameter(Mandatory)][int[]]$RootProcessIds
    )

    $depthById = @{}
    $queue = [System.Collections.Generic.Queue[object]]::new()
    foreach ($rootId in $RootProcessIds) {
        $depthById[$rootId] = 0
        $queue.Enqueue([pscustomobject]@{ Id = $rootId; Depth = 0 })
    }
    while ($queue.Count -gt 0) {
        $parent = $queue.Dequeue()
        foreach ($child in $Processes | Where-Object { $_.ParentProcessId -eq $parent.Id }) {
            $childId = [int]$child.ProcessId
            if (-not $depthById.ContainsKey($childId)) {
                $depth = $parent.Depth + 1
                $depthById[$childId] = $depth
                $queue.Enqueue([pscustomobject]@{ Id = $childId; Depth = $depth })
            }
        }
    }
    return $depthById.GetEnumerator() |
        Sort-Object Value -Descending |
        ForEach-Object { [int]$_.Key }
}

function Wait-TimeboxHttp {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            return Invoke-WebRequest -Uri $Uri -TimeoutSec 3
        } catch {
            Start-Sleep -Milliseconds 500
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Uri"
}

function Get-TimeboxSdkRoot {
    param([string]$RepositoryRoot = (Get-TimeboxRepositoryRoot))

    $propertiesPath = Join-Path $RepositoryRoot "android\local.properties"
    if (-not (Test-Path -LiteralPath $propertiesPath)) {
        throw "Android SDK configuration is missing: $propertiesPath"
    }
    $line = Get-Content -LiteralPath $propertiesPath |
        Where-Object { $_ -match '^sdk\.dir=' } |
        Select-Object -First 1
    if (-not $line) {
        throw "android/local.properties does not define sdk.dir"
    }
    $sdkRoot = $line.Substring("sdk.dir=".Length).Trim().Replace('/', '\')
    if (-not (Test-Path -LiteralPath $sdkRoot)) {
        throw "Configured Android SDK does not exist: $sdkRoot"
    }
    return $sdkRoot
}

function Assert-TimeboxDatabaseUrl {
    param([string]$RepositoryRoot = (Get-TimeboxRepositoryRoot))

    $required = Get-TimeboxRequiredDatabaseUrl
    $envPath = Join-Path $RepositoryRoot "backend\.env"
    if (-not (Test-Path -LiteralPath $envPath)) {
        throw "backend/.env is required for the registered local launcher."
    }
    $line = Get-Content -LiteralPath $envPath |
        Where-Object { $_ -match '^DATABASE_URL=' } |
        Select-Object -First 1
    $configured = if ($line) { $line.Substring("DATABASE_URL=".Length).Trim() } else { "" }
    if ($configured -ne $required) {
        throw "backend/.env DATABASE_URL does not match the registered local Timebox database."
    }
    return $required
}

function Invoke-TimeboxNative {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [switch]$AllowFailure
    )

    $output = & $FilePath @ArgumentList 2>&1 | ForEach-Object { $_.ToString() }
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "$FilePath failed with exit code $exitCode`: $($output -join [Environment]::NewLine)"
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = @($output) }
}

function Invoke-TimeboxPsql {
    param([Parameter(Mandatory)][string]$Sql)

    $result = Invoke-TimeboxNative -FilePath "docker" -ArgumentList @(
        "exec", "timebox-postgres", "psql", "-U", "timebox", "-d", "timebox",
        "-v", "ON_ERROR_STOP=1", "-At", "-F", "|", "-c", $Sql
    )
    return ($result.Output -join "`n").Trim()
}

function Get-TimeboxAlembicState {
    param([string]$RepositoryRoot = (Get-TimeboxRepositoryRoot))

    $alembic = Join-Path $RepositoryRoot "backend\.venv\Scripts\alembic.exe"
    $config = Join-Path $RepositoryRoot "backend\alembic.ini"
    if (-not (Test-Path -LiteralPath $alembic)) {
        throw "Alembic executable is missing: $alembic"
    }
    $currentResult = Invoke-TimeboxNative -FilePath $alembic -ArgumentList @("-c", $config, "current")
    $headsResult = Invoke-TimeboxNative -FilePath $alembic -ArgumentList @("-c", $config, "heads")
    $currentText = $currentResult.Output -join "`n"
    $headsText = $headsResult.Output -join "`n"
    $revisionPattern = '(?m)^([0-9][A-Za-z0-9_]+)(?:\s+\(head\))?\s*$'
    $currentMatch = [regex]::Match($currentText, $revisionPattern)
    $headMatch = [regex]::Match($headsText, $revisionPattern)
    if (-not $currentMatch.Success -or -not $headMatch.Success) {
        throw "Could not parse Alembic current/head revisions."
    }
    return [pscustomobject]@{
        Current = $currentMatch.Groups[1].Value
        Head = $headMatch.Groups[1].Value
    }
}

function Get-TimeboxDatabaseEvidence {
    $taskIdentity = Invoke-TimeboxPsql -Sql @'
SELECT count(*), COALESCE(min(id), 0), COALESCE(max(id), 0),
       COALESCE(md5(string_agg(id::text, ',' ORDER BY id)), md5(''))
FROM tasks;
'@
    $plannedIdentity = Invoke-TimeboxPsql -Sql @'
SELECT count(*), COALESCE(min(id), 0), COALESCE(max(id), 0),
       COALESCE(md5(string_agg(id::text, ',' ORDER BY id)), md5(''))
FROM time_blocks WHERE lane = 'planned';
'@
    $counts = Invoke-TimeboxPsql -Sql @'
SELECT
  (SELECT count(*) FROM time_blocks WHERE lane = 'actual'),
  (SELECT count(*) FROM task_completion_operations),
  (SELECT count(*) FROM actual_block_record_operations);
'@
    return [pscustomobject]@{
        TaskIdentity = $taskIdentity
        PlannedIdentity = $plannedIdentity
        MutableCounts = $counts
    }
}

function Compare-TimeboxDatabaseEvidence {
    param(
        [Parameter(Mandatory)]$Before,
        [Parameter(Mandatory)]$After,
        [switch]$CutoverIncluded
    )

    if ($Before.TaskIdentity -ne $After.TaskIdentity) {
        throw "Task identifiers changed during migration."
    }
    if ($Before.PlannedIdentity -ne $After.PlannedIdentity) {
        throw "Planned Block identifiers changed during migration."
    }
    if (-not $CutoverIncluded -and $Before.MutableCounts -ne $After.MutableCounts) {
        throw "Actual/Undo counts changed during a non-cutover migration."
    }
    return $true
}

function Test-TimeboxCutoverInvariants {
    $result = Invoke-TimeboxPsql -Sql @'
SELECT
  (SELECT count(*) FROM time_blocks WHERE lane = 'actual'),
  (SELECT count(*) FROM time_blocks WHERE planned_block_id IS NOT NULL),
  (SELECT count(*) FROM task_completion_operations),
  (SELECT count(*) FROM actual_block_record_operations),
  (SELECT count(*) FROM tasks
    WHERE parent_id IS NOT NULL
      AND COALESCE(recurrence_kind, '') <> 'quota_session'
      AND (status <> 'open' OR completed_at IS NOT NULL OR ready_to_plan
        OR is_blocked OR blocking_reason IS NOT NULL
        OR task_type_id IS NOT NULL OR urgency IS NOT NULL OR importance IS NOT NULL
        OR deadline_date IS NOT NULL OR deadline_at IS NOT NULL
        OR reminder_at IS NOT NULL OR reminder_delivered_at IS NOT NULL)),
  (SELECT count(*) FROM tasks
    WHERE (recurrence_kind = 'quota_parent' AND completed_at IS NOT NULL)
      OR (status <> 'completed' AND completed_at IS NOT NULL)
      OR (status = 'completed'
        AND COALESCE(recurrence_kind, '') <> 'quota_parent'
        AND (parent_id IS NULL OR recurrence_kind = 'quota_session')
        AND completed_at IS DISTINCT FROM updated_at)),
  (SELECT count(*) FROM time_blocks AS block
    LEFT JOIN tasks AS task ON task.id = block.task_id
    WHERE block.lane = 'planned' AND block.task_id IS NOT NULL AND task.id IS NULL);
'@
    $values = $result -split '\|'
    if ($values.Count -ne 7 -or ($values | Where-Object { $_ -ne '0' })) {
        throw "Post-cutover invariants failed: $result"
    }
    return "all-zero"
}

function New-TimeboxDatabaseBackup {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $backupDirectory = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "Temp\timebox-backups"
    New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
    $fileName = "timebox-$timestamp.dump"
    $containerPath = "/tmp/$fileName"
    $hostPath = Join-Path $backupDirectory $fileName

    try {
        Invoke-TimeboxNative -FilePath "docker" -ArgumentList @(
            "exec", "timebox-postgres", "pg_dump", "-U", "timebox", "-d", "timebox",
            "-Fc", "-f", $containerPath
        ) | Out-Null
        Invoke-TimeboxNative -FilePath "docker" -ArgumentList @(
            "exec", "timebox-postgres", "pg_restore", "-l", $containerPath
        ) | Out-Null
        Invoke-TimeboxNative -FilePath "docker" -ArgumentList @(
            "cp", "timebox-postgres`:$containerPath", $hostPath
        ) | Out-Null
    } finally {
        Invoke-TimeboxNative -FilePath "docker" -ArgumentList @(
            "exec", "timebox-postgres", "rm", "-f", $containerPath
        ) -AllowFailure | Out-Null
    }

    $file = Get-Item -LiteralPath $hostPath
    $hash = Get-FileHash -LiteralPath $hostPath -Algorithm SHA256
    return [pscustomobject]@{
        Path = $file.FullName
        SizeBytes = $file.Length
        Sha256 = $hash.Hash
    }
}

function Invoke-TimeboxPortObservation {
    param(
        [Parameter(Mandatory)][string]$Project,
        [Parameter(Mandatory)][string]$Purpose,
        [Parameter(Mandatory)][ValidateSet("verified-running", "unbound")][string]$State,
        [Parameter(Mandatory)][string]$Detail
    )

    $profile = [Environment]::GetFolderPath("UserProfile")
    $registry = Join-Path $profile ".codex\skills\manage-dev-ports\scripts\port_registry.py"
    if (-not (Test-Path -LiteralPath $registry)) {
        return
    }
    Invoke-TimeboxNative -FilePath "python" -ArgumentList @(
        $registry, "observe", "--application", "Timebox", "--project", $Project,
        "--purpose", $Purpose, "--state", $State, "--detail", $Detail
    ) | Out-Null
}

function Write-TimeboxStatus {
    param([Parameter(Mandatory)]$Status)

    $statusPath = Join-Path (Get-TimeboxLaunchDirectory) "status.json"
    $Status | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $statusPath -Encoding utf8
    return $statusPath
}

Export-ModuleMember -Function *
