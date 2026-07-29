# Installs pgvector into an existing PostgreSQL 16 install on D:\PostgreSQL\16.
#
# Downloads unofficial pre-built Windows binaries from:
#   https://github.com/andreiramani/pgvector_pgsql_windows/releases
#
# Run as Administrator AFTER install-postgres-windows.ps1:
#   powershell -ExecutionPolicy Bypass -File scripts\install-pgvector-windows.ps1

#Requires -Version 5.1
$ErrorActionPreference = 'Stop'

$InstallRoot = 'D:\PostgreSQL\16'
$DownloadDir = 'D:\Downloads'
$ServiceName = 'postgresql-x64-16'
$PgMajor = '16'
$PgvectorVersion = '0.8.5'
$ReleaseTag = '0.8.5_16.14'
$ZipName = 'vector.v0.8.5-pg16.zip'
$ReleaseUrl = "https://github.com/andreiramani/pgvector_pgsql_windows/releases/download/$ReleaseTag/$ZipName"

function Test-Administrator {
    $current = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($current)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrator)) {
    Write-Host 'Run as Administrator.' -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path (Join-Path $InstallRoot 'bin\psql.exe'))) {
    Write-Host "PostgreSQL not found at $InstallRoot" -ForegroundColor Red
    Write-Host 'Run install-postgres-windows.ps1 first.'
    exit 1
}

if (-not (Test-Path $DownloadDir)) {
    New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null
}

$zipPath = Join-Path $DownloadDir $ZipName
Write-Host "`n==> Downloading pgvector $PgvectorVersion for PostgreSQL $PgMajor" -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri $ReleaseUrl -OutFile $zipPath -UseBasicParsing
} catch {
    Write-Host 'Automatic download failed. Install manually:' -ForegroundColor Yellow
    Write-Host '  1. Open https://github.com/andreiramani/pgvector_pgsql_windows/releases'
    Write-Host "  2. Download the PostgreSQL $PgMajor zip"
    Write-Host "  3. Extract DLL to $InstallRoot\lib\"
    Write-Host "  4. Extract extension files to $InstallRoot\share\extension\"
    exit 1
}

$extractDir = Join-Path $DownloadDir 'pgvector-extract'
if (Test-Path $extractDir) {
    Remove-Item -Recurse -Force $extractDir
}
Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force

Write-Host '==> Copying pgvector files into PostgreSQL install' -ForegroundColor Cyan
Get-ChildItem -Path $extractDir -Recurse -File | ForEach-Object {
    if ($_.Extension -eq '.dll') {
        $target = Join-Path $InstallRoot ('lib\' + $_.Name)
        Copy-Item -Path $_.FullName -Destination $target -Force
        Write-Host "  copied lib\$($_.Name)"
        return
    }
    if ($_.Extension -in '.control', '.sql') {
        $target = Join-Path $InstallRoot ('share\extension\' + $_.Name)
        Copy-Item -Path $_.FullName -Destination $target -Force
        Write-Host "  copied share\extension\$($_.Name)"
    }
}

Write-Host '==> Restarting PostgreSQL service' -ForegroundColor Cyan
Restart-Service -Name $ServiceName
Start-Sleep -Seconds 3

$env:PGPASSWORD = 'TravelPlannerPgAdmin123!'
& (Join-Path $InstallRoot 'bin\psql.exe') -U postgres -h localhost -d travel_planner -c `
    'CREATE EXTENSION IF NOT EXISTS vector;'
if ($LASTEXITCODE -ne 0) {
    Write-Host 'CREATE EXTENSION failed — copy files manually per the release readme.' -ForegroundColor Red
    exit 1
}

Write-Host 'pgvector installed and enabled.' -ForegroundColor Green
