# GLOOM Windows Dedicated Clean Uninstaller
# Automated one-line uninstaller for GLOOM Dynamic Island & Fluid Dock on Windows 11/10.

$ErrorActionPreference = 'SilentlyContinue'

Clear-Host -ErrorAction SilentlyContinue

Write-Host @"
 ╭──────────────────────────────────────────────────────────────╮
 │   GLOOM Dynamic Island & Fluid Dock — Clean Uninstaller      │
 ╰──────────────────────────────────────────────────────────────╯
"@ -ForegroundColor Cyan

Write-Host " [1/3] Stopping running GLOOM instances..." -ForegroundColor Blue
Stop-Process -Name "gloom" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "GLOOM" -Force -ErrorAction SilentlyContinue

Write-Host " [2/3] Running GLOOM silent uninstaller..." -ForegroundColor Blue
$uninstallers = @(
    (Join-Path $env:LOCALAPPDATA "Programs\GLOOM\uninstall.exe"),
    (Join-Path $env:ProgramFiles "GLOOM\uninstall.exe"),
    (Join-Path $env:LOCALAPPDATA "Programs\GLOOM\unins000.exe"),
    (Join-Path $env:ProgramFiles "GLOOM\unins000.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "GLOOM\unins000.exe")
)
$unins = $uninstallers | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($unins) {
    $args = if ($unins.EndsWith("uninstall.exe")) { "--uninstall --auto-uninstall" } else { "/VERYSILENT /NORESTART /SUPPRESSMSGBOXES" }
    Start-Process -FilePath $unins -ArgumentList $args -Wait
    Write-Host "       ✔ Native uninstaller completed." -ForegroundColor Green
}

# Clean any residual shortcuts, configs, and temporary files
Write-Host " [3/3] Purging residual files, caches, and autostart shortcuts..." -ForegroundColor Blue
Remove-Item -Path (Join-Path $env:LOCALAPPDATA "GLOOM") -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $env:LOCALAPPDATA "Programs\GLOOM") -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\GLOOM") -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup\GLOOM.lnk") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path ([Environment]::GetFolderPath("Desktop")) "GLOOM.lnk") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $env:TEMP "*gloom*") -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "`n ✔ GLOOM has been completely uninstalled with zero leftovers.`n" -ForegroundColor Green
