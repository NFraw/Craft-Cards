param([string]$GradleUserHome = "$env:USERPROFILE/.gradle", [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    $env:GRADLE_USER_HOME = $GradleUserHome
    $args = @('-p', '.', '--gradle-user-home', $GradleUserHome, '-PmcTarget=1.21.8', 'build', '--no-configuration-cache')
    if ($Offline) { $args += '--offline' }
    & (Join-Path $root 'gradlew.bat') @args
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $properties = Get-Content -LiteralPath (Join-Path $root './gradle.properties') | Where-Object { $_ -match '^mod_version=' } | Select-Object -First 1
    if (-not $properties) { throw 'mod_version is missing' }
    $modVersion = $properties.Substring('mod_version='.Length)
    $jarDir = Join-Path $root 'build/mc-1.21.8/libs/'
    $expectedJar = "crafty_cards-neoforge-mc1.21.8-$modVersion.jar"
    $jars = @(Get-ChildItem -LiteralPath $jarDir -Filter '*.jar' -File)
    if (-not (Test-Path -LiteralPath (Join-Path $jarDir $expectedJar) -PathType Leaf)) { throw "Expected release JAR missing: $expectedJar" }
    foreach ($jar in $jars) {
        if ($jar.Name -ne $expectedJar -and $jar.Name -ne "crafty_cards-neoforge-mc1.21.8-$modVersion-sources.jar") {
            throw "Unexpected JAR for this branch: $($jar.Name)"
        }
    }
    Write-Output "Verified release JAR: $expectedJar"
    exit 0
} finally { Pop-Location }
