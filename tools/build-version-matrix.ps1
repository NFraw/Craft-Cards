param(
    [ValidateSet('neoforge', 'fabric', 'forge')]
    [string[]]$Loaders = @('neoforge', 'fabric', 'forge'),
    [string[]]$Versions = @('1.21','1.21.1','1.21.2','1.21.3','1.21.4','1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10','1.21.11'),
    [string]$GradleUserHome = "$env:USERPROFILE/.gradle",
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$targets = Get-Content (Join-Path $repo 'versions/targets.json') -Raw | ConvertFrom-Json
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $repo "build/version-matrix/$stamp"
New-Item -ItemType Directory -Path $output -Force | Out-Null
$results = [Collections.Generic.List[object]]::new()
Push-Location $repo
try {
    foreach ($version in $Versions) {
        $target = $targets | Where-Object Minecraft -EQ $version
        if (!$target) { throw "Unsupported version: $version (26.x is intentionally excluded)" }
        foreach ($loader in $Loaders) {
            if ($loader -eq 'forge' -and !$target.Forge) {
                Write-Output "$version / Forge: no official release; skipped"
                continue
            }
            $wrapper = if ($loader -eq 'fabric' -and $version -in @('1.21','1.21.1')) { '.\fabric\gradlew.bat' } elseif ($loader -eq 'fabric') { '.\versions\gradlew.bat' } elseif ($loader -eq 'forge' -and $version -eq '1.21.11') { '.\versions\forge-toolchain\gradlew.bat' } else { '.\gradlew.bat' }
            $project = if ($loader -eq 'neoforge') { '.' } else { $loader }
            $log = Join-Path $output "$loader-$version.log"
            $gradleArgs = @('-p', $project, '--gradle-user-home', $GradleUserHome, "-PmcTarget=$version", 'build', '--no-configuration-cache')
            if ($loader -eq 'forge') { $gradleArgs += '--no-daemon' }
            if ($Offline) { $gradleArgs += '--offline' }
            Write-Output "Building $version / $loader"
            & $wrapper @gradleArgs > $log 2>&1
            $code = $LASTEXITCODE
            $buildDir = if ($version -eq '1.21.1') { Join-Path $repo "$project/build" } else { Join-Path $repo "$project/build/mc-$version" }
            $artifacts = @()
            if ($code -eq 0) {
                $destination = Join-Path $repo "build/releases/$version/$loader"
                New-Item -ItemType Directory -Path $destination -Force | Out-Null
                foreach ($jar in (Get-ChildItem "$buildDir/libs/*.jar" | Where-Object Name -NotMatch 'sources|dev|javadoc')) {
                    & (Join-Path $PSScriptRoot 'test-version-artifact.ps1') -Jar $jar.FullName -Minecraft $version
                    Copy-Item -LiteralPath $jar.FullName -Destination $destination -Force
                    $artifacts += [ordered]@{ path = (Join-Path $destination $jar.Name); sha256 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash }
                }
                if (!$artifacts.Count) { throw "Successful build produced no release jar: $loader $version" }
            }
            $tests = 0; $failures = 0
            if ($code -eq 0) {
                foreach ($report in (Get-ChildItem "$buildDir/test-results/test/TEST-*.xml" -ErrorAction SilentlyContinue)) {
                    [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
                    $tests += [int]$xml.testsuite.tests
                    $failures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
                }
            }
            $results.Add([ordered]@{ minecraft = $version; loader = $loader; exitCode = $code; tests = $tests; failures = $failures; runtime = 'not-tested'; log = $log; artifacts = $artifacts })
            $results | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $output 'results.json') -Encoding utf8
            Write-Output "$version / ${loader}: exit=$code tests=$tests failures=$failures"
        }
    }
} finally { Pop-Location }
Write-Output "Evidence: $output"
if (@($results | Where-Object { $_.exitCode -ne 0 }).Count) { exit 1 }
