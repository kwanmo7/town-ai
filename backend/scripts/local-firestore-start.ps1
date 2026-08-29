[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ProjectId = "demo-town-ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$FirebaseToolsVersion = "15.26.0"
)

$ErrorActionPreference = "Stop"

if (-not $ProjectId.StartsWith("demo-", [System.StringComparison]::Ordinal)) {
    throw "Local Emulator ProjectId must start with 'demo-': $ProjectId"
}

$npx = Get-Command "npx.cmd" -ErrorAction SilentlyContinue
if ($null -eq $npx) {
    throw "npx.cmd was not found. Install Node.js 24 first."
}

$backendDirectory = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $backendDirectory "firebase.json"

Write-Host "Starting the Town AI Firestore Emulator."
Write-Host "  Project: $ProjectId"
Write-Host "  Database: town-ai (Standard)"
Write-Host "  Firestore: http://127.0.0.1:8081"
Write-Host "  Emulator UI: http://127.0.0.1:4000"

& $npx.Source `
    "--yes" `
    "firebase-tools@$FirebaseToolsVersion" `
    "emulators:start" `
    "--only" `
    "firestore" `
    "--project" `
    $ProjectId `
    "--config" `
    $configPath

if ($LASTEXITCODE -ne 0) {
    throw "Firestore Emulator exited with code $LASTEXITCODE."
}
