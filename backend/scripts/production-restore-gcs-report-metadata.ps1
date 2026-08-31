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
    [switch]$ConfirmProductionRestore
)

$ErrorActionPreference = "Stop"

if (-not $ConfirmProductionRestore) {
    throw "Production restore requires -ConfirmProductionRestore."
}

if ($ProjectId -ne "town-ai" -or $DatabaseId -ne "town-ai") {
    throw "This script can target only the town-ai/town-ai database."
}

function Resolve-Gcloud {
    $command = Get-Command "gcloud.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $installed = Join-Path $env:LOCALAPPDATA "Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
    if (Test-Path -LiteralPath $installed) {
        return $installed
    }

    throw "gcloud.cmd was not found. Install and authenticate Google Cloud CLI."
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

function Invoke-Firestore {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("GET", "POST")]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter()]
        [object]$Body
    )

    $parameters = @{
        Uri         = "$script:FirestoreRoot$Path"
        Method      = $Method
        Headers     = @{ Authorization = "Bearer $script:AccessToken" }
        TimeoutSec  = 60
        ErrorAction = "Stop"
    }

    if ($PSBoundParameters.ContainsKey("Body")) {
        $json = $Body | ConvertTo-Json -Depth 20 -Compress
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    return Invoke-RestMethod @parameters
}

function New-StringValue {
    param([AllowEmptyString()][string]$Value)
    return @{ stringValue = $Value }
}

function New-IntegerValue {
    param([long]$Value)
    return @{
        integerValue = $Value.ToString(
            [System.Globalization.CultureInfo]::InvariantCulture
        )
    }
}

function New-TimestampValue {
    param([string]$Value)
    return @{ timestampValue = $Value }
}

function New-NullValue {
    return @{ nullValue = $null }
}

function New-IntegerArrayValue {
    param([long[]]$Values)
    return @{
        arrayValue = @{
            values = @($Values | ForEach-Object { New-IntegerValue $_ })
        }
    }
}

function ConvertFrom-Utf8Base64 {
    param([Parameter(Mandatory)][string]$Value)

    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($Value)
    )
}

function New-CreateWrite {
    param(
        [Parameter(Mandatory)]
        [string]$RelativeDocumentName,

        [Parameter(Mandatory)]
        [hashtable]$Fields
    )

    return @{
        update = @{
            name   = "$script:DocumentRoot/$RelativeDocumentName"
            fields = $Fields
        }
        currentDocument = @{
            exists = $false
        }
    }
}

function Get-CollectionDocuments {
    param([Parameter(Mandatory)][string]$Collection)

    $response = Invoke-Firestore -Method GET -Path "/${Collection}?pageSize=100"
    if ($null -ne $response.documents) {
        return @($response.documents | Where-Object { $null -ne $_ })
    }
    return @()
}

function Get-DocumentById {
    param(
        [Parameter(Mandatory)][object[]]$Documents,
        [Parameter(Mandatory)][string]$Collection,
        [Parameter(Mandatory)][string]$DocumentId
    )

    $expectedName = "$script:DocumentRoot/$Collection/$DocumentId"
    return $Documents |
        Where-Object { $_.name -eq $expectedName } |
        Select-Object -First 1
}

function Get-GcsObjectSnapshot {
    param([Parameter(Mandatory)][string]$StoragePath)

    $escapedBucket = [System.Uri]::EscapeDataString($ReportBucket)
    $escapedObject = [System.Uri]::EscapeDataString($StoragePath)
    $metadata = Invoke-RestMethod -Method GET `
        -Uri "https://storage.googleapis.com/storage/v1/b/$escapedBucket/o/$escapedObject" `
        -Headers @{ Authorization = "Bearer $script:AccessToken" } `
        -TimeoutSec 60 `
        -ErrorAction Stop

    return [ordered]@{
        name       = $metadata.name
        generation = [string]$metadata.generation
        md5Hash    = $metadata.md5Hash
        crc32c     = $metadata.crc32c
        size       = [long]$metadata.size
        updated    = $metadata.updated
    }
}

