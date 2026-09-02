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

function Find-RegistrationToken {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    $value = $Text.Trim()
    if ($value -match '--token\s+([A-Za-z0-9._-]{20,})') {
        return $Matches[1]
    }
    if ($value -match '^[A-Za-z0-9._-]{20,}$') {
        return $value
    }
    return $null
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

    $runnerDir = 'C:\actions-runner'
    $runnerVersion = '2.337.0'
    $runnerZip = Join-Path $runnerDir "actions-runner-win-x64-$runnerVersion.zip"
    $runnerUrl = "https://github.com/actions/runner/releases/download/v$runnerVersion/actions-runner-win-x64-$runnerVersion.zip"
    $runnerSha256 = '1150692AFA94E71F872017E254EA55B6EECE1EECE3FE7E3A6D4C93D0A1B85CFC'
    $repoUrl = 'https://github.com/cmlovehtc/taxi'
    $newRunnerUrl = 'https://github.com/cmlovehtc/taxi/settings/actions/runners/new?arch=x64&os=win'
    $runnersUrl = 'https://github.com/cmlovehtc/taxi/settings/actions/runners'

    Clear-Host
    Write-Host '============================================================'
    Write-Host ' TAXI QUESTION BANK AUTO UPDATE - INSTALLER V2'
    Write-Host '============================================================'
    Write-Host ''

    $configuredFile = Join-Path $runnerDir '.runner'
    if (Test-Path -LiteralPath $configuredFile) {
        $existingService = Get-Service -Name 'actions.runner.*' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $existingService -and $existingService.Status -ne 'Running') {
            Start-Service -Name $existingService.Name
        }
        Start-Process $runnersUrl
        Wait-ForUser 'Runner is already installed. GitHub should show Idle.'
        exit 0
    }

    New-Item -ItemType Directory -Path $runnerDir -Force | Out-Null

    Write-Host '[1/4] Downloading the official GitHub Runner (about 100 MB)...'
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $runnerUrl -OutFile $runnerZip

    Write-Host '[2/4] Verifying the official SHA-256 checksum...'
    $downloadedHash = (Get-FileHash -LiteralPath $runnerZip -Algorithm SHA256).Hash
    if ($downloadedHash -ne $runnerSha256) {
        throw 'The downloaded GitHub Runner failed its security checksum.'
    }

    Write-Host '[3/4] Extracting files...'
    Expand-Archive -LiteralPath $runnerZip -DestinationPath $runnerDir -Force
    $configCommand = Join-Path $runnerDir 'config.cmd'
    if (-not (Test-Path -LiteralPath $configCommand)) {
        throw 'GitHub Runner extraction failed: config.cmd was not found.'
    }

    Write-Host '[4/4] A GitHub page will open.'
    Write-Host 'On that page, find Configure and copy the FULL config.cmd line.'
    Write-Host 'Then return to this window.'
    Start-Process $newRunnerUrl
    [void](Read-Host 'After copying the line, press ENTER here')

    $clipboardText = Get-Clipboard -Raw -ErrorAction SilentlyContinue
    $token = Find-RegistrationToken -Text $clipboardText
    if ([string]::IsNullOrWhiteSpace($token)) {
        $manualText = Read-Host 'Paste the full Configure line here, then press ENTER'
        $token = Find-RegistrationToken -Text $manualText
    }
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'No valid GitHub registration token was found.'
    }

    Write-Host ''
    Write-Host 'Connecting this Windows computer to your GitHub project...'
    Push-Location $runnerDir
    try {
        & $configCommand --unattended --url $repoUrl --token $token `
            --name "taxi-$env:COMPUTERNAME" --work '_work' --runasservice --replace
        $configExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $token = $null
        Set-Clipboard -Value '[GitHub token cleared by TAXI installer]' -ErrorAction SilentlyContinue
    }

    if ($configExitCode -ne 0) {
        throw "GitHub rejected the setup command. Exit code: $configExitCode. The token may have expired."
    }

    $runnerService = Get-Service -Name 'actions.runner.*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $runnerService) {
        throw 'The Runner connected, but its Windows service was not found.'
    }
    if ($runnerService.Status -ne 'Running') {
        Start-Service -Name $runnerService.Name
        $runnerService.WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
    }

    Start-Process $runnersUrl
    Write-Host ''
    Write-Host '============================================================'
    Write-Host ' INSTALL COMPLETE'
    Write-Host '============================================================'
    Write-Host 'GitHub should show this computer as Idle.'
    Write-Host 'The updater will run every day at 04:20 Taiwan time.'
    Write-Host 'The computer must be on, online, and not sleeping.'
    Wait-ForUser 'Installation finished successfully.'
    exit 0
}
catch {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host ' INSTALL FAILED' -ForegroundColor Red
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Wait-ForUser 'Take a screenshot of this window and send it to ChatGPT.'
    exit 1
}
