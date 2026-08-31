[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$modulePath = Join-Path `
    (Join-Path $PSScriptRoot "modules") `
    "ReportOrphanCleanup.psm1"
Import-Module $modulePath -Force

$failureCount = 0

function Assert-Equal {
    param(
        [Parameter()][AllowNull()][object]$Actual,
        [Parameter()][AllowNull()][object]$Expected,
        [Parameter(Mandatory)][string]$Message
    )

    if ($Actual -ne $Expected) {
        Write-Error "$Message (actual='$Actual', expected='$Expected')" `
            -ErrorAction Continue
        $script:failureCount++
    }
}

function Assert-Throws {
    param(
        [Parameter(Mandatory)][scriptblock]$Action,
        [Parameter(Mandatory)][string]$Message
    )

    try {
        & $Action
        Write-Error "$Message (no exception was thrown)" -ErrorAction Continue
        $script:failureCount++
    }
    catch {
        # Expected failure proves the production safety guard is active.
    }
}

Assert-ReportCleanupScope `
    -ProjectId "town-ai" `
    -DatabaseId "town-ai" `
    -ReportBucket "town_ai" `
    -AllowedPrefix "reports/v1/"
Assert-Throws `
    -Action {
        Assert-ReportCleanupScope `
            -ProjectId "other-project" `
            -DatabaseId "town-ai" `
            -ReportBucket "town_ai" `
            -AllowedPrefix "reports/v1/"
    } `
    -Message "A different GCP project must be rejected."
Assert-Throws `
    -Action {
        Assert-ReportCleanupScope `
            -ProjectId "town-ai" `
            -DatabaseId "town-ai" `
            -ReportBucket "town_ai" `
            -AllowedPrefix "reports/"
    } `
    -Message "A broad GCS prefix must be rejected."

Assert-ReportCleanupMode `
    -DeleteOrphans $false `
    -ConfirmProductionCleanup $false
Assert-ReportCleanupMode `
    -DeleteOrphans $true `
    -ConfirmProductionCleanup $true
Assert-Throws `
    -Action {
        Assert-ReportCleanupMode `
            -DeleteOrphans $true `
            -ConfirmProductionCleanup $false
    } `
    -Message "Delete mode without confirmation must be rejected."

$cutoff = [DateTimeOffset]::Parse("2026-08-30T00:00:00Z")
$objects = @(
    [pscustomobject]@{
        name = "reports/v1/area/referenced.md"
        generation = "1"
        size = "100"
        updated = "2026-08-01T00:00:00Z"
    },
    [pscustomobject]@{
        name = "reports/v1/all/eligible.md"
        generation = "2"
        size = "200"
        updated = "2026-08-29T23:59:59Z"
    },
    [pscustomobject]@{
        name = "reports/v1/summary/boundary.md"
        generation = "3"
        size = "300"
        updated = "2026-08-30T00:00:00Z"
    },
    [pscustomobject]@{
        name = "reports/v1/compare/recent.md"
        generation = "4"
        size = "400"
        updated = "2026-08-30T00:00:01Z"
    },
    [pscustomobject]@{
        name = "reports/v1/all/not-markdown.txt"
        generation = "5"
        size = "500"
        updated = "2026-08-01T00:00:00Z"
    },
    [pscustomobject]@{
        name = "private/outside.md"
        generation = "6"
        size = "600"
        updated = "2026-08-01T00:00:00Z"
    },
    [pscustomobject]@{
        name = "reports/v1/area/nested/file.md"
        generation = "7"
        size = "700"
        updated = "2026-08-01T00:00:00Z"
    },
    [pscustomobject]@{
        name = "reports/v1/../private.md"
        generation = "8"
        size = "800"
        updated = "2026-08-01T00:00:00Z"
    }
)

$selection = Select-ReportOrphanCandidates `
    -StorageObjects $objects `
    -ReferencedPaths @("reports/v1/area/referenced.md") `
    -CutoffUtc $cutoff

Assert-Equal $selection.Eligible.Count 2 `
    "Only old, unreferenced Markdown objects should be eligible."
Assert-Equal $selection.Eligible[0].name "reports/v1/all/eligible.md" `
    "Eligible objects should be sorted by path."
Assert-Equal $selection.Eligible[1].name "reports/v1/summary/boundary.md" `
    "An object exactly at the cutoff should be eligible."
Assert-Equal $selection.Recent.Count 1 `
    "A recent unreferenced object should be preserved."
Assert-Equal $selection.Recent[0].name "reports/v1/compare/recent.md" `
    "The recent object should be reported separately."
Assert-Equal $selection.Ignored.Count 4 `
    "Objects outside the allowed Markdown scope should be ignored."

$script:capturedDeleteUri = $null
$script:capturedAuthorization = $null
$deleteResult = Remove-ReportOrphanObject `
    -Candidate $selection.Eligible[0] `
    -ReportBucket "town_ai" `
    -AccessToken "test-token" `
    -RequestInvoker {
        param([string]$Uri, [hashtable]$Headers)
        $script:capturedDeleteUri = $Uri
        $script:capturedAuthorization = $Headers.Authorization
    }
Assert-Equal $deleteResult "DELETED" `
    "A successful delete request should be reported."
Assert-Equal $capturedAuthorization "Bearer test-token" `
    "The delete request should use the supplied access token."
if ($capturedDeleteUri -notmatch "ifGenerationMatch=2$") {
    Write-Error "The delete request must include the scanned generation precondition." `
        -ErrorAction Continue
    $failureCount++
}

$missingResult = Remove-ReportOrphanObject `
    -Candidate $selection.Eligible[0] `
    -ReportBucket "town_ai" `
    -AccessToken "test-token" `
    -RequestInvoker {
        param([string]$Uri, [hashtable]$Headers)
        $exception = [System.Exception]::new("not found")
        $exception.Data["StatusCode"] = 404
        throw $exception
    }
Assert-Equal $missingResult "ALREADY_MISSING" `
    "Deleting an already missing object should be idempotent."

$outsideCandidate = [pscustomobject]@{
    name = "private/outside.md"
    generation = "7"
}
Assert-Throws `
    -Action {
        Remove-ReportOrphanObject `
            -Candidate $outsideCandidate `
            -ReportBucket "town_ai" `
            -AccessToken "test-token" `
            -RequestInvoker { throw "must not be called" }
    } `
    -Message "Deletion outside the V1 Report type paths must be rejected."

if ($failureCount -gt 0) {
    throw "Report orphan cleanup tests failed: $failureCount failure(s)."
}

Write-Host "Report orphan cleanup tests passed."
