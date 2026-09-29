param([Parameter(Mandatory)][string]$Jar, [Parameter(Mandatory)][string]$Minecraft)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Jar))
function Read-Entry([string]$name) {
    $entry = $zip.GetEntry($name)
    if (!$entry) { throw "Missing jar entry: $name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
try {
    $minor = if ($Minecraft -eq '1.21') { 0 } else { [int]($Minecraft.Split('.')[-1]) }
    $formats = @{0=34;1=34;2=42;3=42;4=46;5=55;6=63;7=64;8=64;9=69;10=69;11=75}
    if (!$formats.ContainsKey($minor)) { throw "Unsupported audit target: $Minecraft" }
    if (@($zip.Entries | Where-Object FullName -Match '\.ogg$|/gametest/|/smoke/').Count) {
        throw 'Release jar contains bundled audio or development test classes'
    }
    $pack = (Read-Entry 'pack.mcmeta' | ConvertFrom-Json).pack
    if ($minor -ge 9) {
        if ($pack.min_format[0] -ne $formats[$minor] -or $pack.max_format[0] -lt $formats[$minor]) { throw 'Incorrect modern pack range' }
    } elseif ($pack.pack_format -ne $formats[$minor]) { throw 'Incorrect pack format' }
    $recipe = Read-Entry 'data/crafty_cards/recipe/blocks/ddz_table.json' | ConvertFrom-Json
    if ($minor -le 1) {
        if ($recipe.key.C.item -ne 'minecraft:green_carpet' -or $recipe.key.P.tag -ne 'minecraft:planks' -or $recipe.key.S.item -ne 'minecraft:stick') {
            throw 'Incorrect 1.21/1.21.1 recipe ingredients'
        }
    } elseif ($recipe.key.C -ne 'minecraft:green_carpet' -or $recipe.key.P -ne '#minecraft:planks' -or $recipe.key.S -ne 'minecraft:stick') {
        throw 'Recipe ingredients did not migrate to the 1.21.2 format'
    }
    if ($recipe.result.id -ne 'crafty_cards:ddz_table') { throw 'Wrong table recipe output' }
    if ($minor -ge 4) {
        $cards = (Read-Entry 'assets/crafty_cards/items/card.json' | ConvertFrom-Json).model
        if ($cards.type -ne 'minecraft:range_dispatch' -or $cards.property -ne 'minecraft:damage' -or $cards.normalize -ne $false -or $cards.entries.Count -ne 54) {
            throw 'Card model dispatch must use 54 unnormalized damage thresholds'
        }
        for ($i = 0; $i -lt 54; $i++) {
            $entry = $cards.entries[$i]
            if ($entry.threshold -ne $i) { throw "Incorrect card threshold $i" }
            $modelPath = $entry.model.model.Replace('crafty_cards:', 'assets/crafty_cards/models/') + '.json'
            $null = Read-Entry $modelPath
        }
        if (@($cards.entries.model.model | Sort-Object -Unique).Count -ne 54) { throw 'Some card faces resolve to the same model' }
        foreach ($skin in 0..3) { $null = Read-Entry "assets/crafty_cards/items/card_covered_$skin.json" }
    }
    Write-Output "Artifact audit passed: $Minecraft / $Jar"
} finally { $zip.Dispose() }
