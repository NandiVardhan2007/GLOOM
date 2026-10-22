# Project gloom - Automated Test Runner
Write-Host "Running Project gloom Automated Test Suite..." -ForegroundColor Cyan

$testResults = python -m unittest discover -s tests -p "*_test.py" -v
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nAll automated tests PASSED successfully." -ForegroundColor Green
} else {
    Write-Host "`nAutomated tests FAILED." -ForegroundColor Red
    exit 1
}
