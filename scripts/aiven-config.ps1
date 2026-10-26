# Reads local credentials without printing or embedding them in command arguments.
function Get-QuizAivenConfig {
    param([string]$ConfigPath = (Join-Path $PSScriptRoot '../数据库配置.md'))
    $values = @{}
    foreach ($line in (Get-Content -LiteralPath $ConfigPath -Encoding utf8)) {
        if ($line -match '^\s*(?:[-*]\s*)?([^:=：]+?)\s*[:=：]\s*(.*?)\s*$') {
            $key = $Matches[1].Trim()
            $value = $Matches[2].Trim()
            # Strip a matching display wrapper only. Trim(char[]) would also
            # remove legitimate, unmatched quote/backtick characters in a password.
            if ($value.Length -ge 2) {
                $first = $value[0]
                $last = $value[$value.Length - 1]
                if (($first -eq '"' -and $last -eq '"') -or ($first -eq '`' -and $last -eq '`')) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
            }
            $values[$key] = $value
        }
    }
    foreach ($key in @('Database name', 'Host', 'Port', 'User', 'Password')) {
        if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
            throw "数据库配置缺少字段：$key"
        }
    }
    if ($values['Database name'] -notmatch '^[a-zA-Z0-9_]+$') { throw '数据库名称格式不正确' }
    if ($values['Host'] -notmatch '^[a-zA-Z0-9.-]+$') { throw '数据库 Host 格式不正确' }
    $portNumber = 0
    if (-not [int]::TryParse($values['Port'], [ref]$portNumber) -or $portNumber -lt 1 -or $portNumber -gt 65535) { throw '数据库 Port 格式不正确' }
    return [pscustomobject]@{
        Host = $values['Host']
        Port = $portNumber
        Database = $values['Database name']
        Username = $values['User']
        Password = $values['Password']
        JdbcUrl = ('jdbc:mysql://{0}:{1}/{2}?sslMode=REQUIRED&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=15000&socketTimeout=30000' -f $values['Host'], $portNumber, $values['Database name'])
    }
}
