<#
.SYNOPSIS
    Unit tests for scripts/check-prerequisites.ps1

.DESCRIPTION
    Mirrors scripts/tests/test-check-prerequisites.sh assertion for assertion, so the
    two platform scripts are held to identical parsing behaviour.

    Covers the two things that actually break in a prerequisite gate:
      1. version parsing across every real-world format the tools emit
      2. missing-command handling (must fail loudly, never pass silently)

    No Pester dependency - runs on a stock Windows PowerShell 5.1.
    Run directly, or via `npm run prereq:test`.
#>

$ErrorActionPreference = 'Stop'

$targetScript = Join-Path (Split-Path -Parent $PSScriptRoot) 'check-prerequisites.ps1'

# Load the helpers without running the check.
$env:PREREQ_SOURCE_ONLY = '1'
. $targetScript
$env:PREREQ_SOURCE_ONLY = $null

$script:TestsRun    = 0
$script:TestsFailed = 0

function Assert-Equal {
    param([string] $Description, $Expected, $Actual)
    $script:TestsRun++
    if ($Expected -eq $Actual) {
        Write-Host ('  ok    {0}' -f $Description)
    } else {
        $script:TestsFailed++
        Write-Host ('  FAIL  {0}' -f $Description) -ForegroundColor Red
        Write-Host ('        expected: [{0}]' -f $Expected) -ForegroundColor Red
        Write-Host ('        actual:   [{0}]' -f $Actual) -ForegroundColor Red
    }
}

Write-Host ''
Write-Host 'check-prerequisites.ps1 - version parsing'
Write-Host ''

# --- node / npm ------------------------------------------------------------- #
Assert-Equal 'node -v            "v22.23.1"'                 22 (Get-VersionMajor 'v22.23.1')
Assert-Equal 'node -v            "v18.19.1"'                 18 (Get-VersionMajor 'v18.19.1')
Assert-Equal 'npm -v             "10.2.4"'                   10 (Get-VersionMajor '10.2.4')
Assert-Equal 'npm -v             "9.8.1"'                    9  (Get-VersionMajor '9.8.1')

# --- java: legacy 1.x scheme vs modern -------------------------------------- #
Assert-Equal 'java modern        "21.0.11"'                  21 (Get-JavaMajor 'openjdk version "21.0.11" 2026-04-21 LTS')
Assert-Equal 'java legacy        "1.8.0_51" is Java 8'       8  (Get-JavaMajor 'java version "1.8.0_51"')
Assert-Equal 'java legacy        "1.7.0_80" is Java 7'       7  (Get-JavaMajor 'java version "1.7.0_80"')
Assert-Equal 'java modern        "17.0.19"'                  17 (Get-JavaMajor 'openjdk version "17.0.19" 2025-10-21')
Assert-Equal 'java two-digit     "25.0.3"'                   25 (Get-JavaMajor 'openjdk version "25.0.3" 2026-01-20')

# JAVA_TOOL_OPTIONS noise precedes the real version line on many CI images.
$noisyJava = @"
Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8
openjdk version "21.0.11" 2026-04-21 LTS
OpenJDK Runtime Environment Temurin-21.0.11+10 (build 21.0.11+10-LTS)
"@
Assert-Equal 'java with noise    picks the version line'     21 (Get-JavaMajor (Get-JavaVersionLine $noisyJava))

# --- docker / compose / git / gradle ---------------------------------------- #
Assert-Equal 'docker            "Docker version 28.5.2"'     28 (Get-VersionMajor 'Docker version 28.5.2, build ecc6942')
Assert-Equal 'docker old        "Docker version 20.10.7"'    20 (Get-VersionMajor 'Docker version 20.10.7, build f0df350')
Assert-Equal 'compose v2        "version v2.40.3-desktop.1"' 2  (Get-VersionMajor 'Docker Compose version v2.40.3-desktop.1')
Assert-Equal 'compose v1        "docker-compose version 1.29.2"' 1 (Get-VersionMajor 'docker-compose version 1.29.2, build 5becea4c')
Assert-Equal 'git windows       "2.40.1.windows.1"'          2  (Get-VersionMajor 'git version 2.40.1.windows.1')
Assert-Equal 'git linux         "2.43.0"'                    2  (Get-VersionMajor 'git version 2.43.0')
Assert-Equal 'gradle            "Gradle 8.7"'                8  (Get-VersionMajor 'Gradle 8.7')
Assert-Equal 'gradle two-digit  "Gradle 10.0"'               10 (Get-VersionMajor 'Gradle 10.0')

# --- degenerate input -------------------------------------------------------- #
Assert-Equal 'empty string      yields nothing'              $null (Get-VersionMajor '')
Assert-Equal 'no digits         yields nothing'              $null (Get-VersionMajor 'command not found')
Assert-Equal 'empty java        yields nothing'              $null (Get-JavaMajor '')
Assert-Equal 'no version line   yields nothing'              $null (Get-JavaVersionLine 'java : command not found')
Assert-Equal 'extract full      keeps all components'        '2.40.1' (Get-ExtractedVersion 'git version 2.40.1.windows.1')

# --- missing commands -------------------------------------------------------- #
Write-Host ''
Write-Host 'check-prerequisites.ps1 - missing commands'
Write-Host ''

# An empty PATH makes every tool unresolvable. The gate must fail, not pass.
# Run in a child process so the current session's PATH is never mutated.
$probe = @"
`$env:PATH = ''
& '$targetScript'
"@
$emptyPathOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -Command $probe 2>&1 |
    ForEach-Object { $_.ToString() }
$emptyPathExit = $LASTEXITCODE
$joined = $emptyPathOutput -join "`n"

Assert-Equal 'empty PATH        exits non-zero'        1     $emptyPathExit
Assert-Equal 'empty PATH        reports "not found"'   $true ($joined -match 'not found')
Assert-Equal 'empty PATH        prints FAILED summary' $true ($joined -match 'FAILED')
Assert-Equal 'empty PATH        prints an install hint' $true ($joined -match 'adoptium\.net')

# --- summary ----------------------------------------------------------------- #
Write-Host ''
Write-Host ('{0} assertions, {1} failed' -f $script:TestsRun, $script:TestsFailed)
Write-Host ''

if ($script:TestsFailed -gt 0) { exit 1 }
exit 0
