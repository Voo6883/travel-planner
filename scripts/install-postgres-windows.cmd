@echo off
REM Travel Planner — one-click PostgreSQL install on D: (Windows).
REM Right-click this file -> Run as administrator
REM Or from an elevated CMD:
REM   cd /d "D:\Hobby Project\Travel Planner"
REM   scripts\install-postgres-windows.cmd

setlocal
cd /d "%~dp0\.."

where powershell >nul 2>&1
if errorlevel 1 (
  echo PowerShell is required.
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-postgres-windows.ps1"
exit /b %ERRORLEVEL%
