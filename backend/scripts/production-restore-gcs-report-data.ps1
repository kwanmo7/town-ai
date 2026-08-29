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

function ConvertFrom-Utf8Base64 {
    param([Parameter(Mandatory)][string]$Value)

    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($Value)
    )
}

function Get-AreaKey {
    param(
        [Parameter(Mandatory)]
        [string]$Prefecture,

        [Parameter(Mandatory)]
        [string]$City,

        [Parameter(Mandatory)]
        [string]$Name
    )

    $value = "$Prefecture$([char]0)$City$([char]0)$Name"
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha256.ComputeHash(
            [System.Text.Encoding]::UTF8.GetBytes($value)
        )
    }
    finally {
        $sha256.Dispose()
    }

    return [System.BitConverter]::ToString($digest).Replace("-", "").ToLowerInvariant()
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
        $response.documents | Where-Object { $null -ne $_ }
    }
}

function Get-DocumentById {
    param(
        [Parameter(Mandatory)][object[]]$Documents,
        [Parameter(Mandatory)][string]$Collection,
        [Parameter(Mandatory)][string]$DocumentId
    )

    $expectedName = "$script:DocumentRoot/$Collection/$DocumentId"
    return $Documents | Where-Object { $_.name -eq $expectedName } | Select-Object -First 1
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

function Get-GcsReportSnapshot {
    $escapedBucket = [System.Uri]::EscapeDataString($ReportBucket)
    $escapedPrefix = [System.Uri]::EscapeDataString("reports/v1/")
    $response = Invoke-RestMethod -Method GET `
        -Uri "https://storage.googleapis.com/storage/v1/b/$escapedBucket/o?prefix=$escapedPrefix&maxResults=1000" `
        -Headers @{ Authorization = "Bearer $script:AccessToken" } `
        -TimeoutSec 60 `
        -ErrorAction Stop

    $snapshots = foreach ($metadata in @($response.items)) {
        [ordered]@{
            name        = $metadata.name
            generation  = [string]$metadata.generation
            md5Hash     = $metadata.md5Hash
            crc32cHash  = $metadata.crc32c
            size        = [long]$metadata.size
            updateTime  = $metadata.updated
        }
    }

    return @($snapshots | Sort-Object name)
}

$fixtures = @(
    @{
        id         = 1L
        name       = (ConvertFrom-Utf8Base64 "7IS87YSw66+464KY66+4")
        prefecture = (ConvertFrom-Utf8Base64 "6rCA64KY6rCA7JmA7ZiE")
        city       = (ConvertFrom-Utf8Base64 "7JqU7L2U7ZWY66eI7IucIOyTsOymiO2CpOq1rA==")
        station    = (ConvertFrom-Utf8Base64 "7IS87YSw66+464KY66+47Jet")
        visit      = @{
            id              = 1L
            visitDate       = "2026-07-25"
            atmosphereScore = 8L
            infraScore      = 8L
            cleanScore      = 8L
            sizeScore       = 7L
            accessScore     = 7L
            memo            = (ConvertFrom-Utf8Base64 "7JetIOyjvOuzgOydtCDrhJPsnYAg6rSR7J6l7Jy866GcIOuQmOyWtCDsnojslrQg6rCc67Cp6rCQ7J20IOyeiOqzoCwg6rSR7J6l7J2EIOykkeyLrOycvOuhnCDsh7ztlZHrqrDqs7wg7IOB6rCA6rCAIOyeiOyWtCDrj5nshKDsnbQg7KKL6rKMIOuKkOq7tOyhjOuLpC4g7JWE7KeBIOqxsOyjvOuLqOyngCDsqr3snYAg67O07KeAIOuqu+2WiOyngOunjCDsi6Drj4Tsi5wg64qQ64KM7J20IOqwle2VtCDsnpgg7KCV64+I65CY7Ja0IOyeiOydhCDqsoMg6rCZ7J2AIOyduOyDgeydhCDrsJvslZjri6Qu")
        }
    },
    @{
        id         = 2L
        name       = (ConvertFrom-Utf8Base64 "7Lm07JmA6rWs7LmY")
        prefecture = (ConvertFrom-Utf8Base64 "7IKs7J207YOA66eI7ZiE")
        city       = (ConvertFrom-Utf8Base64 "7Lm07JmA6rWs7LmY7Iuc")
        station    = (ConvertFrom-Utf8Base64 "7Lm07JmA6rWs7LmY7Jet")
        visit      = @{
            id              = 2L
            visitDate       = "2026-08-08"
            atmosphereScore = 7L
            infraScore      = 9L
            cleanScore      = 7L
            sizeScore       = 8L
            accessScore     = 9L
            memo            = (ConvertFrom-Utf8Base64 "7JetIOyjvOuzgOyXkCDrnbzrnbzthYzrnbzsiqTsmYAg7JWE66as7JikIOqwmeydgCDsh7ztlZHrqrDsnbQg7J6I7Ja0IOyDne2ZnCDsnbjtlITrnbzsmYAg7KCR6re87ISx7J20IOyii+qyjCDripDqu7TsoYzri6QuIOyXreyXkOyEnCDsobDquIgg67KX7Ja064KY66m0IOunqOyFmCDsnbTsmbgg7KO87YOd7J2YIOuFuO2bhOqwkOydtCDrs7Tsl6wg67aE7JyE6riw64qUIOyVhOyJrOyboOuLpC4gVVLsnbTrgpgg7YOA7JuM66eo7IWY7J2AIOyii+yVhCDrs7TsmIDsp4Drp4wg6rCA6rKpIOu2gOuLtOydtCDsnojqs6AsIOydvOuwmCDsp5Eg66ek66y87J2AIOyggOugtO2VtOuPhCDsp5Eg7J6Q7LK064qUIO2BrOqyjCDsoovslYQg67O07J207KeAIOyViuyVmOuLpC4=")
        }
    },
    @{
        id         = 3L
        name       = (ConvertFrom-Utf8Base64 "7J2064KY6rKM7Lm07J206rCE")
        prefecture = (ConvertFrom-Utf8Base64 "7LmY67CU7ZiE")
        city       = (ConvertFrom-Utf8Base64 "7LmY67CU7IucIOuvuO2VmOuniOq1rA==")
        station    = (ConvertFrom-Utf8Base64 "7J2064KY6rKM7Lm07J206rCE7Jet")
        visit      = @{
            id              = 3L
            visitDate       = "2026-08-09"
            atmosphereScore = 7L
            infraScore      = 4L
            cleanScore      = 9L
            sizeScore       = 9L
            accessScore     = 6L
            memo            = (ConvertFrom-Utf8Base64 "6rWJ7J6l7Z6IIOyjvOqxsCDri6jsp4Ag6rCZ7J2AIOuKkOuCjOydtOqzoCDsnKDrj5nsnbjqtazqsIAg6rGw7J2YIOyXhuyXiOuLpC4g7LKt6rKw64+E7JmAIOuEk+ydgCDsp5Eg6rCA64ql7ISx7J2AIOyii+qyjCDripDqu7TsoYzsp4Drp4wg7IOd7ZmcIOyduO2UhOudvOqwgCDrtoDsobHtlojqs6AsIOydtOuPmSDsi5zqsITsnbQg66eO7J20IOqxuOugpCDsoJHqt7zshLHrj4Qg7JWE7Ims7Jug64ukLiDsnbTrgpjqsowg7ZW07JWI6rO17JuQ7J2AIOyYiOyBmOqzoCDsnpgg65CY7Ja0IOyeiOyXiOuLpC4=")
        }
    }
)

$script:Gcloud = Resolve-Gcloud

$databaseJson = Invoke-Gcloud -Arguments @(
    "firestore", "databases", "describe",
    "--project=$ProjectId",
    "--database=$DatabaseId",
    "--format=json"
)
$database = ($databaseJson -join [Environment]::NewLine) | ConvertFrom-Json
if ($database.databaseEdition -ne "STANDARD" -or $database.type -ne "FIRESTORE_NATIVE" -or $database.locationId -ne $Location) {
    throw "The target Firestore configuration does not match the expected database."
}

$script:AccessToken = (
    Invoke-Gcloud -Arguments @("auth", "print-access-token")
) -join ""
if ([string]::IsNullOrWhiteSpace($script:AccessToken)) {
    throw "Failed to obtain a Google Cloud access token."
}

$reportSnapshot = Get-GcsReportSnapshot
$legacyReport = "reports/v1/all/2026-08-09_10.md"
if ($reportSnapshot.Count -ne 9 -or $legacyReport -notin @($reportSnapshot.name)) {
    throw "The nine legacy GCS reports were not found. Firestore was not changed."
}
$reportSnapshotJson = $reportSnapshot | ConvertTo-Json -Depth 5 -Compress

$escapedProjectId = [System.Uri]::EscapeDataString($ProjectId)
$escapedDatabaseId = [System.Uri]::EscapeDataString($DatabaseId)
$script:DocumentRoot = "projects/$escapedProjectId/databases/$escapedDatabaseId/documents"
$script:FirestoreRoot = "https://firestore.googleapis.com/v1/$script:DocumentRoot"

$collections = Invoke-Firestore -Method POST -Path ":listCollectionIds" -Body @{
    pageSize = 1000
}
$existingCollections = @(
    $collections.collectionIds |
        Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
)
$expectedCollections = @("areaKeys", "areas", "counters", "visits")
$needsRestore = $existingCollections.Count -eq 0
if (-not $needsRestore) {
    $collectionDifference = @(
        Compare-Object -ReferenceObject ($expectedCollections | Sort-Object) -DifferenceObject ($existingCollections | Sort-Object)
    )
    if ($collectionDifference.Count -ne 0) {
        throw "Firestore contains unexpected collections: $($existingCollections -join ', ')"
    }
}

$now = (Get-Date).ToUniversalTime().ToString(
    "yyyy-MM-ddTHH:mm:ss.fffZ",
    [System.Globalization.CultureInfo]::InvariantCulture
)
$writes = [System.Collections.Generic.List[object]]::new()

foreach ($fixture in $fixtures) {
    $areaId = [long]$fixture.id
    $visit = $fixture.visit
    $areaFields = @{
        id         = New-IntegerValue $areaId
        name       = New-StringValue $fixture.name
        prefecture = New-StringValue $fixture.prefecture
        city       = New-StringValue $fixture.city
        station    = New-StringValue $fixture.station
        createdAt  = New-TimestampValue $now
        updatedAt  = New-TimestampValue $now
        deletedAt  = New-NullValue
    }
    $writes.Add(
        (New-CreateWrite -RelativeDocumentName "areas/$areaId" -Fields $areaFields)
    )

    $visitFields = @{
        id              = New-IntegerValue ([long]$visit.id)
        areaId          = New-IntegerValue $areaId
        visitDate       = New-StringValue $visit.visitDate
        atmosphereScore = New-IntegerValue ([long]$visit.atmosphereScore)
        infraScore      = New-IntegerValue ([long]$visit.infraScore)
        cleanScore      = New-IntegerValue ([long]$visit.cleanScore)
        sizeScore       = New-IntegerValue ([long]$visit.sizeScore)
        accessScore     = New-IntegerValue ([long]$visit.accessScore)
        memo            = New-StringValue $visit.memo
        createdAt       = New-TimestampValue $now
        updatedAt       = New-TimestampValue $now
    }
    $writes.Add(
        (New-CreateWrite -RelativeDocumentName "visits/$($visit.id)" -Fields $visitFields)
    )

    $areaKey = Get-AreaKey -Prefecture $fixture.prefecture -City $fixture.city -Name $fixture.name
    $writes.Add((New-CreateWrite -RelativeDocumentName "areaKeys/$areaKey" -Fields @{
                areaId = New-IntegerValue $areaId
            }))
}

$writes.Add((New-CreateWrite -RelativeDocumentName "counters/area" -Fields @{
            lastId = New-IntegerValue 3L
        }))
$writes.Add((New-CreateWrite -RelativeDocumentName "counters/visit" -Fields @{
            lastId = New-IntegerValue 3L
        }))

Write-Host "Starting the GCS report-based Firestore restore and verification."
Write-Host "  Project/Database: $ProjectId/$DatabaseId"
Write-Host "  Area: 3, Visit: 3, GCS Report: $($reportSnapshot.Count)"

if ($needsRestore) {
    $null = Invoke-Firestore -Method POST -Path ":commit" -Body @{
        writes = @($writes)
    }
}
else {
    Write-Host "  Expected restore collections already exist; verifying without rewriting."
}

$areaDocuments = @(Get-CollectionDocuments -Collection "areas")
$visitDocuments = @(Get-CollectionDocuments -Collection "visits")
$keyDocuments = @(Get-CollectionDocuments -Collection "areaKeys")
$counterDocuments = @(Get-CollectionDocuments -Collection "counters")
$reportSnapshotAfter = Get-GcsReportSnapshot
$reportSnapshotAfterJson = $reportSnapshotAfter | ConvertTo-Json -Depth 5 -Compress

Write-Host "  Firestore counts: areas=$($areaDocuments.Count), visits=$($visitDocuments.Count), areaKeys=$($keyDocuments.Count), counters=$($counterDocuments.Count)"

if ($areaDocuments.Count -ne 3 -or $visitDocuments.Count -ne 3 -or $keyDocuments.Count -ne 3 -or $counterDocuments.Count -ne 2) {
    throw "Firestore document count verification failed after restore."
}

foreach ($fixture in $fixtures) {
    $areaId = [string]$fixture.id
    $visit = $fixture.visit
    $visitId = [string]$visit.id
    $areaDocument = Get-DocumentById -Documents $areaDocuments -Collection "areas" -DocumentId $areaId
    $visitDocument = Get-DocumentById -Documents $visitDocuments -Collection "visits" -DocumentId $visitId
    if ($null -eq $areaDocument -or $null -eq $visitDocument) {
        throw "Firestore fixture document verification failed: area=$areaId visit=$visitId"
    }

    Assert-EqualValue $areaDocument.fields.id.integerValue $fixture.id "areas/$areaId.id"
    Assert-EqualValue $areaDocument.fields.name.stringValue $fixture.name "areas/$areaId.name"
    Assert-EqualValue $areaDocument.fields.prefecture.stringValue $fixture.prefecture "areas/$areaId.prefecture"
    Assert-EqualValue $areaDocument.fields.city.stringValue $fixture.city "areas/$areaId.city"
    Assert-EqualValue $areaDocument.fields.station.stringValue $fixture.station "areas/$areaId.station"
    if ($areaDocument.fields.deletedAt.PSObject.Properties.Name -notcontains "nullValue") {
        throw "Firestore value verification failed: areas/$areaId.deletedAt"
    }

    Assert-EqualValue $visitDocument.fields.id.integerValue $visit.id "visits/$visitId.id"
    Assert-EqualValue $visitDocument.fields.areaId.integerValue $fixture.id "visits/$visitId.areaId"
    Assert-EqualValue $visitDocument.fields.visitDate.stringValue $visit.visitDate "visits/$visitId.visitDate"
    Assert-EqualValue $visitDocument.fields.atmosphereScore.integerValue $visit.atmosphereScore "visits/$visitId.atmosphereScore"
    Assert-EqualValue $visitDocument.fields.infraScore.integerValue $visit.infraScore "visits/$visitId.infraScore"
    Assert-EqualValue $visitDocument.fields.cleanScore.integerValue $visit.cleanScore "visits/$visitId.cleanScore"
    Assert-EqualValue $visitDocument.fields.sizeScore.integerValue $visit.sizeScore "visits/$visitId.sizeScore"
    Assert-EqualValue $visitDocument.fields.accessScore.integerValue $visit.accessScore "visits/$visitId.accessScore"
    Assert-EqualValue $visitDocument.fields.memo.stringValue $visit.memo "visits/$visitId.memo"

    $areaKey = Get-AreaKey -Prefecture $fixture.prefecture -City $fixture.city -Name $fixture.name
    $keyDocument = Get-DocumentById -Documents $keyDocuments -Collection "areaKeys" -DocumentId $areaKey
    if ($null -eq $keyDocument) {
        throw "Firestore area key verification failed: area=$areaId"
    }
    Assert-EqualValue $keyDocument.fields.areaId.integerValue $fixture.id "areaKeys/$areaKey.areaId"
}

$areaCounter = Get-DocumentById -Documents $counterDocuments -Collection "counters" -DocumentId "area"
$visitCounter = Get-DocumentById -Documents $counterDocuments -Collection "counters" -DocumentId "visit"
Assert-EqualValue $areaCounter.fields.lastId.integerValue 3L "counters/area.lastId"
Assert-EqualValue $visitCounter.fields.lastId.integerValue 3L "counters/visit.lastId"

if ($reportSnapshotAfter.Count -ne $reportSnapshot.Count -or $legacyReport -notin @($reportSnapshotAfter.name) -or $reportSnapshotAfterJson -ne $reportSnapshotJson) {
    throw "GCS report preservation verification failed."
}

Write-Host ""
Write-Host "Production Firestore restore completed."
Write-Host "  areas: $($areaDocuments.Count)"
Write-Host "  visits: $($visitDocuments.Count)"
Write-Host "  areaKeys: $($keyDocuments.Count)"
Write-Host "  counters: $($counterDocuments.Count)"
Write-Host "  GCS Markdown preserved: $($reportSnapshotAfter.Count)"
