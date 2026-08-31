Set-StrictMode -Version Latest

function Test-ReportCleanupObjectName {
    param([Parameter(Mandatory)][string]$Name)

    return $Name -cmatch `
        '\Areports/v1/(area|compare|summary|all)/[^/\\]+\.md\z'
}

function Assert-ReportCleanupScope {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ProjectId,

        [Parameter(Mandatory)]
        [string]$DatabaseId,

        [Parameter(Mandatory)]
        [string]$ReportBucket,

        [Parameter(Mandatory)]
        [string]$AllowedPrefix
    )

    if ($ProjectId -ne "town-ai" -or $DatabaseId -ne "town-ai") {
        throw "Report cleanup can target only the town-ai/town-ai Firestore database."
    }
    if ($ReportBucket -ne "town_ai") {
        throw "Report cleanup can target only the town_ai bucket."
    }
    if ($AllowedPrefix -ne "reports/v1/") {
        throw "Report cleanup can target only the reports/v1/ prefix."
    }
}

function Assert-ReportCleanupMode {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [bool]$DeleteOrphans,

        [Parameter(Mandatory)]
        [bool]$ConfirmProductionCleanup
    )

    if ($DeleteOrphans -and -not $ConfirmProductionCleanup) {
        throw "Deleting orphan reports requires -ConfirmProductionCleanup."
    }
    if (-not $DeleteOrphans -and $ConfirmProductionCleanup) {
        throw "-ConfirmProductionCleanup is valid only with -DeleteOrphans."
    }
}

function Select-ReportOrphanCandidates {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [object[]]$StorageObjects,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [string[]]$ReferencedPaths,

        [Parameter(Mandatory)]
        [DateTimeOffset]$CutoffUtc,

        [Parameter()]
        [string]$AllowedPrefix = "reports/v1/"
    )

    if ($AllowedPrefix -ne "reports/v1/") {
        throw "Candidate selection can target only the reports/v1/ prefix."
    }

    $references = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($path in $ReferencedPaths) {
        if (-not [string]::IsNullOrWhiteSpace($path)) {
            $null = $references.Add($path)
        }
    }

    $eligible = [System.Collections.Generic.List[object]]::new()
    $recent = [System.Collections.Generic.List[object]]::new()
    $ignored = [System.Collections.Generic.List[object]]::new()

    foreach ($storageObject in $StorageObjects) {
        if ($null -eq $storageObject) {
            continue
        }

        $name = [string]$storageObject.name
        $isAllowedReport =
            -not [string]::IsNullOrWhiteSpace($name) -and
            (Test-ReportCleanupObjectName -Name $name)
        if (-not $isAllowedReport) {
            $ignored.Add($storageObject)
            continue
        }
        if ($references.Contains($name)) {
            continue
        }

        $updatedAt = [DateTimeOffset]::Parse(
            [string]$storageObject.updated,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::AssumeUniversal
        ).ToUniversalTime()
        $candidate = [pscustomobject]@{
            name       = $name
            generation = [string]$storageObject.generation
            size       = [long]$storageObject.size
            updated    = $updatedAt
        }

        if ($updatedAt -le $CutoffUtc.ToUniversalTime()) {
            $eligible.Add($candidate)
        }
        else {
            $recent.Add($candidate)
        }
    }

    return [pscustomobject]@{
        Eligible = @($eligible | Sort-Object name)
        Recent   = @($recent | Sort-Object name)
        Ignored  = @($ignored)
    }
}

function Get-ReportCleanupHttpStatusCode {
    param([Parameter(Mandatory)][object]$Exception)

    if ($null -ne $Exception.Data -and $Exception.Data.Contains("StatusCode")) {
        return [int]$Exception.Data["StatusCode"]
    }

    $responseProperty = $Exception.PSObject.Properties["Response"]
    if ($null -eq $responseProperty -or $null -eq $responseProperty.Value) {
        return $null
    }
    $statusProperty = $responseProperty.Value.PSObject.Properties["StatusCode"]
    if ($null -eq $statusProperty -or $null -eq $statusProperty.Value) {
        return $null
    }
    return [int]$statusProperty.Value
}

function Remove-ReportOrphanObject {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [object]$Candidate,

        [Parameter(Mandatory)]
        [string]$ReportBucket,

        [Parameter(Mandatory)]
        [string]$AccessToken,

        [Parameter()]
        [scriptblock]$RequestInvoker
    )

    if ($ReportBucket -ne "town_ai") {
        throw "Orphan deletion can target only the town_ai bucket."
    }
    if ([string]::IsNullOrWhiteSpace($AccessToken)) {
        throw "A Google Cloud access token is required."
    }

    $name = [string]$Candidate.name
    $generation = [string]$Candidate.generation
    if (-not (Test-ReportCleanupObjectName -Name $name)) {
        throw "Refusing to delete an object outside the V1 Report type paths."
    }
    if ([string]::IsNullOrWhiteSpace($generation)) {
        throw "Refusing to delete an object without a generation precondition."
    }

    if ($null -eq $RequestInvoker) {
        $RequestInvoker = {
            param([string]$Uri, [hashtable]$Headers)
            Invoke-RestMethod `
                -Method DELETE `
                -Uri $Uri `
                -Headers $Headers `
                -TimeoutSec 60 `
                -ErrorAction Stop
        }
    }

    $escapedBucket = [System.Uri]::EscapeDataString($ReportBucket)
    $escapedObject = [System.Uri]::EscapeDataString($name)
    $escapedGeneration = [System.Uri]::EscapeDataString($generation)
    $uri = "https://storage.googleapis.com/storage/v1/b/$escapedBucket/o/${escapedObject}?ifGenerationMatch=$escapedGeneration"

    try {
        $null = & $RequestInvoker `
            $uri `
            @{ Authorization = "Bearer $AccessToken" }
        return "DELETED"
    }
    catch {
        if ((Get-ReportCleanupHttpStatusCode $_.Exception) -eq 404) {
            return "ALREADY_MISSING"
        }
        throw
    }
}

Export-ModuleMember -Function @(
    "Assert-ReportCleanupScope",
    "Assert-ReportCleanupMode",
    "Select-ReportOrphanCandidates",
    "Remove-ReportOrphanObject"
)
