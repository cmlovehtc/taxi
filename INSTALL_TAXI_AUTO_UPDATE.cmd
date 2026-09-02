@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
title 計程車題庫自動更新 - 安裝程式

fltmc >nul 2>&1
if errorlevel 1 goto request_admin
goto admin_ok

:request_admin
if /I "%~1"=="--elevated" goto admin_failed
echo.
echo 即將顯示 Windows 權限視窗，請按「是」。
set "TAXI_INSTALLER_PATH=%~f0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$q=[char]34; $a='/d /c '+$q+$q+$env:TAXI_INSTALLER_PATH+$q+' --elevated'+$q; Start-Process -FilePath $env:ComSpec -ArgumentList $a -WorkingDirectory (Split-Path -Parent $env:TAXI_INSTALLER_PATH) -Verb RunAs"
if errorlevel 1 (
  echo 無法取得系統管理員權限，安裝已停止。
  pause
)
exit /b

:admin_failed
echo.
echo Windows 沒有提供系統管理員權限，安裝已停止。
echo 請保留這個畫面並截圖給我。
pause
exit /b 1

:admin_ok
set "RUNNER_DIR=C:\actions-runner"
set "RUNNER_VERSION=2.337.0"
set "RUNNER_ZIP=C:\actions-runner\actions-runner-win-x64-2.337.0.zip"
set "RUNNER_URL=https://github.com/actions/runner/releases/download/v2.337.0/actions-runner-win-x64-2.337.0.zip"
set "RUNNER_SHA256=1150692AFA94E71F872017E254EA55B6EECE1EECE3FE7E3A6D4C93D0A1B85CFC"
set "REPO_URL=https://github.com/cmlovehtc/taxi"
set "NEW_RUNNER_URL=https://github.com/cmlovehtc/taxi/settings/actions/runners/new?arch=x64^&os=win"
set "RUNNERS_URL=https://github.com/cmlovehtc/taxi/settings/actions/runners"

cls
echo ============================================================
echo   計程車題庫 Windows 全自動更新 - 安裝程式
echo ============================================================
echo.
echo 這個程式只會安裝到：%RUNNER_DIR%
echo.

if exist "%RUNNER_DIR%\.runner" goto already_installed

if not exist "%RUNNER_DIR%\" mkdir "%RUNNER_DIR%"
if errorlevel 1 goto create_folder_failed

echo [1/4] 正在下載 GitHub 官方執行程式，約 100 MB...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri '%RUNNER_URL%' -OutFile '%RUNNER_ZIP%'"
if errorlevel 1 goto download_failed

echo [2/4] 正在檢查下載檔案是否正確...
set "ACTUAL_HASH="
for /f "usebackq delims=" %%H in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "(Get-FileHash -LiteralPath '%RUNNER_ZIP%' -Algorithm SHA256).Hash.ToUpperInvariant()"`) do set "ACTUAL_HASH=%%H"
if /I "%ACTUAL_HASH%"=="%RUNNER_SHA256%" goto hash_ok
goto hash_failed

:hash_ok
echo [3/4] 正在解壓縮...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%RUNNER_ZIP%' -DestinationPath '%RUNNER_DIR%' -Force"
if errorlevel 1 goto extract_failed
if not exist "%RUNNER_DIR%\config.cmd" goto extract_failed

echo [4/4] 需要取得 GitHub 的一次性設定代碼。
echo.
echo 瀏覽器即將開啟 GitHub：
echo   1. 找到 Configure 區塊。
echo   2. 複製包含 config.cmd 和 --token 的完整那一行。
echo   3. 回到這個黑色視窗，按任意鍵。
echo.
start "" "%NEW_RUNNER_URL%"
pause >nul

set "TOKEN="
for /f "usebackq delims=" %%T in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$v=Get-Clipboard -Raw -ErrorAction SilentlyContinue; if($null -ne $v){$v=$v.Trim(); if($v -match '--token\s+([A-Za-z0-9._-]{20,})'){Write-Output $Matches[1]} elseif($v -match '^[A-Za-z0-9._-]{20,}$'){Write-Output $v}}"`) do set "TOKEN=%%T"
if defined TOKEN goto configure_runner

