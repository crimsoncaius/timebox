$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) "Timebox.Launch.psm1"
Import-Module $modulePath -Force

Describe "Timebox launcher safety helpers" {
    It "accepts the registered API process identity" {
        $process = [pscustomobject]@{
            Name = "python.exe"
            CommandLine = 'python.exe "C:\repo\backend\.venv\Scripts\uvicorn.exe" --host 127.0.0.1 --port 8001'
        }
        Test-TimeboxProcessIdentity -Purpose api -Process $process -RepositoryRoot "C:\repo" | Should Be $true
    }

    It "rejects another checkout on the API port" {
        $process = [pscustomobject]@{
            Name = "python.exe"
            CommandLine = 'python.exe "C:\other\backend\.venv\Scripts\uvicorn.exe" --host 127.0.0.1 --port 8001'
        }
        Test-TimeboxProcessIdentity -Purpose api -Process $process -RepositoryRoot "C:\repo" | Should Be $false
    }

    It "accepts the registered Vite process identity" {
        $process = [pscustomobject]@{
            Name = "node.exe"
            CommandLine = 'node "C:\repo\frontend\node_modules\vite\bin\vite.js" --host=127.0.0.1 --port=5176'
        }
        Test-TimeboxProcessIdentity -Purpose frontend -Process $process -RepositoryRoot "C:\repo" | Should Be $true
    }

    It "stops reload workers before their verified API parent" {
        $processes = @(
            [pscustomobject]@{ ProcessId = 10; ParentProcessId = 1 },
            [pscustomobject]@{ ProcessId = 11; ParentProcessId = 10 },
            [pscustomobject]@{ ProcessId = 12; ParentProcessId = 11 },
            [pscustomobject]@{ ProcessId = 20; ParentProcessId = 1 }
        )

        $tree = @(Get-TimeboxProcessTree -Processes $processes -RootProcessIds 10)

        ($tree -join ',') | Should Be '12,11,10'
    }

    It "requires exact identifier evidence across migrations" {
        $before = [pscustomobject]@{ TaskIdentity = "2|1|2|abc"; PlannedIdentity = "3|4|6|def"; MutableCounts = "1|2|3" }
        $after = [pscustomobject]@{ TaskIdentity = "2|1|2|abc"; PlannedIdentity = "3|4|6|def"; MutableCounts = "1|2|3" }
        Compare-TimeboxDatabaseEvidence -Before $before -After $after | Should Be $true
    }

    It "rejects changed mutable counts outside the cutover" {
        $before = [pscustomobject]@{ TaskIdentity = "2|1|2|abc"; PlannedIdentity = "3|4|6|def"; MutableCounts = "1|2|3" }
        $after = [pscustomobject]@{ TaskIdentity = "2|1|2|abc"; PlannedIdentity = "3|4|6|def"; MutableCounts = "2|2|3" }
        $threw = $false
        try {
            Compare-TimeboxDatabaseEvidence -Before $before -After $after | Out-Null
        } catch {
            $threw = $true
        }
        $threw | Should Be $true
    }
}
