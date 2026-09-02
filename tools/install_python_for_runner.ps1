param(
    [switch]$Elevated
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Wait-ForUser {
    param([string]$Message)
    Write-Host ''
    Write-Host $Message
    [void](Read-Host 'Press ENTER to close this window')
}

try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    $isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

    if (-not $isAdmin) {
        if ($Elevated) {
            throw 'Windows did not grant administrator permission.'
        }

        Write-Host 'Windows will ask for administrator permission. Click Yes.'
        $quotedScript = '"' + $PSCommandPath.Replace('"', '""') + '"'
        $arguments = "-NoProfile -ExecutionPolicy Bypass -File $quotedScript -Elevated"
        Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -Verb RunAs | Out-Null
        exit 0
    }

    $pythonVersion = '3.12.10'
    $pythonDir = 'C:\Python312'
    $pythonExe = Join-Path $pythonDir 'python.exe'
    $downloadDir = 'C:\actions-runner\downloads'
    $installer = Join-Path $downloadDir "python-$pythonVersion-amd64.exe"
    $downloadUrl = "https://www.python.org/ftp/python/$pythonVersion/python-$pythonVersion-amd64.exe"
    $expectedHash = '67B5635E80EA51072B87941312D00EC8927C4DB9BA18938F7AD2D27B328B95FB'

    Clear-Host
    Write-Host '============================================================'
    Write-Host ' TAXI RUNNER PYTHON REPAIR'
    Write-Host '============================================================'
    Write-Host ''

    $needsInstall = $true
    if (Test-Path -LiteralPath $pythonExe) {
        $installedVersion = & $pythonExe -c 'import sys; print(".".join(map(str, sys.version_info[:3])))'
        if ($LASTEXITCODE -eq 0 -and $installedVersion -eq $pythonVersion) {
            Write-Host "Python $pythonVersion is already installed."
            $needsInstall = $false
        }
    }

    if ($needsInstall) {
        New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
        Write-Host '[1/3] Downloading official Python from python.org...'
        $ProgressPreference = 'SilentlyContinue'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $installer

        Write-Host '[2/3] Verifying the official SHA-256 checksum...'
        $downloadedHash = (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash
        if ($downloadedHash -ne $expectedHash) {
            throw 'The Python download failed its security checksum.'
        }

        Write-Host '[3/3] Installing Python for the GitHub Runner...'
        $installArguments = @(
            '/quiet',
            'InstallAllUsers=1',
            'TargetDir=C:\Python312',
            'PrependPath=1',
            'Include_pip=1',
            'Include_test=0',
            'Include_launcher=0'
        )
        $process = Start-Process -FilePath $installer -ArgumentList $installArguments -Wait -PassThru
        if ($process.ExitCode -notin @(0, 3010)) {
            throw "Python installer failed. Exit code: $($process.ExitCode)"
        }
    }

    if (-not (Test-Path -LiteralPath $pythonExe)) {
        throw 'Python installation finished, but C:\Python312\python.exe was not found.'
    }

    & $pythonExe --version
    if ($LASTEXITCODE -ne 0) {
        throw 'Python was installed but did not start correctly.'
    }

    $runnerServices = @(Get-Service -Name 'actions.runner.*' -ErrorAction SilentlyContinue)
    foreach ($service in $runnerServices) {
        Restart-Service -Name $service.Name -Force
        $service.WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
    }

    Write-Host ''
    Write-Host '============================================================'
    Write-Host ' PYTHON REPAIR COMPLETE'
    Write-Host '============================================================'
    Wait-ForUser 'Return to GitHub and run the workflow again.'
    exit 0
}
catch {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host ' PYTHON REPAIR FAILED' -ForegroundColor Red
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Wait-ForUser 'Take a screenshot of this window and send it to ChatGPT.'
    exit 1
}
