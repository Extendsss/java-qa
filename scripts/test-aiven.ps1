param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot '../数据库配置.md'),
    [string]$MavenCommand = 'mvn',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'aiven-config.ps1')
$connection = Get-QuizAivenConfig -ConfigPath $ConfigPath
$oldUrl, $oldUsername, $oldPassword = $env:DB_URL, $env:DB_USERNAME, $env:DB_PASSWORD
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mavenArguments = @('-B', 'test', '-Dquiz.test.profile=prod')
if ($Offline) { $mavenArguments += '-o' }
$localCache = Join-Path $projectDirectory '.maven-repository'
if (Test-Path -LiteralPath $localCache) { $mavenArguments += "-Dmaven.repo.local=$localCache" }
Push-Location $projectDirectory
try {
    $env:DB_URL = $connection.JdbcUrl
    $env:DB_USERNAME = $connection.Username
    $env:DB_PASSWORD = $connection.Password
    Write-Host "在 Aiven 数据库 $($connection.Database) 上执行测试（SSL）；测试实体将软删除，测试账号将禁用。"
    & $MavenCommand @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Aiven 测试未全部通过，Maven 退出码 $LASTEXITCODE" }
}
finally {
    Pop-Location
    $env:DB_URL, $env:DB_USERNAME, $env:DB_PASSWORD = $oldUrl, $oldUsername, $oldPassword
    $connection.Password = $null
}
