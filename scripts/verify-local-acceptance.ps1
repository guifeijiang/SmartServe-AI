# SmartServe AI 本地真实环境验收脚本（RAG + 工单 + RocketMQ）
# 前置：后端以 local profile 启动在 8081；MySQL/Ollama/Redis 状态由环境决定；RocketMQ 可选。
# 用法：pwsh -File .\scripts\verify-local-acceptance.ps1 -Username <验收账号> -Password <密码>
param(
    [string]$Base = "http://127.0.0.1:8081",
    [string]$Username = "",
    [string]$Password = "",
    [string]$OutDir = (Join-Path (Split-Path -Parent $PSScriptRoot) "logs\acceptance")
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "请通过 -Username / -Password 传入验收账号，脚本不内置任何账号口令。"
}
if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [string]$Token = $null
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $args = @("-s", "-S", "-X", $Method, $Url, "-H", "Accept: application/json")
    foreach ($key in $headers.Keys) { $args += @("-H", "$key`: $($headers[$key])") }
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

function Save-Json {
    param([string]$Name, $Object)
    $path = Join-Path $OutDir $Name
    [IO.File]::WriteAllText($path, ($Object | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
    Write-Output "saved: $path"
}

# 1. 健康检查
$health = Invoke-Json -Method GET -Url "$Base/api/health"
Write-Output ("health=" + ($health | ConvertTo-Json -Compress))

# 2. 注册（已存在则忽略）并登录
$registerBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
try { Invoke-Json -Method POST -Url "$Base/api/auth/register" -Body $registerBody | Out-Null; Write-Output "register=created" }
catch { Write-Output "register=skipped($($_.Exception.Message))" }
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-Json -Method POST -Url "$Base/api/auth/login" -Body $loginBody
$token = $login.token
if (-not $token) { throw "登录失败：未返回 token" }
$me = Invoke-Json -Method GET -Url "$Base/api/users/me" -Token $token
Write-Output ("login=ok userId=" + $me.id + " role=" + $me.role + " username=" + $me.username)

# 3. 创建知识库（同名已存在时复用，避免重复造数据）
$existing = Invoke-Json -Method GET -Url "$Base/api/knowledge-bases" -Token $token
$kbBody = @{ name = "RAG验收知识库-Sample"; description = "验收用：潮汐 X9 咖啡机售后政策" } | ConvertTo-Json -Compress
$kb = $existing | Where-Object { $_.name -eq "RAG验收知识库-Sample" } | Select-Object -First 1
if ($null -eq $kb) {
    $kb = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases" -Body $kbBody -Token $token
    Write-Output ("knowledgeBase=new id=" + $kb.id)
} else {
    Write-Output ("knowledgeBase=reuse id=" + $kb.id)
}
Save-Json -Name "01-knowledge-base.json" -Object $kb

# 4. 上传文档（multipart）；同名文档已存在且已处理则复用，避免重复造数据
$docPath = Join-Path $PSScriptRoot "acceptance-refund-policy.txt"
if (-not (Test-Path -LiteralPath $docPath)) { throw "缺少验收文档: $docPath" }
$docs = Invoke-Json -Method GET -Url "$Base/api/knowledge-bases/$($kb.id)/documents" -Token $token
$doc = $docs | Where-Object { $_.originalFilename -eq "acceptance-refund-policy.txt" -and $_.processStatus -eq "READY" } | Select-Object -Last 1
if ($null -eq $doc) {
    $upload = & curl.exe -s -S -X POST "$Base/api/knowledge-bases/$($kb.id)/documents" `
        -H "Authorization: Bearer $token" `
        -F "file=@$docPath;type=text/plain"
    $doc = $upload | ConvertFrom-Json
    Write-Output ("document=new id=" + $doc.id + " status=" + $doc.processStatus)
} else {
    Write-Output ("document=reuse id=" + $doc.id + " status=" + $doc.processStatus)
}
Save-Json -Name "02-document-upload.json" -Object $doc

# 5. 处理文档（解析 -> 500/80 切块 -> 向量 -> 索引同步）；已 READY 则跳过重复处理
if ($doc.processStatus -ne "READY") {
    $processed = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$($kb.id)/documents/$($doc.id)/process" -Token $token
    Write-Output ("process status=" + $processed.processStatus + " chunkCount=" + $processed.chunkCount)
    Save-Json -Name "03-document-process.json" -Object $processed
} else {
    Write-Output ("process=skipped(already READY) chunkCount=" + $doc.chunkCount)
    Save-Json -Name "03-document-process.json" -Object $doc
}

# 6. 单轮问答（不建会话）
$askBody = @{ message = "这项售后政策的专属测试编号是什么？退款需要哪些材料？" } | ConvertTo-Json -Compress
$answer1 = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$($kb.id)/ask" -Body $askBody -Token $token
Write-Output ("ask1 answer=" + $answer1.answer)
Write-Output ("ask1 diagnostics=" + ($answer1.diagnostics | ConvertTo-Json -Compress))
Save-Json -Name "04-ask-single.json" -Object $answer1

# 7. 多轮问答（sessionId + 最近 10 条历史）：
#    第一轮问专属编号，第二轮用「刚才说的…」追问，验证历史与指代改写
$session = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$($kb.id)/sessions" -Body "{}" -Token $token
Write-Output ("sessionId=" + $session.id)
$askBody2 = @{ message = "这项售后政策的专属测试编号是什么？"; sessionId = $session.id } | ConvertTo-Json -Compress
$answer2 = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$($kb.id)/ask" -Body $askBody2 -Token $token
Write-Output ("ask2 answer=" + $answer2.answer)
Write-Output ("ask2 diagnostics=" + ($answer2.diagnostics | ConvertTo-Json -Compress))
Save-Json -Name "05-ask-session-turn1.json" -Object $answer2

$askBody3 = @{ message = "刚才说的专属测试编号是多少？"; sessionId = $session.id } | ConvertTo-Json -Compress
$answer3 = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$($kb.id)/ask" -Body $askBody3 -Token $token
Write-Output ("ask3 answer=" + $answer3.answer)
Write-Output ("ask3 diagnostics=" + ($answer3.diagnostics | ConvertTo-Json -Compress))
Save-Json -Name "06-ask-session-turn2.json" -Object $answer3

# 8. 重建向量索引（用 MySQL 已存向量，不调用 Embedding）
$reindex = Invoke-Json -Method POST -Url "$Base/api/knowledge-bases/$($kb.id)/reindex" -Token $token
Write-Output ("reindex=" + ($reindex | ConvertTo-Json -Compress))
Save-Json -Name "07-reindex.json" -Object $reindex

# 9. 工单创建（触发 Outbox -> RocketMQ -> 消费 -> 通知），最多轮询 30 秒
$ticketBody = @{
    title = "验收工单-Sample"
    description = "验收脚本创建的工单，用于验证 Outbox 与 RocketMQ 通知链路"
    category = "AFTER_SALE"
    priority = "MEDIUM"
} | ConvertTo-Json -Compress
$ticket = Invoke-Json -Method POST -Url "$Base/api/tickets" -Body $ticketBody -Token $token
Write-Output ("ticketId=" + $ticket.id + " status=" + $ticket.status)
Save-Json -Name "08-ticket-create.json" -Object $ticket

$notifications = $null
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 3
    $notifications = Invoke-Json -Method GET -Url "$Base/api/notifications/me" -Token $token
    if ($notifications.total -gt 0 -and ($notifications.items | Where-Object { $_.ticketId -eq $ticket.id })) {
        Write-Output ("notification matched after " + (($i + 1) * 3) + "s")
        break
    }
}
Write-Output ("notifications=" + ($notifications | ConvertTo-Json -Depth 6 -Compress))
Save-Json -Name "09-notifications.json" -Object $notifications

Write-Output "done"