echo.
echo 沒有在剪貼簿找到代碼。
set "GITHUB_INPUT="
set /p "GITHUB_INPUT=請把 Configure 完整那一行貼在這裡，再按 Enter："
if not defined GITHUB_INPUT goto token_missing
for /f "usebackq delims=" %%T in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$v=$env:GITHUB_INPUT.Trim(); if($v -match '--token\s+([A-Za-z0-9._-]{20,})'){Write-Output $Matches[1]} elseif($v -match '^[A-Za-z0-9._-]{20,}$'){Write-Output $v}"`) do set "TOKEN=%%T"
set "GITHUB_INPUT="
if not defined TOKEN goto token_missing

:configure_runner
echo.
echo 正在連接你的 GitHub 專案，請稍候...
cd /d "%RUNNER_DIR%"
call config.cmd --unattended --url "%REPO_URL%" --token "%TOKEN%" --name "taxi-%COMPUTERNAME%" --work "_work" --runasservice --replace
set "CONFIG_EXIT=%ERRORLEVEL%"
set "TOKEN="
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Set-Clipboard -Value ''" >nul 2>&1
if not "%CONFIG_EXIT%"=="0" goto configure_failed

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$service=Get-Service -Name 'actions.runner.*' -ErrorAction SilentlyContinue | Select-Object -First 1; if($null -eq $service){exit 2}; if($service.Status -ne 'Running'){Start-Service -Name $service.Name; $service.WaitForStatus('Running',[TimeSpan]::FromSeconds(20))}; Write-Host ('GitHub Runner 服務：' + (Get-Service -Name $service.Name).Status)"
if errorlevel 1 goto service_failed

start "" "%RUNNERS_URL%"
echo.
echo ============================================================
echo   安裝完成！
echo ============================================================
echo.
echo 瀏覽器的 Runners 頁面如果顯示 Idle，代表已經成功。
echo 從現在起，電腦重新開機後也會自動啟動。
echo 每天台灣時間 04:20 會自動檢查官方題庫。
echo.
echo 注意：執行更新時，電腦必須開機、連上網路，而且不能正在休眠。
echo.
pause
exit /b 0

:already_installed
echo 已經找到安裝完成的 GitHub Runner，不會重複安裝。
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$service=Get-Service -Name 'actions.runner.*' -ErrorAction SilentlyContinue | Select-Object -First 1; if($null -ne $service -and $service.Status -ne 'Running'){Start-Service -Name $service.Name}"
start "" "%RUNNERS_URL%"
echo 瀏覽器已開啟 Runners 頁面；顯示 Idle 就是正常。
pause
exit /b 0

:create_folder_failed
echo.
echo 安裝失敗：無法建立 %RUNNER_DIR% 資料夾。
goto failed_end

:download_failed
echo.
echo 安裝失敗：無法下載 GitHub 官方執行程式。請確認網路後再點兩下重試。
goto failed_end

:hash_failed
echo.
echo 安裝已停止：下載檔案的安全檢查未通過，不會繼續執行。
goto failed_end

:extract_failed
echo.
echo 安裝失敗：無法解壓縮 GitHub Runner。
goto failed_end

:token_missing
echo.
echo 安裝已停止：沒有讀到有效的一次性設定代碼。
echo 請回 GitHub 重新取得代碼，再點兩下這個安裝檔。
goto failed_end

:configure_failed
echo.
echo 安裝失敗：GitHub 沒有接受設定代碼。代碼可能已經過期。
echo 請回 GitHub 重新整理取得新代碼，再點兩下這個安裝檔。
goto failed_end

:service_failed
echo.
echo Runner 已連接，但 Windows 背景服務沒有正常啟動。
echo 請保留這個畫面並回聊天室告訴我，我再幫你處理。
goto failed_end

:failed_end
echo.
pause
exit /b 1
