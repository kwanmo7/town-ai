[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ProjectId = "town-ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseId = "town-ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$Location = "asia-northeast1",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ReportBucket = "town_ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$AllowedPrefix = "reports/v1/",

    [Parameter()]
    [ValidateRange(1, 8760)]
    [int]$MinimumAgeHours = 24,

    [Parameter()]
    [switch]$DeleteOrphans,

    [Parameter()]
    [switch]$ConfirmProductionCleanup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$modulePath = Join-Path `
    (Join-Path $PSScriptRoot "modules") `
    "ReportOrphanCleanup.psm1"
Import-Module $modulePath -Force

Assert-ReportCleanupScope `
    -ProjectId $ProjectId `
    -DatabaseId $DatabaseId `
    -ReportBucket $ReportBucket `
    -AllowedPrefix $AllowedPrefix
Assert-ReportCleanupMode `
    -DeleteOrphans $DeleteOrphans.IsPresent `
    -ConfirmProductionCleanup $ConfirmProductionCleanup.IsPresent

function Resolve-Gcloud {
    $command = Get-Command "gcloud.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $command = Get-Command "gcloud" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $installed = Join-Path `
        $env:LOCALAPPDATA `
        "Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
    if (Test-Path -LiteralPath $installed) {
        return $installed
    }

    throw "gcloud was not found. Install and authenticate Google Cloud CLI."
}

function Invoke-Gcloud {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $output = & $script:Gcloud @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed: gcloud $($Arguments -join ' ')"
    }
    return $output
}

function Get-OptionalProperty {
    param(
        [Parameter()][AllowNull()][object]$InputObject,
        [Parameter(Mandatory)][string]$Name
    )

    if ($null -eq $InputObject) {
        return $null
    }
    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Get-FirestoreReportStoragePaths {
    $paths = [System.Collections.Generic.List[string]]::new()
    $pageToken = ""

    do {
        $uri = "$script:FirestoreRoot/reports?pageSize=100"
        if (-not [string]::IsNullOrWhiteSpace($pageToken)) {
            $uri += "&pageToken=$([System.Uri]::EscapeDataString($pageToken))"
        }

        $response = Invoke-RestMethod `
            -Method GET `
            -Uri $uri `
            -Headers @{ Authorization = "Bearer $script:AccessToken" } `
            -TimeoutSec 60 `
            -ErrorAction Stop
        $documents = Get-OptionalProperty $response "documents"
        foreach ($document in @($documents)) {
            $fields = Get-OptionalProperty $document "fields"
            $storagePathField = Get-OptionalProperty $fields "storagePath"
            $storagePath = [string](
                Get-OptionalProperty $storagePathField "stringValue"
            )
            if (-not [string]::IsNullOrWhiteSpace($storagePath)) {
                $paths.Add($storagePath)
            }
        }

        $pageToken = [string](
            Get-OptionalProperty $response "nextPageToken"
        )
    } while (-not [string]::IsNullOrWhiteSpace($pageToken))

    return @($paths)
}

