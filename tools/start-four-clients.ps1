param(
    [ValidateSet('client','client2','client3','client4')][string[]]$Clients=@('client','client2','client3','client4'),
    [switch]$SkipPrepare,
    [string]$GradleUserHome="$env:USERPROFILE/.gradle"
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $existing=@()
    if(Test-Path -LiteralPath 'build/manual-clients/processes.json') {
        # An empty array deserializes to $null, and older runs wrote a {value,Count} wrapper;
        # normalise both so the PID checks below never see a null entry.
        $loaded=Get-Content -LiteralPath 'build/manual-clients/processes.json' -Raw | ConvertFrom-Json
        if($null -ne $loaded -and $null -ne $loaded.PSObject.Properties['value']) {$loaded=$loaded.value}
        $existing=@($loaded | Where-Object { $null -ne $_ -and $null -ne $_.PID })
    }
    if(-not $SkipPrepare) {
        # Prepare all classes/assets before any client starts. Never compile beside running dev clients.
        foreach($runningClient in $existing) {
            if(Get-Process -Id $runningClient.PID -ErrorAction SilentlyContinue) {
                throw "Client $($runningClient.Name) is running. Close all dev clients before preparation, or use -SkipPrepare with existing files."
            }
        }
        $env:GRADLE_USER_HOME=$GradleUserHome
        & .\gradlew.bat '-PmcTarget=1.21.1' --init-script tools/manual-clients.init.gradle prepareFourManualClients --offline --no-configuration-cache
        if($LASTEXITCODE -ne 0){throw 'Client preparation failed'}
    }
    $rows=Get-Content -LiteralPath 'build/manual-clients/launches.json' -Raw | ConvertFrom-Json
    $modClasses='crafty_cards%%'+(Join-Path $projectRoot 'build/mc-1.21.1/classes/java/main')+';crafty_cards%%'+(Join-Path $projectRoot 'build/mc-1.21.1/resources/main')
    $hadModClasses=Test-Path Env:MOD_CLASSES
    $previousModClasses=[Environment]::GetEnvironmentVariable('MOD_CLASSES','Process')
    try {
        # NeoForge prefers MOD_CLASSES over fml.modFolders, even when the environment value is empty.
        # Always supply the complete module mapping to every child, not merely the first one.
        $env:MOD_CLASSES=$modClasses
        foreach($row in $rows | Where-Object {$_.name -in $Clients}) {
            $old=$existing | Where-Object {$_.Name -eq $row.name}
            if($old -and (Get-Process -Id $old.PID -ErrorAction SilentlyContinue)) {
                throw "$($row.name) is still running; close that client before launching it again"
            }
            $logPath=Join-Path $projectRoot ('build/manual-clients/'+$row.name+'.out.log')
            $errorPath=Join-Path $projectRoot ('build/manual-clients/'+$row.name+'.err.log')
            $gameProcess=Start-Process -FilePath $row.executable -ArgumentList ('"@'+$row.argsFile+'"') -WorkingDirectory $row.directory -WindowStyle Hidden -RedirectStandardOutput $logPath -RedirectStandardError $errorPath -PassThru
            $existing=@($existing | Where-Object {$_.Name -ne $row.name})
            $existing+=[pscustomobject]@{Name=$row.name;PID=$gameProcess.Id;Directory=$row.directory;Log=$logPath;ErrorLog=$errorPath}
            Write-Output "$($row.name): PID $($gameProcess.Id), $($row.directory)"
        }
    } finally {
        if($hadModClasses){$env:MOD_CLASSES=$previousModClasses}
        else{Remove-Item Env:MOD_CLASSES -ErrorAction SilentlyContinue}
        $existing | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath 'build/manual-clients/processes.json' -Encoding utf8
        if($existing.Count -eq 0) {Set-Content -LiteralPath 'build/manual-clients/processes.json' -Value '[]' -Encoding utf8}
    }
} finally {Pop-Location}
