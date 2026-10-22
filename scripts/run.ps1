# Project GLOOM - Launch Script
param(
    [switch]$Dev = $false
)

$ErrorActionPreference = "Stop"

Set-Location "$PSScriptRoot\..\app"

if ($Dev) {
    Write-Host "Starting GLOOM in Dev Mode with Hot Reload..." -ForegroundColor Cyan
    npm run tauri dev
} else {
    $exePath = "$PSScriptRoot\..\app\src-tauri\target\release\gloom.exe"
    if (Test-Path $exePath) {
        Write-Host "Launching GLOOM Release Build..." -ForegroundColor Green
        Start-Process $exePath
    } else {
        $debugExePath = "$PSScriptRoot\..\app\src-tauri\target\debug\gloom.exe"
        if (Test-Path $debugExePath) {
            Write-Host "Launching GLOOM Debug Build..." -ForegroundColor Yellow
            Start-Process $debugExePath
        } else {
            Write-Host "No executable found. Running build first..." -ForegroundColor Cyan
            & "$PSScriptRoot\build.ps1" -Release
            Start-Process $exePath
        }
    }
}

Set-Location "$PSScriptRoot\.."