function Get-GcsReportObjects {
    $objects = [System.Collections.Generic.List[object]]::new()
    $pageToken = ""
    $escapedBucket = [System.Uri]::EscapeDataString($ReportBucket)
    $escapedPrefix = [System.Uri]::EscapeDataString($AllowedPrefix)

    do {
        $uri = "https://storage.googleapis.com/storage/v1/b/$escapedBucket/o?prefix=$escapedPrefix&maxResults=1000"
        if (-not [string]::IsNullOrWhiteSpace($pageToken)) {
            $uri += "&pageToken=$([System.Uri]::EscapeDataString($pageToken))"
        }

        $response = Invoke-RestMethod `
            -Method GET `
            -Uri $uri `
            -Headers @{ Authorization = "Bearer $script:AccessToken" } `
            -TimeoutSec 60 `
            -ErrorAction Stop
        $items = Get-OptionalProperty $response "items"
        foreach ($item in @($items)) {
            if ($null -ne $item) {
                $objects.Add($item)
            }
        }

        $pageToken = [string](
            Get-OptionalProperty $response "nextPageToken"
        )
    } while (-not [string]::IsNullOrWhiteSpace($pageToken))

    return @($objects)
}

$script:Gcloud = Resolve-Gcloud
$databaseJson = Invoke-Gcloud -Arguments @(
    "firestore", "databases", "describe",
    "--project=$ProjectId",
    "--database=$DatabaseId",
    "--format=json"
)
$database = ($databaseJson -join [Environment]::NewLine) | ConvertFrom-Json
if ($database.databaseEdition -ne "STANDARD" -or
        $database.type -ne "FIRESTORE_NATIVE" -or
        $database.locationId -ne $Location) {
    throw "The target Firestore configuration does not match the expected database."
}

$script:AccessToken = (
    Invoke-Gcloud -Arguments @("auth", "print-access-token")
) -join ""
if ([string]::IsNullOrWhiteSpace($script:AccessToken)) {
    throw "Failed to obtain a Google Cloud access token."
}

$escapedProjectId = [System.Uri]::EscapeDataString($ProjectId)
$escapedDatabaseId = [System.Uri]::EscapeDataString($DatabaseId)
$documentRoot = "projects/$escapedProjectId/databases/$escapedDatabaseId/documents"
$script:FirestoreRoot = "https://firestore.googleapis.com/v1/$documentRoot"

$cutoffUtc = [DateTimeOffset]::UtcNow.AddHours(-$MinimumAgeHours)
$referencedPaths = @(Get-FirestoreReportStoragePaths)
$storageObjects = @(Get-GcsReportObjects)
$selection = Select-ReportOrphanCandidates `
    -StorageObjects $storageObjects `
    -ReferencedPaths $referencedPaths `
    -CutoffUtc $cutoffUtc `
    -AllowedPrefix $AllowedPrefix

Write-Host "Report orphan cleanup scan completed."
Write-Host "  Mode: $(if ($DeleteOrphans) { 'DELETE' } else { 'DRY_RUN' })"
Write-Host "  Project/Database: $ProjectId/$DatabaseId"
Write-Host "  Bucket/Prefix: gs://$ReportBucket/$AllowedPrefix"
Write-Host "  Minimum age: $MinimumAgeHours hour(s)"
Write-Host "  Firestore references: $($referencedPaths.Count)"
Write-Host "  GCS objects scanned: $($storageObjects.Count)"
Write-Host "  Eligible orphans: $($selection.Eligible.Count)"
Write-Host "  Recent unreferenced objects: $($selection.Recent.Count)"
Write-Host "  Ignored non-report objects: $($selection.Ignored.Count)"

if ($selection.Eligible.Count -gt 0) {
    Write-Host ""
    Write-Host "Eligible orphan objects:"
    $selection.Eligible |
        Select-Object name, updated, size, generation |
        Format-Table -AutoSize |
        Out-String |
        Write-Host
}

if ($selection.Recent.Count -gt 0) {
    Write-Host "Recent objects excluded by the minimum-age guard:"
    $selection.Recent |
        Select-Object name, updated, size, generation |
        Format-Table -AutoSize |
        Out-String |
        Write-Host
}

if (-not $DeleteOrphans) {
    Write-Host "DRY RUN only. No GCS object was deleted."
    Write-Host "Use -DeleteOrphans -ConfirmProductionCleanup to delete eligible objects."
    return
}

$deletedCount = 0
foreach ($candidate in $selection.Eligible) {
    $deleteResult = Remove-ReportOrphanObject `
        -Candidate $candidate `
        -ReportBucket $ReportBucket `
        -AccessToken $script:AccessToken
    Write-Host "  $deleteResult $($candidate.name)"
    $deletedCount++
}

Write-Host ""
Write-Host "Report orphan cleanup completed."
Write-Host "  Deleted/already missing: $deletedCount"
Write-Host "  Recent objects preserved: $($selection.Recent.Count)"