function Assert-EqualValue {
    param(
        [Parameter()][AllowNull()][object]$Actual,
        [Parameter()][AllowNull()][object]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualText = [System.Convert]::ToString(
        $Actual,
        [System.Globalization.CultureInfo]::InvariantCulture
    )
    $expectedText = [System.Convert]::ToString(
        $Expected,
        [System.Globalization.CultureInfo]::InvariantCulture
    )
    if (-not [string]::Equals($actualText, $expectedText, [System.StringComparison]::Ordinal)) {
        throw "Firestore value verification failed: $Label (actual='$actualText', expected='$expectedText')"
    }
}

function Get-IntegerArray {
    param([Parameter(Mandatory)][object]$Field)

    if ($null -eq $Field.arrayValue.values) {
        return @()
    }
    return @($Field.arrayValue.values | ForEach-Object { [long]$_.integerValue })
}

$legacyReports = @(
    @{ id = 2L; type = "ALL"; prompt = "all-v1"; path = "reports/v1/all/2026-08-04_2.md"; areas = [long[]]@(1L) }
    @{ id = 3L; type = "AREA"; prompt = "area-v1"; path = (ConvertFrom-Utf8Base64 "cmVwb3J0cy92MS9hcmVhL+yEvO2EsOuvuOuCmOuvuF8yMDI2LTA4LTA1XzMubWQ="); areas = [long[]]@(1L) }
    @{ id = 4L; type = "AREA"; prompt = "area-v1"; path = (ConvertFrom-Utf8Base64 "cmVwb3J0cy92MS9hcmVhL+yEvO2EsOuvuOuCmOuvuF8yMDI2LTA4LTA1XzQubWQ="); areas = [long[]]@(1L) }
    @{ id = 5L; type = "ALL"; prompt = "all-v1"; path = "reports/v1/all/2026-08-05_5.md"; areas = [long[]]@(1L) }
    @{ id = 6L; type = "AREA"; prompt = "area-v1"; path = (ConvertFrom-Utf8Base64 "cmVwb3J0cy92MS9hcmVhL+yEvO2EsOuvuOuCmOuvuF8yMDI2LTA4LTA1XzYubWQ="); areas = [long[]]@(1L) }
    @{ id = 7L; type = "AREA"; prompt = "area-v1"; path = (ConvertFrom-Utf8Base64 "cmVwb3J0cy92MS9hcmVhL+y5tOyZgOq1rOy5mF8yMDI2LTA4LTA5XzcubWQ="); areas = [long[]]@(2L) }
    @{ id = 8L; type = "AREA"; prompt = "area-v1"; path = (ConvertFrom-Utf8Base64 "cmVwb3J0cy92MS9hcmVhL+ydtOuCmOqyjOy5tOydtOqwhF8yMDI2LTA4LTA5XzgubWQ="); areas = [long[]]@(3L) }
    @{ id = 9L; type = "COMPARE"; prompt = "compare-v1"; path = (ConvertFrom-Utf8Base64 "cmVwb3J0cy92MS9jb21wYXJlL+yEvO2EsOuvuOuCmOuvuC3snbTrgpjqsozsubTsnbTqsIRfMjAyNi0wOC0wOV85Lm1k"); areas = [long[]]@(1L, 3L) }
    @{ id = 10L; type = "ALL"; prompt = "all-v1"; path = "reports/v1/all/2026-08-09_10.md"; areas = [long[]]@(1L, 2L, 3L) }
)

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
$script:DocumentRoot = "projects/$escapedProjectId/databases/$escapedDatabaseId/documents"
$script:FirestoreRoot = "https://firestore.googleapis.com/v1/$script:DocumentRoot"

$areas = @(Get-CollectionDocuments -Collection "areas")
foreach ($areaId in 1L..3L) {
    if ($null -eq (Get-DocumentById -Documents $areas -Collection "areas" -DocumentId ([string]$areaId))) {
        throw "Required restored Area is missing: $areaId"
    }
}

$snapshotsBefore = @{}
foreach ($legacyReport in $legacyReports) {
    $snapshotsBefore[[string]$legacyReport.id] = Get-GcsObjectSnapshot `
        -StoragePath $legacyReport.path
}

$reports = @(Get-CollectionDocuments -Collection "reports")
$writes = [System.Collections.Generic.List[object]]::new()
foreach ($legacyReport in $legacyReports) {
    $reportId = [string]$legacyReport.id
    $existing = Get-DocumentById `
        -Documents $reports `
        -Collection "reports" `
        -DocumentId $reportId
    $snapshot = $snapshotsBefore[$reportId]

    if ($null -eq $existing) {
        $fields = @{
            id                   = New-IntegerValue $legacyReport.id
            reportType           = New-StringValue $legacyReport.type
            model                = New-StringValue "gpt-5.4-mini"
            promptVersion        = New-StringValue $legacyReport.prompt
            sourceFingerprint    = New-NullValue
            storagePath          = New-StringValue $legacyReport.path
            sourceWebhookEventId = New-NullValue
            targetAreaIds        = New-IntegerArrayValue $legacyReport.areas
            createdAt            = New-TimestampValue $snapshot.updated
            updatedAt            = New-TimestampValue $snapshot.updated
        }
        $writes.Add(
            (New-CreateWrite -RelativeDocumentName "reports/$reportId" -Fields $fields)
        )
        continue
    }

    Assert-EqualValue $existing.fields.id.integerValue $legacyReport.id "reports/$reportId.id"
    Assert-EqualValue $existing.fields.reportType.stringValue $legacyReport.type "reports/$reportId.reportType"
    Assert-EqualValue $existing.fields.model.stringValue "gpt-5.4-mini" "reports/$reportId.model"
    Assert-EqualValue $existing.fields.promptVersion.stringValue $legacyReport.prompt "reports/$reportId.promptVersion"
    Assert-EqualValue $existing.fields.storagePath.stringValue $legacyReport.path "reports/$reportId.storagePath"
    Assert-EqualValue $existing.fields.createdAt.timestampValue $snapshot.updated "reports/$reportId.createdAt"
    $actualAreas = @(Get-IntegerArray -Field $existing.fields.targetAreaIds)
    Assert-EqualValue ($actualAreas -join ",") ($legacyReport.areas -join ",") "reports/$reportId.targetAreaIds"
}

$counters = @(Get-CollectionDocuments -Collection "counters")
$reportCounter = Get-DocumentById `
    -Documents $counters `
    -Collection "counters" `
    -DocumentId "report"
if ($null -eq $reportCounter) {
    $writes.Add(
        (New-CreateWrite -RelativeDocumentName "counters/report" -Fields @{
                lastId = New-IntegerValue 10L
            })
    )
}
elseif ([long]$reportCounter.fields.lastId.integerValue -lt 10L) {
    $writes.Add(@{
            update = @{
                name = "$script:DocumentRoot/counters/report"
                fields = @{
                    lastId = New-IntegerValue 10L
                }
            }
            updateMask = @{
                fieldPaths = @("lastId")
            }
            currentDocument = @{
                updateTime = $reportCounter.updateTime
            }
        })
}

Write-Host "Starting legacy GCS Report metadata restore."
Write-Host "  Project/Database: $ProjectId/$DatabaseId"
Write-Host "  Legacy reports: $($legacyReports.Count)"
Write-Host "  Pending Firestore writes: $($writes.Count)"

if ($writes.Count -gt 0) {
    $null = Invoke-Firestore -Method POST -Path ":commit" -Body @{
        writes = @($writes)
    }
}

$reportsAfter = @(Get-CollectionDocuments -Collection "reports")
$countersAfter = @(Get-CollectionDocuments -Collection "counters")
foreach ($legacyReport in $legacyReports) {
    $reportId = [string]$legacyReport.id
    $restored = Get-DocumentById `
        -Documents $reportsAfter `
        -Collection "reports" `
        -DocumentId $reportId
    if ($null -eq $restored) {
        throw "Legacy Report metadata was not restored: $reportId"
    }

    $snapshotAfter = Get-GcsObjectSnapshot -StoragePath $legacyReport.path
    $beforeJson = $snapshotsBefore[$reportId] | ConvertTo-Json -Compress
    $afterJson = $snapshotAfter | ConvertTo-Json -Compress
    if ($beforeJson -ne $afterJson) {
        throw "GCS Report object changed during metadata restore: $($legacyReport.path)"
    }
}

$counterAfter = Get-DocumentById `
    -Documents $countersAfter `
    -Collection "counters" `
    -DocumentId "report"
if ($null -eq $counterAfter -or [long]$counterAfter.fields.lastId.integerValue -lt 10L) {
    throw "Report counter verification failed after restore."
}

Write-Host ""
Write-Host "Legacy GCS Report metadata restore completed."
Write-Host "  Restored/verified reports: $($legacyReports.Count)"
Write-Host "  Total Firestore reports: $($reportsAfter.Count)"
Write-Host "  Report counter: $($counterAfter.fields.lastId.integerValue)"
Write-Host "  GCS objects: unchanged"
