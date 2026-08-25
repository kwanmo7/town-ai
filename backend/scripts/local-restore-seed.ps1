[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseHost = "127.0.0.1",

    [Parameter()]
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 3306,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseName = "town_ai",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DatabaseUsername = "root",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$MySqlPath = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
)

$ErrorActionPreference = "Stop"

function Assert-LocalDatabaseHost {
    param([string]$HostName)

    $localHosts = @("localhost", "127.0.0.1", "::1")
    if ($localHosts -notcontains $HostName.ToLowerInvariant()) {
        throw "Local Seed 복구는 Local MySQL에서만 실행할 수 있습니다: $HostName"
    }
}

Assert-LocalDatabaseHost -HostName $DatabaseHost

if (-not (Test-Path -LiteralPath $MySqlPath -PathType Leaf)) {
    throw "MySQL Client를 찾을 수 없습니다: $MySqlPath"
}

$sqlPath = Join-Path $PSScriptRoot "local-restore-seed.sql"
if (-not (Test-Path -LiteralPath $sqlPath -PathType Leaf)) {
    throw "복구 SQL을 찾을 수 없습니다: $sqlPath"
}

$sourcePath = $sqlPath.Replace("\", "/")

Write-Host "Town AI Local Seed Area를 복구합니다."
Write-Host "  Database: $DatabaseHost`:$DatabasePort/$DatabaseName"
Write-Host "MySQL 비밀번호 Prompt에는 Local Database 비밀번호를 입력해주세요."

& $MySqlPath `
    "--host=$DatabaseHost" `
    "--port=$DatabasePort" `
    "--user=$DatabaseUsername" `
    "--password" `
    "--database=$DatabaseName" `
    "--default-character-set=utf8mb4" `
    "--execute=SOURCE $sourcePath;"

if ($LASTEXITCODE -ne 0) {
    throw "Local Seed Area 복구에 실패했습니다. MySQL 종료 코드: $LASTEXITCODE"
}

Write-Host "Local Seed Area 복구가 완료되었습니다."
Write-Host "Backend가 실행 중이면 화면을 새로고침하거나 local-seed.ps1을 다시 실행해주세요."
