param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot '../数据库配置.md'),
    [int]$Port = 8080,
    [string]$Address = '127.0.0.1',
    [string]$JarPath = (Join-Path $PSScriptRoot '../target/quiz-system-0.0.1-SNAPSHOT.jar')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'aiven-config.ps1')
$connection = Get-QuizAivenConfig -ConfigPath $ConfigPath
if (-not (Test-Path -LiteralPath $JarPath)) { throw '请先执行 mvn package 生成应用 JAR' }
$javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$oldUrl, $oldUsername, $oldPassword = $env:DB_URL, $env:DB_USERNAME, $env:DB_PASSWORD
try {
    $env:DB_URL = $connection.JdbcUrl
    $env:DB_USERNAME = $connection.Username
    $env:DB_PASSWORD = $connection.Password
    Write-Host "连接 Aiven 数据库 $($connection.Database)，使用 SSL；本地端口 $Port。"
    & $javaCommand -jar $JarPath --spring.profiles.active=prod "--server.address=$Address" "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "应用启动失败，退出码 $LASTEXITCODE" }
}
finally {
    $env:DB_URL, $env:DB_USERNAME, $env:DB_PASSWORD = $oldUrl, $oldUsername, $oldPassword
    $connection.Password = $null
}
