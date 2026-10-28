@echo off
rem One-time setup for a computer that only runs the client. Double-click, or run from a terminal.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-client.ps1" %*
echo.
pause
