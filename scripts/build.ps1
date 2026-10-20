# Project JARVIS - Build Pipeline Script
param(
    [switch]$Release = $true
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " PROJECT JARVIS - Production Build" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Verify Prerequisites
Write-Host "`n[1/3] Verifying toolchain..." -ForegroundColor Yellow
$nodeVersion = node --version
$npmVersion = npm --version
$rustcVersion = rustc --version
Write-Host "Node.js: $nodeVersion | npm: $npmVersion" -ForegroundColor Gray
Write-Host "Rust: $rustcVersion" -ForegroundColor Gray

# 2. Build Tauri 2 Application with Embedded Assets
Write-Host "`n[2/2] Building Standalone Native Windows Executable..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot\..\app"
npx tauri build --no-bundle

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Copy-Item "$PSScriptRoot\..\app\src-tauri\target\release\jarvis.exe" "$PSScriptRoot\..\jarvis.exe" -Force
Write-Host "`nBuild complete! Executable updated at d:\Coding\JARVIS\jarvis.exe" -ForegroundColor Green
Set-Location "$PSScriptRoot\.."
