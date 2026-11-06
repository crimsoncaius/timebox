$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$frontend = Join-Path $repo 'frontend'
$probe = Join-Path $frontend 'audit-repro.test.tsx'
if (Test-Path -LiteralPath $probe) { throw "Refusing to overwrite $probe" }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'audit-repro.test.tsx') -Destination $probe
Push-Location $frontend
try {
    # Vitest intentionally writes assertion failures to stderr; Windows PowerShell
    # must not turn that diagnostic stream into a terminating script error.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $frontend 'node_modules/.bin/vitest.cmd') run audit-repro.test.tsx --maxWorkers=1 --reporter=verbose
    $auditExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
    Pop-Location
    Remove-Item -LiteralPath $probe
}
exit $auditExitCode
