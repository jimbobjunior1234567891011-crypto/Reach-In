# Captures a choreographed demo of the grid in use and encodes it to a GIF for the
# site. Assumes the isolated client from run-client.ps1 at 1280x720, GUI scale 2.

param(
    [string]$Run = "C:\Reach-In\run",
    [string]$Frames = "C:\Reach-In\run\demo",
    [string]$Out = "C:\Reach-In\docs\brand\demo.gif",
    [switch]$SkipLaunch
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\mcctl.ps1"

Remove-Item $Frames -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $Frames | Out-Null

function Wait-Log([string]$Pattern, [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path "$Run\launch.log") {
            if (Select-String -Path "$Run\launch.log" -Pattern $Pattern -Quiet -ErrorAction SilentlyContinue) { return }
        }
        Start-Sleep -Milliseconds 1500
    }
    throw "timed out waiting for '$Pattern'"
}

$S = 2; $LEFT = 232; $TOP = 97
$PANEL_X = $LEFT + 176 + 6
$PANEL_Y = $TOP + 6
function Pt([int]$x, [int]$y) { return [pscustomobject]@{ X = $x * $S; Y = $y * $S } }
function Hotbar([int]$i) { return Pt ($LEFT + 8 + $i * 18 + 8) ($TOP + 142 + 8) }
function Cell([int]$i) {
    $col = $i % 9; $row = [int][Math]::Floor($i / 9)
    return Pt ($PANEL_X + 9 + $col * 18 + 8) ($PANEL_Y + 17 + $row * 18 + 8)
}

$script:n = 0
function Cap([int]$times = 1) {
    for ($i = 0; $i -lt $times; $i++) {
        $script:n++
        Save-Shot (Join-Path $Frames ("frame_{0:d3}.png" -f $script:n)) | Out-Null
    }
}

# Walks the cursor between two points so the recording reads as a hand moving,
# not as teleporting.
function Glide($from, $to, [int]$steps) {
    for ($i = 1; $i -le $steps; $i++) {
        $x = [int]($from.X + ($to.X - $from.X) * $i / $steps)
        $y = [int]($from.Y + ($to.Y - $from.Y) * $i / $steps)
        Move-McMouse $x $y
        Cap
    }
}

if (-not $SkipLaunch) {
    Get-Process java -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -like "*curseforge*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    Remove-Item "$Run\launch.log", "$Run\launch.err.log" -Force -ErrorAction SilentlyContinue
    & "$PSScriptRoot\run-client.ps1" -QuickPlay "reachin" | Out-Null
    Write-Output "loading world"
    Wait-Log "joined the game"
    Start-Sleep -Seconds 4
}

[void](Focus-Mc)
Start-Sleep -Milliseconds 800
Send-Command "/gamemode survival"
Send-Command "/clear"
Send-Command "/give @s shulker_box"
Send-Command "/give @s diamond 64"
Send-Command "/give @s redstone 32"
Start-Sleep -Milliseconds 800

Press-Key 0x45 80
Start-Sleep -Milliseconds 1000

$neutral = [pscustomobject]@{ X = 640; Y = 380 }
Move-McMouse $neutral.X $neutral.Y
Cap 3

Write-Output "hover the shulker"
Glide $neutral (Hotbar 0) 6
Cap 4

Write-Output "pick up the diamonds"
Glide (Hotbar 0) (Hotbar 1) 3
Click-Mc (Hotbar 1).X (Hotbar 1).Y
Cap 2

Write-Output "drop them into the grid"
Glide (Hotbar 1) (Cell 0) 7
Cap 2
Click-Mc (Cell 0).X (Cell 0).Y
Cap 5

Write-Output "shift-click back out"
Shift-Click (Cell 0).X (Cell 0).Y
Cap 6

Write-Output "captured $script:n frames"
