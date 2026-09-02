@echo off
setlocal EnableExtensions DisableDelayedExpansion
title TAXI RUNNER PYTHON REPAIR

set "TAXI_SCRIPT_URL=https://raw.githubusercontent.com/cmlovehtc/taxi/main/tools/install_python_for_runner.ps1"
set "TAXI_SCRIPT_PATH=%TEMP%\taxi_install_python_for_runner.ps1"
set "TAXI_SCRIPT_SHA256=718F8B280C59A7483BD79BDE96B1772D89D412D9DD94BC00AD66A4C453C0BA0D"

echo ============================================================
echo  TAXI RUNNER PYTHON REPAIR
echo ============================================================
echo.
echo Downloading the repair tool from your GitHub project...

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri $env:TAXI_SCRIPT_URL -OutFile $env:TAXI_SCRIPT_PATH; $hash=(Get-FileHash -LiteralPath $env:TAXI_SCRIPT_PATH -Algorithm SHA256).Hash; if($hash -ne $env:TAXI_SCRIPT_SHA256){throw 'Downloaded repair tool failed its SHA-256 security check.'}"
if errorlevel 1 goto download_failed

echo Starting Python repair...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%TAXI_SCRIPT_PATH%"
if errorlevel 1 goto repair_failed
exit /b 0

:download_failed
echo.
echo DOWNLOAD FAILED.
echo Check your internet connection and take a screenshot of this window.
pause
exit /b 1

:repair_failed
echo.
echo PYTHON REPAIR STOPPED.
echo Take a screenshot of the error and send it to ChatGPT.
pause
exit /b 1
