param(
    [switch]$Create,
    [string]$BaseRef = 'HEAD'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    $null = & git rev-parse --verify "$BaseRef^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) { throw "BaseRef is not a commit: $BaseRef" }

    $targets = Get-Content (Join-Path $root 'versions/targets.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $versions = @($targets | ForEach-Object { $_.Minecraft })
    $ordered = $versions | Sort-Object -Unique { [version]$_ }
    $branches = foreach ($version in $ordered) {
        foreach ($loader in @('neoforge', 'fabric', 'forge')) {
            if ($loader -eq 'forge' -and $version -eq '1.21.2') { continue }
            [pscustomobject]@{
                Minecraft = $version
                Loader = $loader
                Branch = "$loader-mc$version"
                BuildTarget = "-PmcTarget=$version"
            }
        }
    }

    $existing = @(& git branch --format='%(refname:short)')
    foreach ($item in $branches) {
        if ($item.Branch -in $existing) {
            Write-Host "EXISTS  $($item.Branch)"
        } elseif ($Create) {
            & git branch $item.Branch $BaseRef
            if ($LASTEXITCODE -ne 0) { throw "Could not create $($item.Branch)" }
            Write-Host "CREATED $($item.Branch)"
        } else {
            Write-Host "PLAN    $($item.Branch)"
        }
    }
    Write-Host "Targets: $($branches.Count); unsupported: forge-mc1.21.2"
    if (-not $Create) { Write-Host 'Run with -Create to make local branches. This script never pushes.' }
} finally {
    Pop-Location
}
