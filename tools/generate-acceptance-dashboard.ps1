param([string]$OutputPath)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$OutputPath) { $OutputPath = Join-Path $repo 'build/acceptance-overview.html' }
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
$outDir = Split-Path $OutputPath -Parent
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

function Href([string]$path) {
    if (!$path -or !(Test-Path -LiteralPath $path)) { return $null }
    [IO.Path]::GetRelativePath($outDir, [IO.Path]::GetFullPath($path)).Replace('\', '/')
}

$targets = @(Get-Content (Join-Path $repo 'versions/targets.json') -Raw | ConvertFrom-Json)
$manifestPath = Join-Path $repo 'build/releases/manifest.json'
$manifest = if (Test-Path $manifestPath) { @(Get-Content $manifestPath -Raw | ConvertFrom-Json) } else { @() }
$runs = [Collections.Generic.List[object]]::new()
Get-ChildItem (Join-Path $repo 'build/acceptance-matrix') -Filter results.json -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    $batch = Get-Content $_.FullName -Raw | ConvertFrom-Json
    foreach ($row in @($batch.results)) {
        if ($row.minecraft -and $row.loader) {
            $runs.Add([pscustomobject]@{date=$batch.generatedAt;batch=$batch.batchId;row=$row;batchReport=$_.FullName})
        }
    }
}
Get-ChildItem (Join-Path $repo 'build/poc') -Filter result.json -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    $detail = Get-Content $_.FullName -Raw | ConvertFrom-Json
    if ($detail.minecraft -eq '1.21.1') {
        $runs.Add([pscustomobject]@{
            date=$_.LastWriteTime.ToString('o');batch='1.21.1 baseline';batchReport=$_.FullName
            row=[pscustomobject]@{minecraft='1.21.1';loader=$detail.loader;status=$detail.status;scenario='normal';
                reason=$detail.error;runtime='development';detailReport=$_.FullName;log=$null}
        })
    }
}
$runs = @($runs | Sort-Object date -Descending)

