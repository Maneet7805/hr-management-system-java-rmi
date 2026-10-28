@echo off
rem Opens the window on a computer that only runs the client. Double-click, or run from a terminal.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-client.ps1" %*
echo.
pause
