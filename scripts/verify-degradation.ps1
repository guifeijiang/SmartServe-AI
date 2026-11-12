# 三级降级真实环境验证：只提问并打印 diagnostics，不做数据写入。
# 用法：pwsh -File .\scripts\verify-degradation.ps1 -Username <账号> -Password <密码> -Question "退款需要什么材料？"
param(
    [string]$Base = "http://127.0.0.1:8081",
    [long]$KnowledgeBaseId = 6,
    [string]$Username = "",
    [string]$Password = "",
    [string]$Question = "退款需要什么材料？"
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "请通过 -Username / -Password 传入验收账号，脚本不内置任何账号口令。"
}

function Invoke-Json {
    param([string]$Method, [string]$Url, [string]$Body = $null, [string]$Token = $null)
    $args = @("-s", "-S", "-X", $Method, $Url, "-H", "Accept: application/json")
    if ($Token) { $args += @("-H", "Authorization: Bearer $Token") }
    if ($null -ne $Body) {
        $tmp = Join-Path $env:TEMP ("req-" + [guid]::NewGuid().ToString("N") + ".json")
        [IO.File]::WriteAllText($tmp, $Body, (New-Object Text.UTF8Encoding($false)))
        $args += @("-H", "Content-Type: application/json; charset=utf-8", "--data-binary", "@$tmp")
    }
    $raw = & curl.exe @args
    if ($null -ne $Body) { Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue }
    if (-not $raw) { throw "空响应: $Method $Url" }
    return ($raw | ConvertFrom-Json)
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-Json -Method POST -Url "$Base/api/auth/login" -Body $loginBody
$askBody = @{ message = $Question } | ConvertTo-Json -Compress
$answer = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$KnowledgeBaseId/ask" -Body $askBody -Token $login.token

Write-Output ("question=" + $Question)
Write-Output ("answer=" + $answer.answer)
Write-Output ("diagnostics=" + ($answer.diagnostics | ConvertTo-Json -Compress))
Write-Output ("citations=" + ($answer.citations | Measure-Object).Count)
