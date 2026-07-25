<#
.SYNOPSIS
    Travel Planner - prerequisite check (Windows PowerShell).

.DESCRIPTION
    Mirrors scripts/check-prerequisites.sh. Keep both in sync: same tools, same
    thresholds, same output shape, same exit codes.

    Required versions are defined by plans/superpower/PLAN.md 4.0.0 (LOCKED).
    Exit codes: 0 = every required tool present and correct, 1 = at least one failure.

    Set $env:PREREQ_SOURCE_ONLY = '1' before dot-sourcing to load the parsing
    helpers without running the check (used by scripts/tests/check-prerequisites.tests.ps1).

.NOTES
    Targets Windows PowerShell 5.1 - no ternary, null-coalescing, or && operators.
#>

Set-StrictMode -Version 2.0

# --------------------------------------------------------------------------- #
# Required versions (single source of truth for this script)
# --------------------------------------------------------------------------- #
$script:ReqNodeMajor    = 22   # exact
$script:ReqNpmMajor     = 10   # minimum
$script:ReqJavaMajor    = 21   # exact
$script:ReqDockerMajor  = 24   # minimum
$script:ReqComposeMajor = 2    # minimum
$script:ReqGitMajor     = 2    # minimum
$script:ReqGradleMajor  = 8    # exact, only when the wrapper is present

# --------------------------------------------------------------------------- #
# Version parsing helpers - pure functions, covered by the test script
# --------------------------------------------------------------------------- #

<# Extract the first dotted numeric version from arbitrary text.
   "Docker version 28.5.2, build ecc6942" -> "28.5.2"
   "git version 2.40.1.windows.1"         -> "2.40.1" #>
function Get-ExtractedVersion {
    param([string] $Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    $m = [regex]::Match($Text, '[0-9]+(\.[0-9]+)*')
    if (-not $m.Success) { return $null }
    return $m.Value
}

# Major component of a dotted version. "22.23.1" -> 22
function Get-VersionMajor {
    param([string] $Text)
    $v = Get-ExtractedVersion -Text $Text
    if ($null -eq $v) { return $null }
    return [int]($v -split '\.')[0]
}

# Java uses a legacy scheme below 9: "1.8.0_51" is Java 8, "21.0.11" is Java 21.
function Get-JavaMajor {
    param([string] $Text)
    $v = Get-ExtractedVersion -Text $Text
    if ($null -eq $v) { return $null }
    $parts = $v -split '\.'
    if ($parts[0] -eq '1' -and $parts.Count -ge 2) { return [int]$parts[1] }
    return [int]$parts[0]
}

<# Pull the version line out of `java -version` output, which goes to stderr and may
   be preceded by noise such as "Picked up JAVA_TOOL_OPTIONS". #>
function Get-JavaVersionLine {
    param([string] $Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $null }
    foreach ($line in ($Text -split "`r?`n")) {
        if ($line -match '(^| )version ') { return $line }
    }
    return $null
}

<# Run a native command and return combined stdout+stderr as a plain string.
   Native stderr surfaces as ErrorRecord objects in PowerShell 5.1; ToString()
   flattens them so version text is not lost. #>
function Invoke-VersionProbe {
    param([string] $Command, [string[]] $Arguments)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) { return $null }
    try {
        $out = & $Command @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        return ($out -join "`n")
    } catch {
        return $null
    }
}

# --------------------------------------------------------------------------- #
# Reporting
# --------------------------------------------------------------------------- #
$script:PassCount = 0
$script:FailCount = 0
$script:SkipCount = 0
$script:WarnCount = 0

function Write-Result {
    param(
        [ValidateSet('ok', 'fail', 'warn', 'skip')] [string] $Status,
        [string] $Tool,
        [string] $Detected,
        [string] $Requirement
    )
    switch ($Status) {
        'ok'   { $tag = '[ OK ]'; $colour = 'Green';    $script:PassCount++ }
        'fail' { $tag = '[FAIL]'; $colour = 'Red';      $script:FailCount++ }
        'warn' { $tag = '[WARN]'; $colour = 'Yellow';   $script:WarnCount++ }
        'skip' { $tag = '[SKIP]'; $colour = 'DarkGray'; $script:SkipCount++ }
    }
    Write-Host '  ' -NoNewline
    Write-Host $tag -ForegroundColor $colour -NoNewline
    Write-Host ('  {0,-18} {1,-24} ' -f $Tool, $Detected) -NoNewline
    Write-Host $Requirement -ForegroundColor DarkGray
}

