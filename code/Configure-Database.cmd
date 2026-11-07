@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0scripts\start-supermarket.ps1" -ConfigureDatabase
if errorlevel 1 pause
endlocal
