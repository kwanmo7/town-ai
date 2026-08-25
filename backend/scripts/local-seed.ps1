[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-LocalBaseUrl {
    param([string]$Url)

    try {
        $uri = [System.Uri]::new($Url)
    }
    catch {
        throw "BaseUrl 형식이 올바르지 않습니다: $Url"
    }

    if (-not $uri.IsLoopback) {
        throw "Local Seed는 localhost 또는 Loopback 주소에서만 실행할 수 있습니다: $Url"
    }
}

function Invoke-TownAiApi {
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
        Uri         = "$($BaseUrl.TrimEnd('/'))$Path"
        Method      = $Method
        TimeoutSec  = 30
        ErrorAction = "Stop"
    }

    if ($PSBoundParameters.ContainsKey("Body")) {
        $json = $Body | ConvertTo-Json -Depth 5 -Compress
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    $response = Invoke-WebRequest @parameters -UseBasicParsing

    if ($response.StatusCode -eq 204 -or $response.RawContentLength -eq 0) {
        return $null
    }

    $memory = [System.IO.MemoryStream]::new()
    try {
        $response.RawContentStream.CopyTo($memory)
        $content = [System.Text.Encoding]::UTF8.GetString($memory.ToArray())
    }
    finally {
        $memory.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }

    $parsed = $content | ConvertFrom-Json

    if ($parsed -is [System.Array]) {
        foreach ($item in $parsed) {
            Write-Output $item
        }
        return
    }

    return $parsed
}

function Test-SameVisit {
    param(
        [object]$Existing,
        [long]$AreaId,
        [hashtable]$Fixture
    )

    return $Existing.area.id -eq $AreaId `
        -and $Existing.visitDate -eq $Fixture.visitDate `
        -and $Existing.atmosphereScore -eq $Fixture.atmosphereScore `
        -and $Existing.infraScore -eq $Fixture.infraScore `
        -and $Existing.cleanScore -eq $Fixture.cleanScore `
        -and $Existing.sizeScore -eq $Fixture.sizeScore `
        -and $Existing.accessScore -eq $Fixture.accessScore
}

Assert-LocalBaseUrl -Url $BaseUrl

$fixtures = @(
    @{
        area = @{
            name       = "센터미나미"
            prefecture = "가나가와현"
            city       = "요코하마시 츠즈키구"
            station    = "센터미나미역"
        }
        visits = @(
            @{
                visitDate       = "2026-07-25"
                atmosphereScore = 8
                infraScore      = 9
                cleanScore      = 8
                sizeScore       = 7
                accessScore     = 7
                memo            = "역 앞 광장이 넓고 쇼핑 동선이 편했다. 주거 지역은 다음 방문에서 더 확인할 예정."
            },
            @{
                visitDate       = "2026-08-02"
                atmosphereScore = 9
                infraScore      = 9
                cleanScore      = 9
                sizeScore       = 8
                accessScore     = 7
                memo            = "주거 단지 쪽도 차분하고 정돈된 느낌이었다. 생활 편의성과 산책 환경이 좋았다."
            }
        )
    },
    @{
        area = @{
            name       = "무사시코스기"
            prefecture = "가나가와현"
            city       = "가와사키시 나카하라구"
            station    = "무사시코스기역"
        }
        visits = @(
            @{
                visitDate       = "2026-07-28"
                atmosphereScore = 7
                infraScore      = 9
                cleanScore      = 8
                sizeScore       = 6
                accessScore     = 9
                memo            = "교통과 상업 시설은 매우 편리했지만 역 주변의 밀도와 높은 건물이 조금 답답하게 느껴졌다."
            }
        )
    },
    @{
        area = @{
            name       = "키치조지"
            prefecture = "도쿄도"
            city       = "무사시노시"
            station    = "키치조지역"
        }
        visits = @(
            @{
                visitDate       = "2026-07-31"
                atmosphereScore = 9
                infraScore      = 8
                cleanScore      = 7
                sizeScore       = 6
                accessScore     = 8
                memo            = "공원과 상점가가 가까워 분위기가 좋았다. 주말 혼잡도와 주거 비용은 추가 확인이 필요하다."
            },
            @{
                visitDate       = "2026-08-08"
                atmosphereScore = 8
                infraScore      = 8
                cleanScore      = 8
                sizeScore       = 6
                accessScore     = 8
                memo            = "평일에는 주말보다 차분했다. 주요 지역 이동은 편하지만 넓은 집 선택지는 제한적으로 보였다."
            }
        )
    }
)

Write-Host "Town AI Local Seed를 시작합니다: $BaseUrl"

try {
    $health = Invoke-TownAiApi -Method GET -Path "/actuator/health/readiness"
}
catch {
    throw "Local Backend에 연결할 수 없습니다. Backend를 먼저 실행해주세요. 원인: $($_.Exception.Message)"
}

if ($health.status -ne "UP") {
    throw "Local Backend가 준비되지 않았습니다. readiness 상태: $($health.status)"
}

$existingAreas = @(Invoke-TownAiApi -Method GET -Path "/api/areas")
$existingVisits = @(Invoke-TownAiApi -Method GET -Path "/api/visits")
$createdAreaCount = 0
$createdVisitCount = 0
$skippedAreaCount = 0
$skippedVisitCount = 0

foreach ($fixture in $fixtures) {
    $areaFixture = $fixture.area
    $area = $existingAreas | Where-Object {
        $_.name -eq $areaFixture.name `
            -and $_.prefecture -eq $areaFixture.prefecture `
            -and $_.city -eq $areaFixture.city
    } | Select-Object -First 1

    if ($null -eq $area) {
        $area = Invoke-TownAiApi -Method POST -Path "/api/areas" -Body $areaFixture
        $existingAreas += $area
        $createdAreaCount++
        Write-Host "[생성] Area #$($area.id) $($area.name)"
    }
    else {
        $skippedAreaCount++
        Write-Host "[유지] Area #$($area.id) $($area.name)"
    }

    foreach ($visitFixture in $fixture.visits) {
        $sameVisit = $existingVisits | Where-Object {
            Test-SameVisit -Existing $_ -AreaId $area.id -Fixture $visitFixture
        } | Select-Object -First 1

        if ($null -ne $sameVisit) {
            $skippedVisitCount++
            Write-Host "  [유지] Visit #$($sameVisit.id) $($visitFixture.visitDate)"
            continue
        }

        $visitRequest = @{
            areaId          = $area.id
            visitDate       = $visitFixture.visitDate
            atmosphereScore = $visitFixture.atmosphereScore
            infraScore      = $visitFixture.infraScore
            cleanScore      = $visitFixture.cleanScore
            sizeScore       = $visitFixture.sizeScore
            accessScore     = $visitFixture.accessScore
            memo            = $visitFixture.memo
        }

        $visit = Invoke-TownAiApi -Method POST -Path "/api/visits" -Body $visitRequest
        $existingVisits += $visit
        $createdVisitCount++
        Write-Host "  [생성] Visit #$($visit.id) $($visitFixture.visitDate)"
    }
}

$statistics = Invoke-TownAiApi -Method GET -Path "/api/statistics"

Write-Host ""
Write-Host "Local Seed가 완료되었습니다."
Write-Host "  Area: 생성 $createdAreaCount, 유지 $skippedAreaCount, 전체 $($statistics.areaCount)"
Write-Host "  Visit: 생성 $createdVisitCount, 유지 $skippedVisitCount, 전체 $($statistics.visitCount)"
