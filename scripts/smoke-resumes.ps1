param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = 'admin123'
)

$ErrorActionPreference = 'Stop'
$assertionCount = 0
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$createdResumes = [System.Collections.Generic.List[long]]::new()
$createdDefinitions = [System.Collections.Generic.List[long]]::new()
$createdUsers = [System.Collections.Generic.List[object]]::new()
$userTokens = [System.Collections.Generic.List[string]]::new()
$cleanupFailures = [System.Collections.Generic.List[string]]::new()
$cleanupAssertionCount = 0
$isCleaning = $false
$runFailure = $null
$skipReason = $null
$adminToken = $null
$adminId = $null

function Invoke-QuizApi {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = '', [int[]]$ExpectedCode = @(0))
    $request = @{ Method=$Method; Uri=($BaseUrl.TrimEnd('/') + '/api' + $Path); ContentType='application/json; charset=utf-8'; TimeoutSec=20 }
    if ($Token) { $request.Headers = @{satoken=$Token} }
    if ($null -ne $Body) { $request.Body = ($Body | ConvertTo-Json -Depth 30 -Compress) }
    $response = Invoke-RestMethod @request
    if ($response.code -notin $ExpectedCode) {
        throw "$Method $Path expected code $($ExpectedCode -join '/'), got $($response.code): $($response.message)"
    }
    if ($script:isCleaning) { $script:cleanupAssertionCount++ } else { $script:assertionCount++ }
    return $response.data
}

function Assert-Quiz {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
    if ($script:isCleaning) { $script:cleanupAssertionCount++ } else { $script:assertionCount++ }
}

function New-DefinitionBody {
    param([string]$Code, [int]$Repeatable=0, [int]$Enabled=1)
    return @{
        code=$Code; name="Smoke $Code"; repeatable=$Repeatable; enabled=$Enabled; sort=10
        fieldSchema=@(
            @{key='name';label='名称';type='text';required=$true},
            @{key='start';label='开始日期';type='date';required=$false},
            @{key='details';label='分条详情';type='list';required=$false},
            @{key='description';label='简介';type='textarea';required=$false}
        )
    }
}

function New-ValidContent {
    return @{ name='测试项目';start='2026-10';details=@('实现权限隔离','验证问题链');description='真实 MySQL 集成测试' }
}

function Test-DefinitionCapacity {
    $currentDefinitions = @(Invoke-QuizApi Get '/admin/block-defs' $null $adminToken)
    Assert-Quiz ($currentDefinitions.Count -le 20) 'Existing template count already exceeds the configured maximum'
    $toCapacity = 20 - $currentDefinitions.Count
    for ($index=0; $index -lt $toCapacity; $index++) {
        $capacity = Invoke-QuizApi Post '/admin/block-defs' (New-DefinitionBody "smoke_cap_${runId}_$index") $adminToken
        $createdDefinitions.Add([long]$capacity.id)
    }
    $null = Invoke-QuizApi Post '/admin/block-defs' (New-DefinitionBody "smoke_over_$runId") $adminToken 1006
    $atCapacity = @(Invoke-QuizApi Get '/admin/block-defs' $null $adminToken)
    Assert-Quiz ($atCapacity.Count -eq 20) 'Template capacity is not exactly 20'
    Write-Output 'Validated the 20-definition capacity using only definitions created by this run.'
}