$unitFiles = @(Get-ChildItem (Join-Path $repo 'build/test-results/test') -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
$unitCount = 0; $unitFailures = 0
foreach ($file in $unitFiles) {
    [xml]$xml = Get-Content $file.FullName
    $unitCount += [int]$xml.testsuite.tests
    $unitFailures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
}
$gameLog = Join-Path $repo 'run-gametest/logs/latest.log'
$gamePassed = (Test-Path $gameLog) -and [bool](Select-String -LiteralPath $gameLog -Pattern 'All 35 required tests passed' -Quiet)
$versions = @('1.21.1') + @($targets.Minecraft | Sort-Object { [version]$_ })
$cases = [Collections.Generic.List[object]]::new()
$pass=0; $fail=0; $notRun=0; $unsupported=0
foreach ($version in $versions) {
    $target = $targets | Where-Object Minecraft -EQ $version | Select-Object -First 1
    foreach ($loader in @('neoforge','fabric','forge')) {
        $history = @($runs | Where-Object { $_.row.minecraft -eq $version -and $_.row.loader -eq $loader })
        $actual = @($history | Where-Object {
            $_.row.status -in @('PASS','FAIL','TIMEOUT') -and
            ($_.row.runtime -ne 'not-run' -or ($_.row.detailReport -and (Test-Path -LiteralPath $_.row.detailReport)))
        } | Select-Object -First 1)[0]
        $missingLoader = $version -ne '1.21.1' -and $loader -eq 'forge' -and !$target.Forge
        $status = if ($missingLoader) {'UNSUPPORTED'} elseif ($actual) {$actual.row.status} else {'NOT_RUN'}
        if ($status -eq 'PASS') {$pass++} elseif ($status -in @('FAIL','TIMEOUT')) {$fail++}
        elseif ($status -eq 'UNSUPPORTED') {$unsupported++} else {$notRun++}

        $latest = $null
        if ($actual) {
            $row = $actual.row
            $detail = if ($row.detailReport -and (Test-Path -LiteralPath $row.detailReport)) {
                Get-Content $row.detailReport -Raw | ConvertFrom-Json
            } else {$null}
            $roles = @()
            if ($detail) {
                $roles = @($detail.roles | ForEach-Object {
                    [ordered]@{name=$_.role;result=$_.result;exitCode=$_.exitCode;log=Href $_.log
                        shots=@($_.screenshots | ForEach-Object {
                            $local = Href $_
                            if ($local) {[ordered]@{name=[IO.Path]::GetFileName($_);href=$local}}
                        } | Where-Object {$_})}
                })
            }
            $latest = [ordered]@{date=$actual.date;batch=$actual.batch;reason=$row.reason;scenario=$row.scenario
                report=Href $row.detailReport;log=Href $row.log;batchReport=Href $actual.batchReport
                checks=if($detail){$detail.checks}else{$null};roles=$roles}
        }
        $oldRuns = @($history | ForEach-Object {
            $errorPath = if($_.row.log){[regex]::Replace([string]$_.row.log,'\.log$','.stderr.log')}else{$null}
            [ordered]@{date=$_.date;batch=$_.batch;status=$_.row.status;scenario=$_.row.scenario;reason=$_.row.reason
                report=Href $_.batchReport;log=Href $_.row.log;error=Href $errorPath}
        })
        $build = $manifest | Where-Object { $_.minecraft -eq $version -and $_.loader -eq $loader } | Select-Object -First 1
        $buildInfo = if($build){[ordered]@{tests=$build.tests;failures=$build.failures;sha256=$build.sha256
            jar=Href (Join-Path $repo ('build/releases/'+$build.file));log=Href (Join-Path $repo $build.buildLog)}}else{$null}
        $cases.Add([ordered]@{version=$version;loader=$loader;status=$status;latest=$latest;history=$oldRuns;build=$buildInfo})
    }
}
$data = [ordered]@{generated=(Get-Date -Format 'yyyy-MM-dd HH:mm:ss');versions=$versions
    totals=[ordered]@{pass=$pass;fail=$fail;notRun=$notRun;unsupported=$unsupported}
    unit=[ordered]@{count=$unitCount;failures=$unitFailures;report=Href (Join-Path $repo 'build/reports/tests/test/index.html')}
    game=[ordered]@{passed=$gamePassed;log=Href $gameLog}
    baseline=Href (Join-Path $repo 'docs/evidence/multi-version-2026-09-27/neoforge-1.21.1/client2/02-selected.png')
    cases=$cases.ToArray()}
$json = (ConvertTo-Json -InputObject $data -Depth 20 -Compress).Replace('<','\u003c')

$page = @'
<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Crafty Cards 多版本验收</title>
<style>
:root{color-scheme:dark;font:15px/1.5 system-ui,"Microsoft YaHei",sans-serif;background:#0d1722;color:#e7f0f7}*{box-sizing:border-box}body{max-width:1480px;margin:auto;padding:24px}h1,h2,h3,p{margin-top:0}h1{font-size:1.8rem;margin-bottom:5px}h2{font-size:1.2rem;margin-bottom:12px}h3{font-size:1rem;margin-bottom:10px}a{color:#8bd0ff}button{font:inherit;cursor:pointer}button:focus-visible,a:focus-visible{outline:2px solid #ffdc7b;outline-offset:2px}.muted{color:#a8bdca}.small{font-size:.85rem}.lead{max-width:1000px;color:#cad9e3}.hero{display:flex;justify-content:space-between;gap:20px;align-items:start}.links{display:flex;gap:8px;flex-wrap:wrap}.links a,.quick a{border:1px solid #4b6a7b;border-radius:7px;padding:6px 10px;text-decoration:none}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0 20px}.stat,.panel{background:#142531;border:1px solid #304a5b;border-radius:11px}.stat{padding:13px 16px}.stat strong{display:block;font-size:1.5rem}.stat small{color:#adbfcb}.panel{padding:18px;margin-bottom:18px}.matrix-wrap,.scroll{overflow-x:auto}.matrix{width:100%;border-collapse:separate;border-spacing:0 5px}.matrix th{text-align:left;color:#b6cad6;padding:5px 8px}.matrix td{padding:2px 6px}.matrix td:first-child{width:115px;font-weight:700}.cell{display:flex;align-items:center;justify-content:space-between;width:100%;min-height:42px;padding:8px 12px;border:1px solid transparent;border-radius:8px;background:#203845;color:#e9f4f9;text-align:left}.cell:hover{filter:brightness(1.15)}.cell[aria-pressed=true]{outline:2px solid #ffdc7b;outline-offset:1px}.badge{display:inline-block;border-radius:999px;padding:2px 9px;font-size:.82rem;font-weight:750;white-space:nowrap}.PASS{background:#176348;color:#b0f8cf}.FAIL,.TIMEOUT{background:#813a40;color:#ffdddd}.NOT_RUN{background:#6b572b;color:#ffe0a1}.UNSUPPORTED{background:#40515c;color:#dde6ed}.detail-head,.section-head{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}.detail-head h2,.section-head h3{margin:0}.detail-head>div:first-child{display:flex;align-items:center;gap:10px}.nav,.filters,.quick,.checks{display:flex;flex-wrap:wrap;gap:8px}.nav button,.filters button{border:1px solid #557185;border-radius:7px;background:#1d3544;color:#e6f2fa;padding:6px 10px}.nav button:disabled{opacity:.4;cursor:default}.filters button[aria-pressed=true]{background:#346384;border-color:#9dd8ff}.quick{margin:13px 0 16px}.checks{margin:0 0 16px}.check{border:1px solid #4b6776;border-radius:7px;padding:5px 9px;background:#1c3440}.check.ok{border-color:#3a9369}.check.manual{border-color:#987943}.check.bad{border-color:#b45057}.section-head{margin:18px 0 9px}.gallery{display:grid;grid-template-columns:repeat(auto-fill,minmax(205px,1fr));gap:12px}.shot{display:block;border:1px solid #476679;border-radius:8px;overflow:hidden;background:#0e1c27;text-decoration:none;color:#e5f0f8}.shot img{display:block;width:100%;height:150px;object-fit:contain;background:#101820}.shot span{display:block;padding:7px 9px;font-size:.87rem}.shot small{color:#9cb2c1}.empty{padding:22px;border:1px dashed #526d7e;border-radius:8px;color:#aabdc9}details{margin-top:16px}summary{cursor:pointer;font-weight:700;padding:7px 0}table.records{width:100%;border-collapse:collapse;min-width:690px}table.records th,table.records td{border-bottom:1px solid #2e4858;padding:8px;text-align:left;vertical-align:top}table.records th{color:#b9cad6}table.records td:last-child{overflow-wrap:anywhere}.foot{color:#adc0ce;margin:22px 0 8px}
@media(max-width:760px){body{padding:14px}.hero{display:block}.links{margin-top:12px}.stats{grid-template-columns:repeat(2,1fr)}.panel{padding:12px}.cell{display:block;padding:7px}.cell .badge{margin-top:5px}.matrix td:first-child{width:88px}}
</style></head><body>
<header class="hero"><div><h1>Crafty Cards 多版本验收</h1><p class="lead">先选版本与加载器，再核对牌面、全屏 HUD、配置文字和日志。状态是自动化结果；截图观感和实际听音仍需人工确认。</p><p id="generated" class="muted small"></p></div><div class="links"><a id="baseline" target="_blank">1.21.1 人工基准截图</a><a id="unit-link" target="_blank">单测报告</a></div></header>
<div class="stats" id="stats"></div>
<section class="panel"><h2>版本 × 加载器</h2><p class="muted">每格显示最近一次真实联机结果。点击格子查看该组合；旧失败与超时保留在运行历史中。</p><div class="matrix-wrap"><table class="matrix" id="matrix"><thead><tr><th>Minecraft</th><th>NeoForge</th><th>Fabric</th><th>Forge</th></tr></thead><tbody></tbody></table></div></section>
<section class="panel" id="detail" aria-live="polite"></section>
<p class="foot">运行使用开发环境；历史安装包可能早于本轮修复。音乐包资源存在并重载成功不等于实际扬声器播放；配置文字断言不等于所有屏幕尺寸都无重叠。请保留本页与 build 目录的相对位置，以便打开截图和日志。</p>
<script>
const D=__DATA__, loaderName={neoforge:'NeoForge',fabric:'Fabric',forge:'Forge'};
const checkName={networkGame:'三人联机',staticAudioPack:'音乐包资源',hudFacesVisible:'牌面可见',configurationText:'配置文字',actualAudioListening:'实际听音',visualReview:'人工看图',productionJar:'发布包安装'};
const shotName={'02-hud-windowed.png':'HUD · 窗口','02-hud-fullscreen.png':'HUD · 全屏','02-playing.png':'出牌与底牌','02-selected.png':'选牌','01-bidding.png':'叫分','03-settled.png':'结算','04-config-home.png':'配置主页','04-config-sound.png':'音乐包配置','04-config-play.png':'玩法配置','04-config-play-bottom.png':'玩法配置下半页','04-config-render.png':'渲染配置','04-config-render-bottom.png':'渲染配置下半页','04-config-table.png':'牌桌配置'};
const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const link=(href,label)=>href?`<a href="${esc(href)}" target="_blank" rel="noopener">${esc(label)}</a>`:`<span class="muted">${esc(label)}：无</span>`;
const badge=s=>`<span class="badge ${esc(s)}">${esc(s)}</span>`;
const byKey=new Map(D.cases.map(c=>[c.version+'/'+c.loader,c]));
let selected='1.21.11/neoforge',filter='featured';
document.getElementById('generated').textContent='生成于 '+D.generated;
for(const [id,href] of [['baseline',D.baseline],['unit-link',D.unit.report]]){let a=document.getElementById(id);if(href)a.href=href;else a.hidden=true}
document.getElementById('stats').innerHTML=[`${D.totals.pass} / ${D.totals.pass+D.totals.fail+D.totals.notRun}`,D.totals.fail,`${D.unit.count} / ${D.unit.failures}`,D.game.passed?'35 / 35':'未确认'].map((x,i)=>`<div class="stat"><strong>${esc(x)}</strong><small>${['可用组合通过','失败或超时','1.21.1 单测 / 失败','1.21.1 GameTest'][i]}</small></div>`).join('');
const body=document.querySelector('#matrix tbody');
for(const v of D.versions){let tr=document.createElement('tr');tr.innerHTML='<td>'+esc(v)+'</td>'+['neoforge','fabric','forge'].map(l=>{let c=byKey.get(v+'/'+l);return `<td><button class="cell" type="button" data-key="${esc(v+'/'+l)}" aria-pressed="false"><span>${loaderName[l]}</span>${badge(c.status)}</button></td>`}).join('');body.appendChild(tr)}
body.addEventListener('click',ev=>{let b=ev.target.closest('button[data-key]');if(!b)return;selected=b.dataset.key;filter='featured';render();document.getElementById('detail').scrollIntoView({behavior:'smooth',block:'start'})});
const kind=n=>n.startsWith('04-config')?'config':n.startsWith('03-')?'settled':'hud';
function allShots(c){return(c.latest?.roles||[]).flatMap(r=>(r.shots||[]).map(s=>({...s,role:r.name,kind:kind(s.name)})))}
function shown(s){if(filter==='all')return true;if(filter!=='featured')return s.kind===filter;return(s.role==='runSmokeClient2'&&['02-hud-windowed.png','02-hud-fullscreen.png','02-playing.png','02-selected.png','03-settled.png'].includes(s.name))||(s.role==='runSmokeClient1'&&s.kind==='config')}
function gallery(c){let all=allShots(c),items=all.filter(shown);document.getElementById('shot-count').textContent=`${items.length} / ${all.length} 张`;document.getElementById('gallery').innerHTML=items.length?items.map(s=>`<a class="shot" href="${esc(s.href)}" target="_blank" rel="noopener"><img loading="lazy" src="${esc(s.href)}" alt="${esc(shotName[s.name]||s.name)}"><span>${esc(shotName[s.name]||s.name)}<br><small>${esc(s.role)}</small></span></a>`).join(''):'<div class="empty">这个筛选下没有截图；可切换到“全部截图”。</div>';document.querySelectorAll('.filters button').forEach(b=>b.setAttribute('aria-pressed',b.dataset.filter===filter))}
function render(){let c=byKey.get(selected),i=D.versions.indexOf(c.version),latest=c.latest;document.querySelectorAll('.cell').forEach(b=>b.setAttribute('aria-pressed',b.dataset.key===selected));let reason=c.status==='UNSUPPORTED'?'该 Minecraft 版本没有对应的 Forge 发布组合。':latest?.reason||'尚无真实客户端测试结果。';let quick=latest?[[latest.report,'四进程结果'],[latest.log,'控制日志'],[latest.batchReport,'批次 JSON']].map(x=>link(...x)).join(''):'';
let checks=latest?.checks?Object.entries(latest.checks).map(([k,v])=>{let manual=['actualAudioListening','visualReview','productionJar'].includes(k);return `<span class="check ${manual?'manual':v===true?'ok':v===false?'bad':'manual'}">${esc(checkName[k]||k)}：${manual?'待人工':v===true?'通过':v===false?'未过':'未检查'}</span>`}).join(''):'<span class="muted">暂无检查项</span>';
let roles=(latest?.roles||[]).map(r=>`<tr><td>${esc(r.name)}</td><td>${esc(r.result)}</td><td>${esc(r.exitCode)}</td><td>${link(r.log,'日志')}</td></tr>`).join('');let history=c.history.map(h=>`<tr><td>${badge(h.status)}</td><td>${esc(h.date)}</td><td>${esc(h.scenario)}</td><td>${esc(h.reason)}</td><td>${link(h.report,'批次')} · ${link(h.log,'日志')}${h.error?' · '+link(h.error,'错误'):''}</td></tr>`).join('');let build=c.build?`<p class="small muted">${esc(c.build.tests)} 项单测，${esc(c.build.failures)} 失败 · ${link(c.build.jar,'安装包')} · ${link(c.build.log,'构建日志')}<br>SHA-256：${esc(c.build.sha256)}</p>`:'<p class="muted">没有本地历史发布清单记录。</p>';
document.getElementById('detail').innerHTML=`<div class="detail-head"><div><h2>${esc(c.version)} · ${loaderName[c.loader]}</h2>${badge(c.status)}</div><div class="nav"><button id="prev" type="button" ${i===0?'disabled':''}>← 上一版本</button><button id="next" type="button" ${i===D.versions.length-1?'disabled':''}>下一版本 →</button></div></div><p class="muted small">${esc(reason)}${latest?.date?' · '+esc(latest.date):''}</p><div class="quick">${quick}</div><div class="checks">${checks}</div><div class="section-head"><h3>截图复核</h3><div class="filters"><button data-filter="featured">重点截图</button><button data-filter="hud">HUD / 牌面</button><button data-filter="config">配置文字</button><button data-filter="settled">结算</button><button data-filter="all">全部截图</button></div></div><p id="shot-count" class="small muted"></p><div id="gallery" class="gallery"></div><details><summary>四进程结果与日志</summary><div class="scroll"><table class="records"><thead><tr><th>进程</th><th>结果</th><th>退出码</th><th>日志</th></tr></thead><tbody>${roles||'<tr><td colspan="4">无记录</td></tr>'}</tbody></table></div></details><details><summary>运行历史（${c.history.length} 次，含旧失败）</summary><div class="scroll"><table class="records"><thead><tr><th>状态</th><th>时间</th><th>场景</th><th>说明</th><th>记录</th></tr></thead><tbody>${history||'<tr><td colspan="5">无记录</td></tr>'}</tbody></table></div></details><details><summary>历史构建与安装包</summary>${build}</details>`;
document.querySelectorAll('.filters button').forEach(b=>b.addEventListener('click',()=>{filter=b.dataset.filter;gallery(c)}));for(const [id,d] of [['prev',-1],['next',1]])document.getElementById(id).addEventListener('click',()=>{let v=D.versions[i+d];if(v){selected=v+'/'+c.loader;filter='featured';render()}});gallery(c)}
render();
</script></body></html>
'@
[IO.File]::WriteAllText($OutputPath, $page.Replace('__DATA__',$json), [Text.UTF8Encoding]::new($false))
Write-Output "Dashboard: $OutputPath"
