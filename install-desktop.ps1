# ==============================================================================
# JARVIS Dynamic Island — Windows Desktop Clean Setup Installer
# Single-line installation command:
#   powershell -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/NandiVardhan2007/JARVIS/main/install-desktop.ps1 | iex"
# ==============================================================================

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "  +----------------------------------------------------------+" -ForegroundColor Cyan
Write-Host "  |             JARVIS DYNAMIC ISLAND FOR WINDOWS            |" -ForegroundColor Cyan
Write-Host "  |             Apple-Grade Live Activities & Notch          |" -ForegroundColor Cyan
Write-Host "  +----------------------------------------------------------+" -ForegroundColor Cyan
Write-Host ""

$Repo = "NandiVardhan2007/JARVIS"
$InstallDir = "$env:LOCALAPPDATA\JARVIS-Island"
$ExeTarget = "$InstallDir\JARVIS-Island.exe"
$StartMenuDir = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs"
$StartMenuShortcut = "$StartMenuDir\JARVIS Dynamic Island.lnk"
$DesktopDir = [Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop)
$DesktopShortcut = "$DesktopDir\JARVIS Dynamic Island.lnk"

# 1. Stop any currently running instances
Write-Host "[1/5] Checking for running instances..." -ForegroundColor Yellow
$Running = Get-Process -Name "jarvis", "JARVIS-Island" -ErrorAction SilentlyContinue
if ($Running) {
    Write-Host "      Gracefully stopping existing JARVIS process..." -ForegroundColor Gray
    $Running | Stop-Process -Force
    Start-Sleep -Milliseconds 800
}
Write-Host "      Processes cleared." -ForegroundColor Green

# 2. Prepare installation directory
Write-Host "[2/5] Preparing installation directory..." -ForegroundColor Yellow
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}
Write-Host "      Target path: $InstallDir" -ForegroundColor Green

# 3. Fetch latest release information
Write-Host "[3/5] Querying GitHub Releases for latest version..." -ForegroundColor Yellow
$ApiUrl = "https://api.github.com/repos/$Repo/releases/latest"
$DownloadUrl = $null
$TagName = "latest"

try {
    $Release = Invoke-RestMethod -Uri $ApiUrl -Headers @{ "User-Agent" = "JARVIS-Installer" } -TimeoutSec 15
    $TagName = $Release.tag_name
    $ExeAsset = $Release.assets | Where-Object { $_.name -like "*.exe" } | Select-Object -First 1
    if ($ExeAsset) {
        $DownloadUrl = $ExeAsset.browser_download_url
        Write-Host "      Found release: $TagName ($([math]::Round($ExeAsset.size / 1MB, 2)) MB)" -ForegroundColor Green
    }
} catch {
    Write-Host "      Notice: GitHub API rate limited or unreachable. Using direct release CDN fallback." -ForegroundColor DarkYellow
}

if (-not $DownloadUrl) {
    $DownloadUrl = "https://github.com/$Repo/releases/latest/download/JARVIS-Island-Windows.exe"
}

# 4. Download executable
Write-Host "[4/5] Downloading JARVIS Dynamic Island ($TagName)..." -ForegroundColor Yellow
$TempFile = "$InstallDir\temp_download.exe"
$DownloadSuccess = $false

# Primary: High-speed curl.exe with follow redirects and progress display
$CurlExe = Get-Command "curl.exe" -ErrorAction SilentlyContinue
if ($CurlExe) {
    Write-Host "      Using curl.exe engine for fast binary streaming..." -ForegroundColor Gray
    & curl.exe -fL --progress-bar -o "$TempFile" "$DownloadUrl"
    if ($LASTEXITCODE -eq 0 -and (Test-Path $TempFile) -and ((Get-Item $TempFile).Length -gt 100000)) {
        $DownloadSuccess = $true
    }
}

# Secondary: .NET WebClient with automatic 302 redirection support
if (-not $DownloadSuccess) {
    try {
        Write-Host "      Falling back to .NET WebClient..." -ForegroundColor Gray
        $WebClient = New-Object System.Net.WebClient
        $WebClient.Headers.Add("User-Agent", "JARVIS-Installer")
        $WebClient.DownloadFile($DownloadUrl, $TempFile)
        if ((Test-Path $TempFile) -and ((Get-Item $TempFile).Length -gt 100000)) {
            $DownloadSuccess = $true
        }
    } catch {
        Write-Host "      WebClient attempt: $($_.Exception.Message)" -ForegroundColor DarkGray
    }
}

# Tertiary: Invoke-WebRequest fallback
if (-not $DownloadSuccess) {
    try {
        Write-Host "      Falling back to Invoke-WebRequest..." -ForegroundColor Gray
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $TempFile -UseBasicParsing -TimeoutSec 60
        if ((Test-Path $TempFile) -and ((Get-Item $TempFile).Length -gt 100000)) {
            $DownloadSuccess = $true
        }
    } catch {
        Write-Host "      Invoke-WebRequest error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

if ($DownloadSuccess) {
    Move-Item -Path $TempFile -Destination $ExeTarget -Force
    Write-Host "      Binary successfully deployed to: $ExeTarget" -ForegroundColor Green
} else {
    Write-Host "      Failed to download executable from: $DownloadUrl" -ForegroundColor Red
    if (Test-Path $TempFile) { Remove-Item $TempFile -Force }
    exit 1
}

# 5. Create Shortcuts (Start Menu & Desktop)
Write-Host "[5/5] Creating application shortcuts..." -ForegroundColor Yellow
$WshShell = New-Object -ComObject WScript.Shell

# Start Menu Shortcut
$Shortcut = $WshShell.CreateShortcut($StartMenuShortcut)
$Shortcut.TargetPath = $ExeTarget
$Shortcut.WorkingDirectory = $InstallDir
$Shortcut.Description = "JARVIS Dynamic Island & Live Activities for Windows"
$Shortcut.Save()

# Desktop Shortcut
$ShortcutDesk = $WshShell.CreateShortcut($DesktopShortcut)
$ShortcutDesk.TargetPath = $ExeTarget
$ShortcutDesk.WorkingDirectory = $InstallDir
$ShortcutDesk.Description = "JARVIS Dynamic Island & Live Activities for Windows"
$ShortcutDesk.Save()

# Create Uninstaller Script
$UninstallScript = @"
Get-Process -Name "jarvis", "JARVIS-Island" -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item -Path "$DesktopShortcut" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$StartMenuShortcut" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$InstallDir" -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "JARVIS Dynamic Island has been cleanly uninstalled." -ForegroundColor Green
"@
Set-Content -Path "$InstallDir\uninstall.ps1" -Value $UninstallScript -Force

Write-Host ""
Write-Host "  [OK] Installation completed successfully!" -ForegroundColor Green
Write-Host "  [OK] Start Menu shortcut created." -ForegroundColor Green
Write-Host "  [OK] Desktop shortcut created." -ForegroundColor Green
Write-Host ""
Write-Host "  Launching JARVIS Dynamic Island..." -ForegroundColor Cyan
Start-Process -FilePath $ExeTarget
Write-Host "  Enjoy your Apple-grade Dynamic Island on Windows!" -ForegroundColor Cyan
Write-Host ""
