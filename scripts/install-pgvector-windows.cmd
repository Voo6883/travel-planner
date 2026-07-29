@echo off
REM Installs pgvector after PostgreSQL is on D:\PostgreSQL\16.
REM Run as administrator.

setlocal
cd /d "%~dp0\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-pgvector-windows.ps1"
exit /b %ERRORLEVEL%
