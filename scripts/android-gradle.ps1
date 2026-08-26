$ErrorActionPreference = "Stop"

$gradleArguments = @($args)
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repositoryRoot "android"
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$minimumJavaVersion = 17

if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper not found at $gradleWrapper"
}

$candidateHomes = [System.Collections.Generic.List[string]]::new()

function Add-JavaCandidate {
    param([string]$JavaHome)

    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return
    }

    $normalizedHome = [System.IO.Path]::GetFullPath($JavaHome.Trim().Trim('"'))
    if (-not $candidateHomes.Contains($normalizedHome)) {
        $candidateHomes.Add($normalizedHome)
    }
}

function Get-JavaCandidate {
    param([string]$JavaHome)

    $javaExecutable = Join-Path $JavaHome "bin\java.exe"
    $javacExecutable = Join-Path $JavaHome "bin\javac.exe"
    if (-not (Test-Path -LiteralPath $javaExecutable) -or
        -not (Test-Path -LiteralPath $javacExecutable)) {
        return $null
    }

    $versionOutput = (& $javaExecutable -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or
        $versionOutput -notmatch 'version "(?<major>\d+)(?:\.(?<minor>\d+))?') {
        return $null
    }

    $majorVersion = [int]$Matches.major
    if ($majorVersion -eq 1 -and $Matches.minor) {
        $majorVersion = [int]$Matches.minor
    }

    return [pscustomobject]@{
        Home = $JavaHome
        MajorVersion = $majorVersion
    }
}

# Prefer an explicitly configured compatible JDK, then Android Studio's bundled
# runtime, then Java installations discoverable through PATH or common locations.
Add-JavaCandidate $env:JAVA_HOME

$programFiles = [Environment]::GetFolderPath("ProgramFiles")
$localAppData = [Environment]::GetFolderPath("LocalApplicationData")

Add-JavaCandidate (Join-Path $programFiles "Android\Android Studio\jbr")
Add-JavaCandidate (Join-Path $localAppData "Programs\Android Studio\jbr")

$androidInstallRoot = Join-Path $programFiles "Android"
if (Test-Path -LiteralPath $androidInstallRoot) {
    Get-ChildItem -LiteralPath $androidInstallRoot -Directory -Filter "Android Studio*" |
        ForEach-Object { Add-JavaCandidate (Join-Path $_.FullName "jbr") }
}

$pathJava = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if ($pathJava) {
    Add-JavaCandidate (Split-Path -Parent (Split-Path -Parent $pathJava.Source))
}

$jdkRoots = @(
    (Join-Path $programFiles "Java"),
    (Join-Path $programFiles "Microsoft"),
    (Join-Path $programFiles "Eclipse Adoptium"),
    (Join-Path $programFiles "Amazon Corretto"),
    (Join-Path $programFiles "Azul Systems")
)

foreach ($jdkRoot in $jdkRoots) {
    if (Test-Path -LiteralPath $jdkRoot) {
        Get-ChildItem -LiteralPath $jdkRoot -Directory |
            ForEach-Object { Add-JavaCandidate $_.FullName }
    }
}

$selectedJava = $null
foreach ($candidateHome in $candidateHomes) {
    $candidate = Get-JavaCandidate $candidateHome
    if ($candidate -and $candidate.MajorVersion -ge $minimumJavaVersion) {
        $selectedJava = $candidate
        break
    }
}

if (-not $selectedJava) {
    throw "Android Gradle requires JDK $minimumJavaVersion or newer. Set JAVA_HOME to a compatible JDK or install Android Studio with its bundled JBR."
}

$hadJavaHome = Test-Path Env:JAVA_HOME
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
$locationPushed = $false

try {
    $env:JAVA_HOME = $selectedJava.Home
    $env:Path = "$(Join-Path $selectedJava.Home 'bin');$previousPath"
    Write-Output "Using Java $($selectedJava.MajorVersion) from $($selectedJava.Home)"

    Push-Location $androidRoot
    $locationPushed = $true
    & $gradleWrapper @gradleArguments
    $gradleExitCode = $LASTEXITCODE
} finally {
    if ($locationPushed) {
        Pop-Location
    }

    if ($hadJavaHome) {
        $env:JAVA_HOME = $previousJavaHome
    } else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:Path = $previousPath
}

exit $gradleExitCode
