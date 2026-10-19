param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('neoforge', 'fabric', 'forge')]
    [string]$Loader,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+(\.\d+)?$')]
    [string]$McTarget
)

$ErrorActionPreference = 'Stop'

# The tools/ folder lives at the repository root, so the root is its parent.
# Keep this file ASCII-only: Windows PowerShell 5.1 reads BOM-less UTF-8 as ANSI.
$root = Split-Path -Parent $PSScriptRoot
$workflowDir = Join-Path $root '.github/workflows'
$target = Join-Path $workflowDir 'build.yml'

$template = @'
name: build

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: build-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    uses: NFraw/Craft-Cards/.github/workflows/build-reusable.yml@main
    with:
      mc_target: "__MC_TARGET__"
      loader: __LOADER__
'@

$content = $template.Replace('__MC_TARGET__', $McTarget).Replace('__LOADER__', $Loader)

New-Item -ItemType Directory -Force -Path $workflowDir | Out-Null
# UTF-8 without BOM; works on both Windows PowerShell 5.1 and PowerShell 7
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($target, $content, $utf8NoBom)

Write-Host "Wrote $target"
Write-Host "Target: $Loader / Minecraft $McTarget"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Review .github/workflows/build.yml"
Write-Host "  2. git add .github/workflows/build.yml"
Write-Host "  3. Commit on the version branch and push"
