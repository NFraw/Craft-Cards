param([string]$GradleUserHome = "$env:USERPROFILE/.gradle", [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    $env:GRADLE_USER_HOME = $GradleUserHome
    $args = @('-p', '.', '--gradle-user-home', $GradleUserHome, '-PmcTarget=1.21.1', 'build', '--no-configuration-cache')
    if ($Offline) { $args += '--offline' }
    & (Join-Path $root 'gradlew.bat') @args
    exit $LASTEXITCODE
} finally { Pop-Location }