function Write-Hint {
    param([string] $Message)
    Write-Host ('          -> {0}' -f $Message) -ForegroundColor DarkGray
}

# --------------------------------------------------------------------------- #
# Individual checks
# --------------------------------------------------------------------------- #

function Test-Tool {
    param(
        [string] $Label,
        [string] $Command,
        [string[]] $Arguments,
        [ValidateSet('exact', 'min')] [string] $Mode,
        [int] $Required,
        [string] $InstallHint
    )
    if ($Mode -eq 'exact') { $req = "required $Required.x" } else { $req = "required >=$Required" }

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        Write-Result -Status fail -Tool $Label -Detected 'not found' -Requirement $req
        Write-Hint $InstallHint
        return
    }

    $raw      = Invoke-VersionProbe -Command $Command -Arguments $Arguments
    $detected = Get-ExtractedVersion -Text $raw
    $major    = Get-VersionMajor -Text $raw

    if ($null -eq $major) {
        Write-Result -Status fail -Tool $Label -Detected 'unparseable' -Requirement $req
        Write-Hint ('Could not read a version from: {0}' -f (($raw -split "`n")[0]))
        return
    }
    if ($Mode -eq 'exact' -and $major -ne $Required) {
        Write-Result -Status fail -Tool $Label -Detected $detected -Requirement $req
        Write-Hint $InstallHint
        return
    }
    if ($Mode -eq 'min' -and $major -lt $Required) {
        Write-Result -Status fail -Tool $Label -Detected $detected -Requirement $req
        Write-Hint $InstallHint
        return
    }
    Write-Result -Status ok -Tool $Label -Detected $detected -Requirement $req
}

function Test-Java {
    $req = "required $script:ReqJavaMajor"
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Result -Status fail -Tool 'Java JDK' -Detected 'not found' -Requirement $req
        Write-Hint ('Install Adoptium Temurin {0}: https://adoptium.net/' -f $script:ReqJavaMajor)
        return
    }
    $raw      = Invoke-VersionProbe -Command 'java' -Arguments @('-version')
    $line     = Get-JavaVersionLine -Text $raw
    $detected = Get-ExtractedVersion -Text $line
    $major    = Get-JavaMajor -Text $line

    if ($null -eq $major) {
        Write-Result -Status fail -Tool 'Java JDK' -Detected 'unparseable' -Requirement $req
        Write-Hint ('Could not read a version from: {0}' -f (($raw -split "`n")[0]))
    } elseif ($major -ne $script:ReqJavaMajor) {
        Write-Result -Status fail -Tool 'Java JDK' -Detected "$detected (Java $major)" -Requirement $req
        Write-Hint ('Install Adoptium Temurin {0}: https://adoptium.net/' -f $script:ReqJavaMajor)
    } else {
        Write-Result -Status ok -Tool 'Java JDK' -Detected $detected -Requirement $req
    }

    # Gradle resolves the JDK through JAVA_HOME, not PATH. A mismatch builds with the
    # wrong compiler while `java -version` looks correct, so surface it - non-fatal.
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $homeJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $homeJava) {
            $homeRaw   = Invoke-VersionProbe -Command $homeJava -Arguments @('-version')
            $homeMajor = Get-JavaMajor -Text (Get-JavaVersionLine -Text $homeRaw)
            if ($null -ne $homeMajor -and $homeMajor -ne $script:ReqJavaMajor) {
                Write-Result -Status warn -Tool 'JAVA_HOME' -Detected "Java $homeMajor" `
                    -Requirement "expected $script:ReqJavaMajor"
                Write-Hint ('JAVA_HOME={0} resolves to Java {1}; Gradle will use it.' -f $env:JAVA_HOME, $homeMajor)
            }
        }
    }
}

function Test-Compose {
    $req = "required >=v$script:ReqComposeMajor"
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Result -Status fail -Tool 'Docker Compose' -Detected 'not found' -Requirement $req
        Write-Hint 'Included with Docker Desktop: https://www.docker.com/products/docker-desktop/'
        return
    }
    $raw      = Invoke-VersionProbe -Command 'docker' -Arguments @('compose', 'version')
    $detected = Get-ExtractedVersion -Text $raw
    $major    = Get-VersionMajor -Text $raw

    if ($null -eq $major) {
        if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
            Write-Result -Status fail -Tool 'Docker Compose' -Detected 'v1 (docker-compose)' -Requirement $req
            Write-Hint 'Compose v1 is end-of-life. Upgrade to Docker Desktop with the v2 plugin.'
        } else {
            Write-Result -Status fail -Tool 'Docker Compose' -Detected 'not found' -Requirement $req
            Write-Hint 'Included with Docker Desktop: https://www.docker.com/products/docker-desktop/'
        }
    } elseif ($major -lt $script:ReqComposeMajor) {
        Write-Result -Status fail -Tool 'Docker Compose' -Detected $detected -Requirement $req
        Write-Hint 'Upgrade Docker Desktop to get the Compose v2 plugin.'
    } else {
        Write-Result -Status ok -Tool 'Docker Compose' -Detected $detected -Requirement $req
    }
}

<# The wrapper does not exist until Task 02 scaffolds the backend, so absence is a
   skip rather than a failure. #>
function Test-GradleWrapper {
    param([string] $RepoRoot)
    $req     = "required $script:ReqGradleMajor.x"
    $wrapper = Join-Path $RepoRoot 'apps\backend\gradlew.bat'
    if (-not (Test-Path $wrapper)) {
        Write-Result -Status skip -Tool 'Gradle wrapper' -Detected 'not present' `
            -Requirement 'checked once apps/backend/gradlew.bat exists'
        return
    }
    $raw = Invoke-VersionProbe -Command $wrapper -Arguments @('--version')
    $gradleLine = $null
    foreach ($line in ($raw -split "`r?`n")) {
        if ($line -match '^Gradle ') { $gradleLine = $line; break }
    }
    $detected = Get-ExtractedVersion -Text $gradleLine
    $major    = Get-VersionMajor -Text $gradleLine

    if ($null -eq $major) {
        Write-Result -Status fail -Tool 'Gradle wrapper' -Detected 'unparseable' -Requirement $req
        Write-Hint 'Run: apps\backend\gradlew.bat --version'
    } elseif ($major -ne $script:ReqGradleMajor) {
        Write-Result -Status fail -Tool 'Gradle wrapper' -Detected $detected -Requirement $req
        Write-Hint ('Update the wrapper: apps\backend\gradlew.bat wrapper --gradle-version {0}' -f $script:ReqGradleMajor)
    } else {
        Write-Result -Status ok -Tool 'Gradle wrapper' -Detected $detected -Requirement $req
    }
}

# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #
function Invoke-PrerequisiteCheck {
    $repoRoot = Split-Path -Parent $PSScriptRoot

    Write-Host ''
    Write-Host 'Travel Planner - prerequisite check'
    Write-Host ('Repo:     {0}' -f $repoRoot) -ForegroundColor DarkGray
    Write-Host ('Platform: {0} (PowerShell {1})' -f [System.Environment]::OSVersion.Platform, $PSVersionTable.PSVersion) -ForegroundColor DarkGray
    Write-Host ''

    Test-Tool -Label 'Node.js' -Command 'node' -Arguments @('-v') -Mode exact -Required $script:ReqNodeMajor `
        -InstallHint ('Install Node {0}: nvm install {0} (or https://nodejs.org/)' -f $script:ReqNodeMajor)
    Test-Tool -Label 'npm' -Command 'npm' -Arguments @('-v') -Mode min -Required $script:ReqNpmMajor `
        -InstallHint ('npm ships with Node {0}; updating Node updates npm' -f $script:ReqNodeMajor)
    Test-Java
    Test-Tool -Label 'Docker' -Command 'docker' -Arguments @('--version') -Mode min -Required $script:ReqDockerMajor `
        -InstallHint 'Install Docker Desktop: https://www.docker.com/products/docker-desktop/'
    Test-Compose
    Test-Tool -Label 'Git' -Command 'git' -Arguments @('--version') -Mode min -Required $script:ReqGitMajor `
        -InstallHint 'Install Git: https://git-scm.com/'
    Test-GradleWrapper -RepoRoot $repoRoot

    Write-Host ''
    $summary = '{0} passed, {1} failed, {2} skipped' -f $script:PassCount, $script:FailCount, $script:SkipCount
    if ($script:WarnCount -gt 0) { $summary += ', {0} warning(s)' -f $script:WarnCount }
    Write-Host $summary

    if ($script:FailCount -gt 0) {
        Write-Host 'FAILED' -ForegroundColor Red -NoNewline
        Write-Host ' - install the tools listed above, then re-run: npm run prereq'
        Write-Host ''
        return 1
    }
    Write-Host 'OK' -ForegroundColor Green -NoNewline
    Write-Host ' - prerequisites satisfied.'
    Write-Host ''
    return 0
}

if ($env:PREREQ_SOURCE_ONLY -ne '1') {
    exit (Invoke-PrerequisiteCheck)
}
