@echo off
setlocal EnableExtensions DisableDelayedExpansion
title TAXI AUTO UPDATE INSTALLER V2

set "TAXI_SCRIPT_URL=https://raw.githubusercontent.com/cmlovehtc/taxi/main/tools/install_windows_runner_v2.ps1"
set "TAXI_SCRIPT_PATH=%TEMP%\taxi_install_windows_runner_v2.ps1"
set "TAXI_SCRIPT_SHA256=6D177FDF564B000092DBBFB8A7252B52AA63A91FFD46E5ABFA582D183847B34D"

echo ============================================================
echo  TAXI AUTO UPDATE INSTALLER V2
echo ============================================================
echo.
echo Downloading the safe installer from your GitHub project...

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri $env:TAXI_SCRIPT_URL -OutFile $env:TAXI_SCRIPT_PATH; $hash=(Get-FileHash -LiteralPath $env:TAXI_SCRIPT_PATH -Algorithm SHA256).Hash; if($hash -ne $env:TAXI_SCRIPT_SHA256){throw 'Downloaded installer failed its SHA-256 security check.'}"
if errorlevel 1 goto download_failed

echo Starting the installer...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%TAXI_SCRIPT_PATH%"
if errorlevel 1 goto installer_failed
exit /b 0

:download_failed
echo.
echo DOWNLOAD FAILED.
echo Check your internet connection and take a screenshot of this window.
pause
exit /b 1

:installer_failed
echo.
echo INSTALLER STOPPED.
echo Take a screenshot of the error and send it to ChatGPT.
pause
exit /b 1
