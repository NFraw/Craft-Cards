param(
    [ValidateSet('neoforge','fabric','forge')][string[]]$Loaders = @('neoforge','fabric','forge'),
    [string[]]$Versions = @('1.21.1','1.21.2','1.21.3','1.21.4','1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10','1.21.11'),
    [ValidateSet('normal')][string]$Scenario = 'normal',
    [switch]$CompileOnly,
    [switch]$Fullscreen,
    [switch]$ForgeOffline,
    [switch]$StopOnFailure,
    [ValidateRange(1,120)][int]$TimeoutMinutes = 25,
    [string]$Jdk8Path
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$targets = @(Get-Content (Join-Path $repo 'versions/targets.json') -Raw | ConvertFrom-Json)
if (!$Jdk8Path) {
    $localJdk = Join-Path $repo 'build/modern-port/temurin8-path.txt'
    if (Test-Path -LiteralPath $localJdk) { $Jdk8Path = (Get-Content -LiteralPath $localJdk -Raw).Trim() }
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$batchId = "$stamp-$([guid]::NewGuid().ToString('N').Substring(0,8))"
$output = Join-Path $repo "build/acceptance-matrix/$batchId"
New-Item -ItemType Directory -Path $output -Force | Out-Null
$results = [Collections.Generic.List[object]]::new()
$manifestPath = Join-Path $repo 'build/releases/manifest.json'
$manifest = if (Test-Path -LiteralPath $manifestPath) { @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json) } else { @() }
$failed = $false

function Write-Report {
    $summary = [ordered]@{
        batchId = $batchId
        scenario = $Scenario
        compileOnly = [bool]$CompileOnly
        fullscreen = [bool]$Fullscreen
        forgeOffline = [bool]$ForgeOffline
        started = $stamp
        generatedAt = (Get-Date).ToString('o')
        totals = [ordered]@{
            pass = @($results | Where-Object status -EQ 'PASS').Count
            fail = @($results | Where-Object status -EQ 'FAIL').Count
            timeout = @($results | Where-Object status -EQ 'TIMEOUT').Count
            notRun = @($results | Where-Object status -EQ 'NOT_RUN').Count
            unsupported = @($results | Where-Object status -EQ 'UNSUPPORTED').Count
        }
        results = $results.ToArray()
    }
    $tmp = Join-Path $output 'results.tmp.json'
    $summary | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $tmp -Encoding utf8
    Move-Item -LiteralPath $tmp -Destination (Join-Path $output 'results.json') -Force
    $lines = @('# Crafty Cards 自动验收结果', '', "批次：$batchId；场景：$Scenario；只编译：$([bool]$CompileOnly)。", '',
        '| Minecraft | 加载器 | 状态 | 用时 | 结果 |', '| --- | --- | --- | --- | --- |')
    foreach ($row in $results) {
        $detail = if ($row.reason) { $row.reason.Replace('|','/').Replace("`n",' ') } else { '—' }
        $lines += "| $($row.minecraft) | $($row.loader) | $($row.status) | $($row.seconds)s | $detail |"
    }
    $lines += @('', 'PASS = 三人真实联机流程和四份结果、截图检查通过；仍未人工复核 HUD 和实际听音。',
        'NOT_RUN = 仅编译/配置通过，没有启动游戏；UNSUPPORTED = 官方未提供该组合。',
        '日志、四份结果、截图与失败原因保存在本批次目录及各版本的 build/poc/<runId>/。')
    $lines | Set-Content -LiteralPath (Join-Path $output 'README.md') -Encoding utf8
}

:versionsLoop foreach ($version in $Versions) {
    $target = $targets | Where-Object Minecraft -EQ $version | Select-Object -First 1
    if (!$target -and $version -eq '1.21.1') { $target = [pscustomobject]@{ Minecraft='1.21.1'; Forge='52.1.0' } }
    if (!$target) { throw "Version not registered: $version" }
    foreach ($loader in $Loaders) {
        if ($loader -eq 'forge' -and !$target.Forge) {
            $results.Add([ordered]@{minecraft=$version;loader=$loader;status='UNSUPPORTED';seconds=0;reason='Forge official release unavailable';runtime='not-run'})
            Write-Report
            continue
        }
        $runId = "$batchId-$version-$loader".Replace('.','_')
        $wrapper = if ($loader -eq 'fabric' -and $version -eq '1.21.1') { Join-Path $repo 'fabric/gradlew.bat' }
            elseif ($loader -eq 'fabric') { Join-Path $repo 'versions/gradlew.bat' }
            elseif ($loader -eq 'forge' -and $version -eq '1.21.11') { Join-Path $repo 'versions/forge-toolchain/gradlew.bat' }
            else { Join-Path $repo 'gradlew.bat' }
        $project = if ($loader -eq 'neoforge') { $repo } else { Join-Path $repo $loader }
        $caseLog = Join-Path $output "$version-$loader.log"
        $runOutput = Join-Path $project "build/mc-$version/poc/$runId"
        if ($version -eq '1.21.1') { $runOutput = Join-Path $project "build/poc/$runId" }
        $entry = $manifest | Where-Object { $_.minecraft -eq $version -and $_.loader -eq $loader } | Select-Object -First 1
        $row = [ordered]@{
            minecraft=$version;loader=$loader;scenario=$(if ($Fullscreen) { "$Scenario/fullscreen" } else { $Scenario });status='NOT_RUN';seconds=0
            reason='Pending';runtime=$(if ($CompileOnly) {'not-run'} else {'development'});runId=$runId;log=$caseLog;detailReport=(Join-Path $runOutput 'result.json')
            releaseJarSha256=if ($entry) { $entry.sha256 } else { $null }
        }
        $startedAt = Get-Date
        $process = $null
        $outFile = $null
        $errFile = $null
        try {
            if (!$CompileOnly) {
                $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,25576)
                try { $listener.Start() } finally { $listener.Stop() }
            }
            $info = [Diagnostics.ProcessStartInfo]::new()
            $info.FileName = Join-Path $PSHOME 'pwsh.exe'
            $info.WorkingDirectory = $repo
            foreach ($argument in @('-NoProfile','-NonInteractive','-File',(Join-Path $PSScriptRoot 'run-acceptance-case.ps1'),
                '-Wrapper',$wrapper,'-ProjectDir',$project,'-Minecraft',$version,'-RunId',$runId,
                '-Task',$(if ($CompileOnly) {'pocSmokeConfig'} else {'pocSmoke'}))) { $info.ArgumentList.Add([string]$argument) }
            if ($Fullscreen) { $info.ArgumentList.Add('-Fullscreen') }
            if ($ForgeOffline -and $loader -eq 'forge') { $info.ArgumentList.Add('-Offline') }
            if ($loader -eq 'forge' -and $version -eq '1.21.11') {
                if (!$Jdk8Path) { throw 'ForgeGradle 7 requires JDK 8 for its launcher tool; pass -Jdk8Path' }
                $info.ArgumentList.Add('-Jdk8Path'); $info.ArgumentList.Add($Jdk8Path)
            }
            $info.UseShellExecute = $false
            $info.RedirectStandardOutput = $true
            $info.RedirectStandardError = $true
            $process = [Diagnostics.Process]::Start($info)
            $outFile = [IO.File]::Create($caseLog)
            $errFile = [IO.File]::Create((Join-Path $output "$version-$loader.stderr.log"))
            $outCopy = $process.StandardOutput.BaseStream.CopyToAsync($outFile)
            $errCopy = $process.StandardError.BaseStream.CopyToAsync($errFile)
            if (!$process.WaitForExit($TimeoutMinutes * 60000)) {
                $process.Kill($true)
                $process.WaitForExit()
                $row.status='TIMEOUT'; $row.reason="Exceeded $TimeoutMinutes minutes"
            } else {
                $row.exitCode=$process.ExitCode
                if ($process.ExitCode -ne 0) { $row.status='FAIL'; $row.reason="Gradle exit $($process.ExitCode)" }
                elseif ($CompileOnly) { $row.status='NOT_RUN'; $row.reason='Launch configuration and smoke classes compiled'; $row.compile='PASS' }
                elseif (!(Test-Path -LiteralPath $row.detailReport)) { $row.status='FAIL'; $row.reason='Missing four-process result.json' }
                else {
                    $detail=Get-Content -LiteralPath $row.detailReport -Raw|ConvertFrom-Json
                    if ($detail.status -ne 'PASS' -or @($detail.roles).Count -ne 4) { $row.status='FAIL'; $row.reason='Four-process result incomplete' }
                    else { $row.status='PASS'; $row.reason='Three-client game, network, screenshots, static audio pack'; $row.runtime='development' }
                }
            }
            $null = $outCopy.GetAwaiter().GetResult()
            $null = $errCopy.GetAwaiter().GetResult()
        } catch {
            $row.status='FAIL'; $row.reason=$_.Exception.Message
        } finally {
            if ($process -and !$process.HasExited) { $process.Kill($true); $process.WaitForExit() }
            if ($outFile) { $outFile.Dispose() }
            if ($errFile) { $errFile.Dispose() }
            $row.seconds=[math]::Round(((Get-Date)-$startedAt).TotalSeconds,1)
            $results.Add($row)
            Write-Report
            Write-Output "$version / $loader : $($row.status) ($($row.seconds)s) $($row.reason)"
            if ($row.status -in @('FAIL','TIMEOUT')) { $failed=$true }
        }
        if ($failed -and $StopOnFailure) { break versionsLoop }
    }
}
Write-Output "Report: $output"
try {
    & (Join-Path $PSScriptRoot 'generate-acceptance-dashboard.ps1')
} catch {
    Write-Error "Could not generate acceptance dashboard: $_"
    $failed = $true
}
if ($failed) { exit 1 }
