# Travel Planner — PostgreSQL 16 native install on Windows (D: drive).
#
# Run as Administrator:
#   powershell -ExecutionPolicy Bypass -File scripts\install-postgres-windows.ps1
#
# What this does:
#   1. Downloads PostgreSQL 16 EDB installer (if missing)
#   2. Silent-installs to D:\PostgreSQL\16
#   3. Creates travel_planner database + user
#   4. Enables pgvector when the extension files are present
#
# pgvector is NOT bundled with the EDB installer. After Postgres installs, either:
#   - run scripts\install-pgvector-windows.ps1
#   - or keep using Docker for the DB: npm run dev:db

#Requires -Version 5.1
$ErrorActionPreference = 'Stop'

$PgVersion = '16'
$InstallRoot = 'D:\PostgreSQL\16'
$DataDir = 'D:\PostgreSQL\16\data'
$DownloadDir = 'D:\Downloads'
$InstallerName = 'postgresql-16.10-1-windows-x64.exe'
$InstallerUrl = 'https://get.enterprisedb.com/postgresql/postgresql-16.10-1-windows-x64.exe'
$ServiceName = 'postgresql-x64-16'
$DbName = 'travel_planner'
$DbUser = 'travel_planner'
$DbPassword = 'change_me'
$SuperPassword = 'TravelPlannerPgAdmin123!'
$Port = 5432

function Test-Administrator {
    $current = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($current)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Ensure-Directory([string]$Path) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Get-PsqlPath {
    return Join-Path $InstallRoot 'bin\psql.exe'
}

function Invoke-Psql([string[]]$Args, [string]$Password) {
    $env:PGPASSWORD = $Password
    & (Get-PsqlPath) @Args
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed: psql $($Args -join ' ')"
    }
}

if (-not (Test-Administrator)) {
    Write-Host 'Re-run this script in an Administrator PowerShell or CMD:' -ForegroundColor Yellow
    Write-Host '  powershell -ExecutionPolicy Bypass -File scripts\install-postgres-windows.ps1'
    exit 1
}

Write-Step 'Creating folders on D:'
Ensure-Directory $InstallRoot
Ensure-Directory $DownloadDir

$installerPath = Join-Path $DownloadDir $InstallerName
if (-not (Test-Path $installerPath)) {
    Write-Step "Downloading PostgreSQL $PgVersion installer"
    Invoke-WebRequest -Uri $InstallerUrl -OutFile $installerPath -UseBasicParsing
} else {
    Write-Step 'Installer already downloaded — skipping download'
}

$psqlPath = Get-PsqlPath
if (-not (Test-Path $psqlPath)) {
    Write-Step "Installing PostgreSQL $PgVersion to $InstallRoot"
    $installArgs = @(
        '--mode', 'unattended',
        '--unattendedmodeui', 'none',
        '--prefix', $InstallRoot,
        '--datadir', $DataDir,
        '--superpassword', $SuperPassword,
        '--serverport', "$Port",
        '--install_runtimes', '1'
    )
    $process = Start-Process -FilePath $installerPath -ArgumentList $installArgs -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "PostgreSQL installer exited with code $($process.ExitCode). Check %TEMP%\install-postgresql.log"
    }
} else {
    Write-Step 'PostgreSQL already installed — skipping installer'
}

Write-Step 'Starting PostgreSQL service'
$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($null -eq $service) {
    throw "Service $ServiceName not found. Installation may have failed."
}
if ($service.Status -ne 'Running') {
    Start-Service -Name $ServiceName
}
Start-Sleep -Seconds 3

Write-Step 'Creating database and user'
$env:PGPASSWORD = $SuperPassword
$roleExists = (& (Get-PsqlPath) -U postgres -h localhost -p $Port -d postgres -tAc `
    "SELECT 1 FROM pg_roles WHERE rolname = '$DbUser'")
if ([string]::IsNullOrWhiteSpace($roleExists)) {
    Invoke-Psql @('-U', 'postgres', '-h', 'localhost', '-p', "$Port", '-d', 'postgres', '-c',
        "CREATE USER $DbUser WITH PASSWORD '$DbPassword';") $SuperPassword
} else {
    Write-Host "User $DbUser already exists — skipping"
}

$dbExists = (& (Get-PsqlPath) -U postgres -h localhost -p $Port -d postgres -tAc `
    "SELECT 1 FROM pg_database WHERE datname = '$DbName'")
if ([string]::IsNullOrWhiteSpace($dbExists)) {
    Invoke-Psql @('-U', 'postgres', '-h', 'localhost', '-p', "$Port", '-d', 'postgres', '-c',
        "CREATE DATABASE $DbName OWNER $DbUser;") $SuperPassword
} else {
    Write-Host "Database $DbName already exists — skipping"
}

Invoke-Psql @('-U', 'postgres', '-h', 'localhost', '-p', "$Port", '-d', $DbName, '-c',
    "GRANT ALL ON SCHEMA public TO $DbUser;") $SuperPassword

Write-Step 'Checking pgvector extension'
$vectorAvailable = (& (Get-PsqlPath) -U postgres -h localhost -p $Port -d $DbName -tAc `
    "SELECT 1 FROM pg_available_extensions WHERE name = 'vector'")
if ([string]::IsNullOrWhiteSpace($vectorAvailable)) {
    Write-Host 'pgvector is NOT installed yet.' -ForegroundColor Yellow
    Write-Host 'Run next: powershell -ExecutionPolicy Bypass -File scripts\install-pgvector-windows.ps1'
    Write-Host 'Or use Docker Postgres instead: npm run dev:db'
} else {
    Invoke-Psql @('-U', 'postgres', '-h', 'localhost', '-p', "$Port", '-d', $DbName, '-c',
        'CREATE EXTENSION IF NOT EXISTS vector;') $SuperPassword
    Write-Host 'pgvector enabled.' -ForegroundColor Green
}

Write-Step 'Done — add this to your .env file'
Write-Host @"

POSTGRES_DB=$DbName
POSTGRES_USER=$DbUser
POSTGRES_PASSWORD=$DbPassword
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$Port/$DbName
BACKEND_INTERNAL_URL=http://localhost:8080
JWT_SECRET=change_me_min_32_chars_random_string

"@ -ForegroundColor Gray

Write-Host 'PostgreSQL superuser (postgres) password:' $SuperPassword -ForegroundColor Yellow
Write-Host 'Start the app (no Docker DB): npm run dev' -ForegroundColor Green
