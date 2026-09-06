# Launches a NeoForge client straight from the CurseForge install, with an
# isolated game directory containing only the mods you point it at.
#
# Exists so Reach-In can be smoke tested on its own instead of inside a 477-mod
# pack, where a mixin failure would be buried in unrelated log noise.

param(
    [string]$Install = "$env:USERPROFILE\curseforge\minecraft\Install",
    [string]$Version = "neoforge-21.1.221",
    [string]$GameDir = "C:\Reach-In\run",
    [string]$Username = "Dev",
    [int]$Width = 1280,
    [int]$Height = 720,
    [string]$QuickPlay = "",
    [switch]$Trace
)

$ErrorActionPreference = "Stop"

$libraryDir = Join-Path $Install "libraries"
$versionsDir = Join-Path $Install "versions"

$neo = Get-Content (Join-Path $versionsDir "$Version\$Version.json") -Raw | ConvertFrom-Json
$base = Get-Content (Join-Path $versionsDir "$($neo.inheritsFrom)\$($neo.inheritsFrom).json") -Raw | ConvertFrom-Json

# Windows-only launch, so the only rules worth honouring are os-name ones.
function Test-Rules($lib) {
    if (-not $lib.rules) { return $true }
    $allow = $false
    foreach ($rule in $lib.rules) {
        $matched = $true
        if ($rule.os -and $rule.os.name -and $rule.os.name -ne "windows") { $matched = $false }
        if ($matched) { $allow = ($rule.action -eq "allow") }
    }
    return $allow
}

$classpath = [System.Collections.Generic.List[string]]::new()
$seen = @{}
foreach ($lib in @($neo.libraries) + @($base.libraries)) {
    if (-not (Test-Rules $lib)) { continue }
    $path = $lib.downloads.artifact.path
    if (-not $path) { continue }
    if ($seen.ContainsKey($path)) { continue }
    $seen[$path] = $true
    $full = Join-Path $libraryDir ($path -replace "/", "\")
    if (Test-Path $full) { $classpath.Add($full) }
}
# Deliberately NOT adding versions\<mc>\<mc>.jar. FML builds the "minecraft" module
# itself from the srg + extra jars under libraryDirectory; putting the vanilla jar on
# the classpath as well makes it a second module exporting the same packages, and
# module resolution fails before the game starts.

$replace = @{
    "`${library_directory}"    = $libraryDir
    "`${classpath_separator}"  = ";"
    "`${version_name}"         = $Version
    "`${natives_directory}"    = (Join-Path $Install "natives\$Version")
    "`${launcher_name}"        = "reachin-dev"
    "`${launcher_version}"     = "1"
}

function Expand-Args($values) {
    $out = @()
    foreach ($value in $values) {
        if ($value -isnot [string]) { continue }   # skip rule-gated arg objects
        foreach ($key in $replace.Keys) { $value = $value.Replace($key, $replace[$key]) }
        $out += $value
    }
    return $out
}

$jvmArgs = @("-Xmx4G", "-XX:+UseG1GC")
if ($Trace) { $jvmArgs += "-Dreachin.debug=true" }
$jvmArgs += Expand-Args $base.arguments.jvm
$jvmArgs += Expand-Args $neo.arguments.jvm

$gameArgs = @(
    "--username", $Username,
    "--version", $Version,
    "--gameDir", $GameDir,
    "--assetsDir", (Join-Path $Install "assets"),
    "--assetIndex", $base.assetIndex.id,
    "--uuid", "00000000000000000000000000000000",
    "--accessToken", "0",
    "--userType", "legacy",
    "--versionType", "release",
    "--width", $Width,
    "--height", $Height
)
$gameArgs += Expand-Args $neo.arguments.game
if ($QuickPlay) { $gameArgs += @("--quickPlaySingleplayer", $QuickPlay) }

New-Item -ItemType Directory -Force (Join-Path $GameDir "mods") | Out-Null

$java = Join-Path $Install "java\java-runtime-delta\bin\java.exe"
if (-not (Test-Path $java)) { $java = Join-Path $Install "java\Jre_21\bin\java.exe" }
if (-not (Test-Path $java)) { throw "no java runtime found under $Install\java" }

$all = $jvmArgs + @("-cp", ($classpath -join ";"), $neo.mainClass) + $gameArgs

$log = Join-Path $GameDir "launch.log"
$errLog = Join-Path $GameDir "launch.err.log"

Write-Output "java:    $java"
Write-Output "gameDir: $GameDir"
Write-Output "mods:    $((Get-ChildItem (Join-Path $GameDir 'mods') -Filter *.jar | ForEach-Object { $_.Name }) -join ', ')"
Write-Output "log:     $log"

$proc = Start-Process -FilePath $java -ArgumentList $all -WorkingDirectory $GameDir `
    -RedirectStandardOutput $log -RedirectStandardError $errLog -PassThru
Write-Output "launched pid $($proc.Id)"

