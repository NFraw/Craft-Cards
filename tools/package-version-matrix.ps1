param([string]$ArchiveName = 'crafty-cards-1.0.0-all-loaders.zip')
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$release = Join-Path $repo 'build/releases'
$targets = Get-Content (Join-Path $repo 'versions/targets.json') -Raw | ConvertFrom-Json
$latest = @{}
foreach ($file in (Get-ChildItem (Join-Path $repo 'build/version-matrix/*/results.json') | Sort-Object FullName)) {
    foreach ($result in @(Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json)) {
        $latest["$($result.minecraft)/$($result.loader)"] = $result
    }
}
$manifest = [Collections.Generic.List[object]]::new()
$checksums = [Collections.Generic.List[string]]::new()
$rows = [Collections.Generic.List[string]]::new()
foreach ($target in ($targets | Sort-Object { [version]$_.Minecraft })) {
    foreach ($loader in @('neoforge', 'fabric', 'forge')) {
        if ($loader -eq 'forge' -and !$target.Forge) { continue }
        $key = "$($target.Minecraft)/$loader"
        $result = $latest[$key]
        if (!$result -or $result.exitCode -ne 0 -or $result.tests -lt 122 -or $result.failures -ne 0) {
            throw "No complete passing build for $key"
        }
        if (@($result.artifacts).Count -ne 1) { throw "Expected exactly one installable jar for $key" }
        $artifact = $result.artifacts[0]
        if (!(Test-Path -LiteralPath $artifact.path) -or (Get-FileHash -LiteralPath $artifact.path -Algorithm SHA256).Hash -ne $artifact.sha256) {
            throw "Release jar changed since validation: $key"
        }
        $relative = "$key/$(Split-Path $artifact.path -Leaf)"
        $dependency = switch ($loader) { neoforge { $target.Neo }; fabric { "Loader 0.19.5 + Fabric API $($target.Fabric)" }; forge { $target.Forge } }
        $manifest.Add([ordered]@{minecraft=$target.Minecraft;loader=$loader;dependency=$dependency;file=$relative;sha256=$artifact.sha256;tests=$result.tests;failures=$result.failures;runtime='not-tested';buildLog=[IO.Path]::GetRelativePath($repo,$result.log).Replace('\','/')})
        $checksums.Add("$($artifact.sha256.ToLowerInvariant())  $relative")
        $rows.Add("| $($target.Minecraft) | $loader | $dependency | $($result.tests) / 0 | 待测 |")
    }
}
if ($manifest.Count -ne 35) { throw "Expected 35 supported combinations, got $($manifest.Count)" }
$manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $release 'manifest.json') -Encoding utf8
$checksums | Set-Content (Join-Path $release 'SHA256SUMS.txt') -Encoding utf8
$readme = @'
# Crafty Cards 1.0.0 安装包索引

范围：Minecraft 1.21—1.21.11，不含任何 26.x。Forge 官方未发布 1.21.2，因此共有 35 个安装包。

这些包通过了构建、公共单测和发布资源检查；Fabric / Forge 还检查了 mixin 的目标签名。三人联机、HUD 观感与实际听音仍需逐个版本人工验收。不要将构建通过理解为已完成实机验收。

## 安装

1. 使用 Java 21，为每个 Minecraft / 加载器组合创建独立实例和测试世界。
2. 从“版本/加载器”目录取出唯一 jar 放进 mods。客户端和服务器安装相同组合；不要同时放入多个 Crafty Cards jar。
3. Fabric 额外安装表中对应的 Fabric API。NeoForge 和 Forge 不需要 Fabric API。
4. 音乐包单独安装到 config/crafty_cards/soundpacks/<包目录>/，目录内应直接包含 pack.json 与音频文件。在游戏设置中选择包并保存。模组 jar 不包含音频。
5. 三人加入牌桌前，先由房主 Shift+右键配置筹码，或关闭该桌筹码。检查发牌、叫分、HUD 遮挡与焦点、倒计时、出牌、过牌、结算、离桌和掉线。
6. 音乐需实际听发牌、语音与 BGM，并测试切包、保存、重载；配置保存后退出重进检查桌子设置。

报错时记录版本、加载器版本、操作步骤和 logs/latest.log；HUD 问题附截图。完整验收清单见仓库 versions/README.md。

## 构建记录

每个组合独立构建和运行同一套公共测试，并非 35 套不同测试。1.21.1 NeoForge 的 35 项 GameTest 另行回归；新版本 GameTest 尚未移植。

| Minecraft | 加载器 | 依赖版本 | 单测数 / 失败数 | 实机 |
| --- | --- | --- | --- | --- |
'@
($readme + "`n" + ($rows -join "`n") + "`n") | Set-Content (Join-Path $release 'README.md') -Encoding utf8
$archive = Join-Path (Split-Path $release -Parent) $ArchiveName
# Include only verified artifacts and the generated index, never stale release files.
$staging = Join-Path $repo ('build/package-stage/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging -Force | Out-Null
foreach ($entry in $manifest) {
    $destination = Join-Path $staging $entry.file
    New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $release $entry.file) -Destination $destination
}
foreach ($name in @('README.md','manifest.json','SHA256SUMS.txt')) {
    Copy-Item -LiteralPath (Join-Path $release $name) -Destination $staging
}
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $archive -Force
Write-Output "Packaged $($manifest.Count) verified builds: $archive"
