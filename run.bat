@echo off
rem Starts MySQL, the server and the window. Double-click, or run from a terminal.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run.ps1" %*
echo.
pause
