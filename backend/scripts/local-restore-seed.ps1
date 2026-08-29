[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

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
    throw "Restore is allowed only against a loopback Firestore Emulator: $EmulatorHost"
}

$escapedProjectId = [System.Uri]::EscapeDataString($ProjectId)
$escapedDatabaseId = [System.Uri]::EscapeDataString($DatabaseId)
$clearUrl = "http://$EmulatorHost/emulator/v1/projects/$escapedProjectId/databases/$escapedDatabaseId/documents"

Write-Host "Clearing Firestore Emulator data: $ProjectId/$DatabaseId"
Invoke-WebRequest `
    -Uri $clearUrl `
    -Method Delete `
    -TimeoutSec 30 `
    -UseBasicParsing | Out-Null

Write-Host "Firestore Emulator data was cleared. Recreating the local seed."
& (Join-Path $PSScriptRoot "local-seed.ps1") -BaseUrl $BaseUrl
