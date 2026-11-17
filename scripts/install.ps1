# GLOOM Windows CLI Installer
# Automated one-line installer for GLOOM Dynamic Island & Fluid Dock on Windows 11/10.

$ErrorActionPreference = 'Stop'

# Ensure TLS 1.2 is active (PowerShell 5.1 compatibility)
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
} catch {}

$Interactive = $false
$NoLaunch = $false
if ($args -contains "-Interactive") { $Interactive = $true }
if ($args -contains "-NoLaunch") { $NoLaunch = $true }

Clear-Host -ErrorAction SilentlyContinue

Write-Host @"
 ╭──────────────────────────────────────────────────────────────╮
 │   ____ _      ___   ___  __  __                              │
 │  / ___| |    / _ \ / _ \|  \/  |   DYNAMIC ISLAND FOR WINDOWS│
 │ | |  _| |   | | | | | | | |\/| |                             │
 │ | |_| | |___| |_| | |_| | |  | |   Crafted with Apple HIG    │
 │  \____|_____|\___/ \___/|_|  |_|   Fluid Motion & OLED Glass │
 ╰──────────────────────────────────────────────────────────────╯
"@ -ForegroundColor Cyan

function Write-Step([string]$num, [string]$title) {
    Write-Host " [$num/4] " -ForegroundColor Blue -NoNewline
    Write-Host $title -ForegroundColor White
}

function Write-SubInfo([string]$text) {
    Write-Host "       ↳ $text" -ForegroundColor DarkGray
}

function Write-Success([string]$text) {
    Write-Host "       ✔ $text" -ForegroundColor Green
}

function Write-Fail([string]$text) {
    Write-Host "`n ✖ Error: $text`n" -ForegroundColor Red
}

# 1. Architecture Check
Write-Step "1" "Verifying 64-bit Windows environment"
if (-not [Environment]::Is64BitOperatingSystem) {
    Write-Fail "GLOOM requires a 64-bit Windows 11 or Windows 10 OS."
    exit 1
}
Write-Success "64-bit Windows architecture confirmed"

# 2. Query GitHub for latest release asset
Write-Step "2" "Querying latest verified release from GitHub"
$repo = "NandiVardhan2007/GLOOM"
$downloadUrl = "https://github.com/$repo/releases/latest/download/GLOOM-Island-Setup.exe"

$version = "v2.3.2"
try {
    $releaseInfo = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/latest" -UseBasicParsing -ErrorAction Stop
    if ($releaseInfo.tag_name) {
        $version = $releaseInfo.tag_name
    }
} catch {
    # Fallback to direct latest release asset
}
Write-Success "Target release: $version"
Write-SubInfo "Download source: $downloadUrl"

# 3. Download the setup wizard
Write-Step "3" "Downloading installer payload"
$tempExe = Join-Path $env:TEMP "GLOOM-Island-Setup.exe"
if (Test-Path $tempExe) {
    Remove-Item $tempExe -Force -ErrorAction SilentlyContinue
}

try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $tempExe -UseBasicParsing
    $sizeMB = [math]::Round((Get-Item $tempExe).Length / 1MB, 2)
    Write-Success "Downloaded payload ($sizeMB MB) verified"
} catch {
    Write-Fail "Failed to download $downloadUrl. Please check your internet connection."
    exit 1
}

# 4. Install GLOOM
Write-Step "4" "Deploying GLOOM into system environment"
Write-SubInfo "Configuring Start Menu shortcuts and uninstaller registration..."

# Stop any running GLOOM instances so files are not locked during installation
Stop-Process -Name "gloom" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "GLOOM" -Force -ErrorAction SilentlyContinue

$arguments = if ($Interactive) { "" } else { "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /FORCECLOSEAPPLICATIONS" }
$process = Start-Process -FilePath $tempExe -ArgumentList $arguments -PassThru
$exited = $process.WaitForExit(90000)
if (-not $exited) {
    try { $process.Kill() } catch {}
}

$possiblePaths = @(
    (Join-Path $env:LOCALAPPDATA "Programs\GLOOM\gloom.exe"),
    (Join-Path $env:LOCALAPPDATA "Programs\GLOOM\GLOOM.exe"),
    (Join-Path $env:ProgramFiles "GLOOM\gloom.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "GLOOM\gloom.exe")
)
$installPath = $possiblePaths | Where-Object { Test-Path $_ } | Select-Object -First 1

if ($process.ExitCode -eq 0 -or $installPath) {
    Remove-Item $tempExe -Force -ErrorAction SilentlyContinue

    Write-Host @"

 ╭──────────────────────────────────────────────────────────────╮
 │  Installation Complete! GLOOM $version is ready.            │
 ├──────────────────────────────────────────────────────────────┤
 │  Install Directory:  $installPath
 │  System Integration: Start Menu & Desktop Shortcut Registered │
 ╰──────────────────────────────────────────────────────────────╯
"@ -ForegroundColor Cyan

    if (-not $NoLaunch -and $installPath) {
        Write-Host " 🚀 Launching GLOOM Dynamic Island..." -ForegroundColor Yellow
        Start-Process -FilePath $installPath
    }

    Write-Host @"
 💡 Quick Tips:
   • Click the Island capsule to expand, or press Esc to dismiss.
   • Manage auto-start and auto-update anytime in GLOOM Settings.
"@ -ForegroundColor DarkGray

} else {
    Write-Fail "Setup wizard exited with code $($process.ExitCode)"
    exit $process.ExitCode
}
