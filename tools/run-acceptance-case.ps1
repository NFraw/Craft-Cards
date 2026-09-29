param(
    [Parameter(Mandatory)][string]$Wrapper,
    [Parameter(Mandatory)][string]$ProjectDir,
    [Parameter(Mandatory)][string]$Minecraft,
    [Parameter(Mandatory)][string]$RunId,
    [Parameter(Mandatory)][ValidateSet('pocSmoke','pocSmokeConfig')][string]$Task,
    [switch]$Fullscreen,
    [switch]$Offline,
    [string]$Jdk8Path
)
$ErrorActionPreference = 'Stop'
$arguments = @('-p', $ProjectDir, "-PmcTarget=$Minecraft", '-PpocSmoke', "-PpocRunId=$RunId", $Task, '--no-configuration-cache', '--no-daemon')
if ($Fullscreen) { $arguments += '-PpocFullscreen' }
if ($Offline) {
    $arguments += '--offline'
    # ForgeGradle's downloadMCMeta task still opens a socket under --offline.
    # The development launch metadata was already resolved when this case was built.
    if ($Minecraft -ne '1.21.11' -and $ProjectDir -eq (Join-Path (Split-Path $PSScriptRoot -Parent) 'forge')) {
        $arguments += @('-x', 'downloadMCMeta')
    }
}
if ($Jdk8Path) {
    if (!(Test-Path -LiteralPath (Join-Path $Jdk8Path 'bin/java.exe'))) { throw "JDK 8 is not installed at $Jdk8Path" }
    $arguments += "-Dorg.gradle.java.installations.paths=$Jdk8Path"
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS, '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE') | Where-Object { $_ }) -join ' '
}
& $Wrapper @arguments
exit $LASTEXITCODE
