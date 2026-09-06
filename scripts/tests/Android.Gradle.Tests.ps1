Describe "Android Gradle launcher" {
    It "discovers a JDK from its release file under Windows PowerShell 5.1" {
        $repositoryRoot = Join-Path $TestDrive "repo"
        $scriptsRoot = Join-Path $repositoryRoot "scripts"
        $androidRoot = Join-Path $repositoryRoot "android"
        $javaHome = Join-Path $TestDrive "jdk-21"

        New-Item -ItemType Directory -Path $scriptsRoot, $androidRoot, (Join-Path $javaHome "bin") | Out-Null
        Copy-Item (Join-Path (Split-Path -Parent $PSScriptRoot) "android-gradle.ps1") $scriptsRoot
        Copy-Item "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" (Join-Path $javaHome "bin\java.exe")
        Copy-Item "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" (Join-Path $javaHome "bin\javac.exe")
        Set-Content -LiteralPath (Join-Path $javaHome "release") -Value 'JAVA_VERSION="21.0.8"'
        Set-Content -LiteralPath (Join-Path $androidRoot "gradlew.bat") -Value '@exit /b 0'

        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
        $startInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $scriptsRoot 'android-gradle.ps1')`" --version"
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.EnvironmentVariables["JAVA_HOME"] = $javaHome

        $process = [System.Diagnostics.Process]::Start($startInfo)
        $standardOutput = $process.StandardOutput.ReadToEnd()
        $standardError = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        $process.ExitCode | Should Be 0
        $standardOutput | Should Match ([regex]::Escape("Using Java 21 from $javaHome"))
        $standardError | Should BeNullOrEmpty
    }
}
