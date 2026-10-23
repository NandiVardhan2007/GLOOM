<#
.SYNOPSIS
    GLOOM Windows One-Line Installer
.DESCRIPTION
    Downloads and installs the latest verified GLOOM Dynamic Island & Desktop Dock release.
.EXAMPLE
    irm https://raw.githubusercontent.com/NandiVardhan2007/GLOOM/main/scripts/install.ps1 | iex
#>

[CmdletBinding()]
param (
    [switch]$Interactive = $false,
    [switch]$NoLaunch = $false
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

Write-Host @"
  ==============================================================
   ____ _      ___   ___  __  __ 
  / ___| |    / _ \ / _ \|  \/  |
 | |  _| |   | | | | | | | |\/| |
 | |_| | |___| |_| | |_| | |  | |
  \____|_____|\___/ \___/|_|  |_|  Dynamic Island & Desktop Dock
  ==============================================================
"@ -ForegroundColor Cyan

Write-Host " [1/3] Querying latest release from GitHub..." -ForegroundColor Yellow
$repo = "NandiVardhan2007/GLOOM"
$downloadUrl = "https://github.com/$repo/releases/latest/download/GLOOM-Island-Setup.exe"

$tempExe = Join-Path $env:TEMP "GLOOM-Island-Setup.exe"
if (Test-Path $tempExe) {
    Remove-Item $tempExe -Force -ErrorAction SilentlyContinue
}

Write-Host " [2/3] Downloading latest GLOOM setup wizard..." -ForegroundColor Yellow
Invoke-WebRequest -Uri $downloadUrl -OutFile $tempExe -UseBasicParsing

Write-Host " [3/3] Installing GLOOM Ecosystem..." -ForegroundColor Green
$arguments = if ($Interactive) { "" } else { "/VERYSILENT /NORESTART /SP-" }

$process = Start-Process -FilePath $tempExe -ArgumentList $arguments -PassThru -Wait

if ($process.ExitCode -eq 0) {
    Write-Host "`n  ✓ GLOOM installed successfully!" -ForegroundColor Green
    
    $installPath = Join-Path $env:LOCALAPPDATA "Programs\GLOOM\GLOOM.exe"
    if (-not $NoLaunch -and (Test-Path $installPath)) {
        Write-Host "  🚀 Launching GLOOM Dynamic Island..." -ForegroundColor Cyan
        Start-Process -FilePath $installPath
    }
} else {
    Write-Host "`n  ✗ Setup exited with code $($process.ExitCode)" -ForegroundColor Red
}
