[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ProjectId = "demo-town-ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseId = "town-ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$EmulatorHost = "127.0.0.1:8081"
)

$ErrorActionPreference = "Stop"

if (-not $ProjectId.StartsWith("demo-", [System.StringComparison]::Ordinal)) {
    throw "Local Emulator ProjectId must start with 'demo-': $ProjectId"
}

try {
    $emulatorUri = [System.Uri]::new("http://$EmulatorHost")
}
catch {
    throw "EmulatorHost is invalid: $EmulatorHost"
}

if (-not $emulatorUri.IsLoopback) {
    throw "Local Backend requires a loopback Firestore Emulator: $EmulatorHost"
}

$backendDirectory = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $backendDirectory "gradlew.bat"

$env:FIRESTORE_PROJECT_ID = $ProjectId
$env:FIRESTORE_DATABASE_ID = $DatabaseId
$env:FIRESTORE_EMULATOR_HOST = $EmulatorHost
$env:REPORT_STORAGE_TYPE = "local"
$env:WEB_AUTH_ENABLED = "false"
$env:LINE_EVENT_DISPATCHER = "local"

Write-Host "Starting Town AI Backend with the Firestore Emulator."
Write-Host "  Project: $ProjectId"
Write-Host "  Database: $DatabaseId"
Write-Host "  Emulator: $EmulatorHost"

Push-Location $backendDirectory
try {
    & $gradle ":app:bootRun"
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