try {
    $login = Invoke-QuizApi Post '/auth/login' @{username=$AdminUsername;password=$AdminPassword}
    $adminToken = $login.token
    $adminId = $login.id
    $baselineDefinitions = @(Invoke-QuizApi Get '/admin/block-defs' $null $adminToken)
    if ($baselineDefinitions.Count -gt 18) {
        $skipReason = "Full resume/schema suite requires two free definition slots; the database already has $($baselineDefinitions.Count)/20. Existing definitions were not modified."
        Test-DefinitionCapacity
    }
    else {
    $userA = Invoke-QuizApi Post '/admin/users' @{username="resume_a_$runId";password='smoke123';nickname='简历测试A';role='USER'} $adminToken
    $createdUsers.Add($userA)
    $userB = Invoke-QuizApi Post '/admin/users' @{username="resume_b_$runId";password='smoke123';nickname='简历测试B';role='USER'} $adminToken
    $createdUsers.Add($userB)
    $tokenA = (Invoke-QuizApi Post '/auth/login' @{username=$userA.username;password='smoke123'}).token
    $userTokens.Add($tokenA)
    $tokenB = (Invoke-QuizApi Post '/auth/login' @{username=$userB.username;password='smoke123'}).token
    $userTokens.Add($tokenB)
    $categories = @(Invoke-QuizApi Get '/categories' $null $tokenA)
    $categoryId = $categories[0].id
    Write-Output 'Authenticated admin and two isolated users.'

    $builtIn = $baselineDefinitions | Where-Object {$_.builtIn -eq 1} | Select-Object -First 1
    if ($builtIn) { $null = Invoke-QuizApi Delete "/admin/block-defs/$($builtIn.id)" $null $adminToken 403 }
    $null = Invoke-QuizApi Get '/admin/block-defs' $null $tokenA 403
    $singleBody = New-DefinitionBody "smoke_single_$runId" 0
    $multiBody = New-DefinitionBody "smoke_multi_$runId" 1
    $single = Invoke-QuizApi Post '/admin/block-defs' $singleBody $adminToken
    $createdDefinitions.Add([long]$single.id)
    $multi = Invoke-QuizApi Post '/admin/block-defs' $multiBody $adminToken
    $createdDefinitions.Add([long]$multi.id)
    $content = New-ValidContent
    $resume = Invoke-QuizApi Post '/resumes' @{title="Smoke resume $runId";blocks=@(@{blockDefId=$single.id;content=$content})} $tokenA
    $createdResumes.Add([long]$resume.id)
    Assert-Quiz ($resume.ownerId -eq $userA.id -and $resume.isPublic -eq 0 -and $resume.blockCount -eq 1) 'Private resume ownership or initial block count incorrect'
    $null = Invoke-QuizApi Get "/resumes/$($resume.id)" $null $tokenB 404
    $null = Invoke-QuizApi Get "/resumes/$($resume.id)/blocks" $null $tokenB 404
    $hidden = Invoke-QuizApi Get "/resumes?keyword=$runId" $null $tokenB
    Assert-Quiz ($hidden.total -eq 0) 'Private resume leaked into another user list'
    $blocks = @(Invoke-QuizApi Get "/resumes/$($resume.id)/blocks" $null $tokenA)
    $singleBlock = $blocks[0]
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$single.id;content=$content} $tokenA 1008
    $null = Invoke-QuizApi Delete "/admin/block-defs/$($single.id)" $null $adminToken 1007

    $bad = New-ValidContent; $bad.extra='not allowed'
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;content=$bad} $tokenA 400
    $bad = New-ValidContent; $bad.Remove('description')
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;content=$bad} $tokenA 400
    $bad = New-ValidContent; $bad.start='2026-02-30'
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;content=$bad} $tokenA 400
    $bad = New-ValidContent; $bad.name='  '
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;content=$bad} $tokenA 400
    $bad = New-ValidContent; $bad.details=@(1)
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;content=$bad} $tokenA 400
    $bad = New-ValidContent; $bad.details=@(1..31 | ForEach-Object {'条目'})
    $null = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;content=$bad} $tokenA 400
    $firstMulti = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;sort=1;content=$content} $tokenA
    $fullDateContent = New-ValidContent; $fullDateContent.start='2024-02-29'
    $secondMulti = Invoke-QuizApi Post "/resumes/$($resume.id)/blocks" @{blockDefId=$multi.id;sort=2;content=$fullDateContent} $tokenA
    $singleMode = New-DefinitionBody $multi.code 0
    $null = Invoke-QuizApi Put "/admin/block-defs/$($multi.id)" $singleMode $adminToken 400
    Write-Output 'Validated JSON fields, real dates, repeatability, and definition reference protection.'

    $singleBody.enabled=0
    $null = Invoke-QuizApi Put "/admin/block-defs/$($single.id)" $singleBody $adminToken
    $template = @(Invoke-QuizApi Get '/block-defs' $null $tokenA)
    Assert-Quiz (-not ($template | Where-Object {$_.id -eq $single.id})) 'Disabled definition remains in new-entry template'
    $blocks = @(Invoke-QuizApi Get "/resumes/$($resume.id)/blocks" $null $tokenA)
    Assert-Quiz ([bool]($blocks | Where-Object {$_.id -eq $singleBlock.id})) 'Disabling a definition hid existing content'
    $null = Invoke-QuizApi Put "/resumes/blocks/$($singleBlock.id)" @{content=$content;sort=0} $tokenA
    $other = Invoke-QuizApi Post '/resumes' @{title="Smoke other $runId"} $tokenA
    $createdResumes.Add([long]$other.id)
    $null = Invoke-QuizApi Post "/resumes/$($other.id)/blocks" @{blockDefId=$single.id;content=$content} $tokenA 400
    Write-Output 'Validated disabled templates preserve existing content and block new entries.'

    $questionBody = @{categoryId=$categoryId;type='QA';difficulty=2;title="Smoke $runId 简历项目的缓存策略";content='项目如何避免缓存不一致？';answer='按实际设计说明';resumeId=$resume.id;blockId=$firstMulti.id}
    $question = Invoke-QuizApi Post '/questions' $questionBody $tokenA
    Assert-Quiz ($question.ownerId -eq $userA.id -and $question.isPublic -eq 0 -and $question.resumeId -eq $resume.id) 'Resume question failed ownership inheritance'
    $followBody = @{categoryId=$categoryId;type='QA';difficulty=2;title="Smoke $runId 如何处理缓存穿透";content='继续解释具体实现';answer='布隆过滤器、空值缓存';parentId=$question.id}
    $follow = Invoke-QuizApi Post '/questions' $followBody $adminToken
    Assert-Quiz ($follow.ownerId -eq $userA.id -and $follow.isPublic -eq 0 -and $follow.rootId -eq $question.id -and $follow.resumeId -eq $resume.id) 'Admin follow-up failed to inherit private resume ownership'
    $null = Invoke-QuizApi Get "/questions/$($question.id)" $null $tokenB 404
    $cross = $questionBody.Clone(); $cross.resumeId=$other.id
    $null = Invoke-QuizApi Post '/questions' $cross $tokenA 400
    $cross = $followBody.Clone(); $cross.resumeId=$other.id
    $null = Invoke-QuizApi Post '/questions' $cross $tokenA 400
    $null = Invoke-QuizApi Put "/admin/questions/$($question.id)/public" @{isPublic=1} $adminToken 400
    $pool = @(Invoke-QuizApi Post '/practice/start' @{mode='ORDER';count=100} $tokenA)
    Assert-Quiz (-not ($pool | Where-Object {$_.resumeId -eq $resume.id})) 'Resume question leaked into ordinary practice pool'
    $ownPool = @(Invoke-QuizApi Post '/practice/start' @{mode='ORDER';resumeId=$resume.id;count=100} $tokenA)
    Assert-Quiz ($ownPool.Count -eq 2 -and -not ($ownPool | Where-Object {$_.answer -or $_.analysis})) 'Resume practice scope or answer suppression failed'

    $null = Invoke-QuizApi Put "/admin/resumes/$($resume.id)/public" @{isPublic=1} $adminToken
    $published = Invoke-QuizApi Get "/resumes/$($resume.id)" $null $tokenB
    Assert-Quiz ($published.ownerId -eq $userA.id -and $published.isPublic -eq 1) 'Publication changed resume owner'
    $publishedQuestion = Invoke-QuizApi Get "/questions/$($question.id)" $null $tokenB
    $publishedFollow = Invoke-QuizApi Get "/questions/$($follow.id)" $null $tokenB
    Assert-Quiz ($publishedQuestion.isPublic -eq 1 -and $publishedFollow.isPublic -eq 1 -and $publishedFollow.ownerId -eq $userA.id) 'Publication did not include every question and follow-up'
    $null = Invoke-QuizApi Put "/resumes/$($resume.id)" @{title='forbidden edit'} $tokenA 403
    $null = Invoke-QuizApi Put "/resumes/blocks/$($singleBlock.id)" @{content=$content} $tokenA 403
    $null = Invoke-QuizApi Delete "/resumes/blocks/$($firstMulti.id)" $null $tokenA 403
    $null = Invoke-QuizApi Post '/questions' $followBody $tokenA 403
    $null = Invoke-QuizApi Delete "/resumes/$($resume.id)" $null $tokenA 403
    $null = Invoke-QuizApi Put "/admin/resumes/$($resume.id)/public" @{isPublic=0} $adminToken
    $null = Invoke-QuizApi Get "/resumes/$($resume.id)" $null $tokenB 404
    $null = Invoke-QuizApi Get "/questions/$($follow.id)" $null $tokenB 404
    $null = Invoke-QuizApi Put "/resumes/$($resume.id)" @{title="Smoke restored $runId"} $tokenA
    $null = Invoke-QuizApi Delete "/resumes/blocks/$($firstMulti.id)" $null $tokenA
    $detached = Invoke-QuizApi Get "/questions/$($question.id)" $null $tokenA
    Assert-Quiz ($null -eq $detached.blockId) 'Deleted resume block left a dangling question association'
    Write-Output 'Validated resume isolation, question inheritance, practice scope, publication, and restored editing rights.'

    Test-DefinitionCapacity

    $null = Invoke-QuizApi Delete "/resumes/$($resume.id)" $null $tokenA
    $null = Invoke-QuizApi Get "/resumes/$($resume.id)" $null $adminToken 404
    $null = Invoke-QuizApi Get "/questions/$($question.id)" $null $adminToken 404
    $null = Invoke-QuizApi Get "/questions/$($follow.id)" $null $adminToken 404
    }
}
catch {
    $runFailure = $_
}
finally {
    $isCleaning = $true
    if ($adminToken) {
        foreach ($resumeId in $createdResumes) {
            try {
                $null = Invoke-QuizApi Delete "/admin/resumes/$resumeId" $null $adminToken @(0,404)
                $null = Invoke-QuizApi Get "/resumes/$resumeId" $null $adminToken 404
            }
            catch { $cleanupFailures.Add("Resume $resumeId cleanup: $($_.Exception.Message)") }
        }
        foreach ($definitionId in $createdDefinitions) {
            try { $null = Invoke-QuizApi Delete "/admin/block-defs/$definitionId" $null $adminToken @(0,404) }
            catch { $cleanupFailures.Add("Definition $definitionId cleanup: $($_.Exception.Message)") }
        }
        foreach ($createdUser in $createdUsers) {
            try {
                Assert-Quiz ($createdUser.id -ne $adminId -and $createdUser.username -in @("resume_a_$runId","resume_b_$runId")) 'Refusing to disable a user that this run did not create'
                $disabled = Invoke-QuizApi Put "/admin/users/$($createdUser.id)" @{nickname=$createdUser.nickname;role='USER';status=0} $adminToken
                Assert-Quiz ($disabled.id -eq $createdUser.id -and $disabled.status -eq 0) 'Test account was not disabled'
            }
            catch { $cleanupFailures.Add("Test user $($createdUser.id) cleanup: $($_.Exception.Message)") }
        }
        foreach ($testToken in $userTokens) {
            try {
                $null = Invoke-QuizApi Post '/auth/logout' $null $testToken @(0,401)
                $null = Invoke-QuizApi Get '/auth/me' $null $testToken 401
            }
            catch { $cleanupFailures.Add("Test session cleanup: $($_.Exception.Message)") }
        }
        if ($createdDefinitions.Count -gt 0) {
            try {
                $remaining = @(Invoke-QuizApi Get '/admin/block-defs' $null $adminToken)
                Assert-Quiz (-not ($remaining | Where-Object { $createdDefinitions.Contains([long]$_.id) })) 'Definitions created by this run remain active after cleanup'
            }
            catch { $cleanupFailures.Add("Definition cleanup verification: $($_.Exception.Message)") }
        }
        try { $null = Invoke-QuizApi Post '/auth/logout' $null $adminToken @(0,401) }
        catch { $cleanupFailures.Add("Admin test-session logout: $($_.Exception.Message)") }
    }
    if ($cleanupFailures.Count -eq 0) {
        Write-Output "Cleanup verified: $($createdResumes.Count) test resumes and $($createdDefinitions.Count) test definitions soft-deleted; $($createdUsers.Count) test users disabled; test sessions invalidated ($cleanupAssertionCount cleanup assertions)."
    }
    else {
        foreach ($cleanupFailure in $cleanupFailures) { Write-Warning $cleanupFailure }
    }
}

if ($runFailure) { throw $runFailure }
if ($cleanupFailures.Count -gt 0) { throw "Smoke cleanup failed for $($cleanupFailures.Count) operation(s); see warnings above. Only IDs created by run $runId were targeted." }
if ($skipReason) { Write-Output "PARTIAL: $assertionCount capacity assertions passed; SKIP: $skipReason (run $runId)." }
else { Write-Output "PASS: $assertionCount resume/template API assertions and $cleanupAssertionCount cleanup assertions against $BaseUrl (run $runId)." }
