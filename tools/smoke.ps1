# End-to-end smoke test: launches the isolated client, loads the test world, sets
# up items and drives the Reach-In grid, saving a screenshot at each step.
#
# Assumes a 1280x720 client at GUI scale 3, which is what the run-client default
# window produces, and a world called "New World" that already exists.

param(
    [string]$Run = "C:\Reach-In\run",
    [string]$Out = "C:\Reach-In\run\smoke",
    [switch]$SkipLaunch,
    [switch]$Trace
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\mcctl.ps1"

New-Item -ItemType Directory -Force $Out | Out-Null

function Wait-Log([string]$Pattern, [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path "$Run\launch.log") {
            if (Select-String -Path "$Run\launch.log" -Pattern $Pattern -Quiet -ErrorAction SilentlyContinue) { return $true }
        }
        Start-Sleep -Milliseconds 1500
    }
    throw "timed out waiting for '$Pattern'"
}

# GUI scale 3 on a 1280x720 client: 426x240 virtual, inventory at (125, 37).
# GUI scale 2 on a 1280x720 client: 640x360 virtual. The inventory is 176x166 at
# (232, 97), which leaves room for the panel to sit beside it rather than over it.
$S = 2
$LEFT = 232
$TOP = 97
$PANEL_X = $LEFT + 176 + 6
$PANEL_Y = $TOP + 6

# A three-row chest is 176x168, so its GUI sits one pixel higher.
$CHEST_TOP = 96
$CHEST_PANEL_Y = $CHEST_TOP + 6

function Pt([int]$guiX, [int]$guiY) {
    return [pscustomobject]@{ X = $guiX * $S; Y = $guiY * $S }
}

# Player inventory hotbar slot centre.
function Hotbar([int]$i) {
    return Pt ($LEFT + 8 + $i * 18 + 8) ($TOP + 142 + 8)
}

# Main inventory slot centre (row 0-2, col 0-8).
function InvSlot([int]$row, [int]$col) {
    return Pt ($LEFT + 8 + $col * 18 + 8) ($TOP + 84 + $row * 18 + 8)
}

# Reach-In grid cell centre.
function Cell([int]$i) {
    $col = $i % 9
    $row = [int][Math]::Floor($i / 9)
    return Pt ($PANEL_X + 9 + $col * 18 + 8) ($PANEL_Y + 17 + $row * 18 + 8)
}

# Same, for the chest screen's slightly different GUI origin.
function ChestCell([int]$i) {
    $col = $i % 9
    $row = [int][Math]::Floor($i / 9)
    return Pt ($PANEL_X + 9 + $col * 18 + 8) ($CHEST_PANEL_Y + 17 + $row * 18 + 8)
}
# Chest storage slot centre (row 0-2, col 0-8).
function ChestSlot([int]$row, [int]$col) {
    return Pt ($LEFT + 8 + $col * 18 + 8) ($CHEST_TOP + 18 + $row * 18 + 8)
}
# Player hotbar inside the chest screen.
function ChestHotbar([int]$i) {
    return Pt ($LEFT + 8 + $i * 18 + 8) ($CHEST_TOP + 143 + 8)
}

function Shot([string]$name) {
    Start-Sleep -Milliseconds 500
    $p = Join-Path $Out "$name.png"
    Save-Shot $p | Out-Null
    Write-Output "  shot: $name"
}

if (-not $SkipLaunch) {
    Get-Process java -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -like "*curseforge*" } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Remove-Item "$Run\launch.log", "$Run\launch.err.log" -Force -ErrorAction SilentlyContinue
    # Quick play instead of clicking through the menus, so the run does not depend
    # on GUI scale. The world folder must have no spaces for the identifier to parse.
    & "$PSScriptRoot\run-client.ps1" -Trace:$Trace -QuickPlay "reachin" | Out-Null
    Write-Output "launched, loading world"
    Wait-Log "joined the game"
    Start-Sleep -Seconds 4
}

[void](Focus-Mc)
Start-Sleep -Milliseconds 800

# Chat only opens when no screen is up. If a screen is open, every character of a
# command lands as a keybind instead - "l" opens advancements, "e" the inventory -
# and the run is garbage from there. A fresh launch is the only state where that is
# guaranteed, which is why -SkipLaunch is for debugging only.
Write-Output "setting up"
Send-Command "/gamemode survival"
Send-Command "/clear"
Send-Command "/give @s shulker_box"
Send-Command "/give @s diamond 64"
Send-Command "/give @s redstone 64"
Start-Sleep -Milliseconds 800

Press-Key 0x45 80              # E - open inventory
Start-Sleep -Milliseconds 900

Write-Output "1. hover the shulker"
$p = Hotbar 0
Move-McMouse $p.X $p.Y
Shot "01-hover"

Write-Output "2. pick up diamonds, drop them in grid cell 0"
$p = Hotbar 1
Click-Mc $p.X $p.Y
$p = Cell 0
Click-Mc $p.X $p.Y
Shot "02-placed"

Write-Output "3. right-click cell 0 to split"
$p = Cell 1
Click-Mc $p.X $p.Y "right"
Shot "03-split"

# Escape is consumed by the grid, so it does not close the screen - toggle with E.
Write-Output "4. close and reopen, check it persisted"
Press-Key 0x45 80
Start-Sleep -Milliseconds 800
Press-Key 0x45 80
Start-Sleep -Milliseconds 900
$p = Hotbar 0
Move-McMouse $p.X $p.Y
Shot "04-persisted"

Write-Output "5. shift-click a grid item out"
$p = Cell 0
Shift-Click $p.X $p.Y
Shot "05-shift-out"

Write-Output "6. shift-click an inventory item in"
$p = Hotbar 2
Shift-Click $p.X $p.Y
Shot "06-shift-in"

Write-Output "7. escape closes the grid, not the screen"
Press-Key 0x1B 80
Shot "07-escape"

# Proves the contents really landed on the item's CONTAINER component, rather than
# only looking right in the grid.
Write-Output "8. read the shulker's real NBT back"
Press-Key 0x1B 80
Start-Sleep -Milliseconds 800
Press-Key 0x31 80              # select hotbar slot 1, the shulker
Start-Sleep -Milliseconds 400
Send-Command "/data get entity @s SelectedItem"
Start-Sleep -Milliseconds 800
Shot "08-nbt"

# The chest case is where the anti-spill guard matters: ChestMenu bounds its
# quick-move range with slots.size(), which now includes the grid.
Write-Output "9. open a chest with the grid available"
Send-Command "/tp @s ~ ~ ~ 0 0"
Send-Command "/setblock ~ ~1 ~2 chest[facing=north]"
Send-Command "/item replace block ~ ~1 ~2 container.0 with minecraft:emerald 32"
Start-Sleep -Milliseconds 800
[Native]::mouse_event(0x0008, 0, 0, 0, [IntPtr]::Zero)
Start-Sleep -Milliseconds 80
[Native]::mouse_event(0x0010, 0, 0, 0, [IntPtr]::Zero)
Start-Sleep -Milliseconds 1200
Shot "09-chest-open"

Write-Output "10. hover the shulker inside the chest screen"
$p = ChestHotbar 0
Move-McMouse $p.X $p.Y
Shot "10-chest-hover"

Write-Output "11. shift-click a chest item - must go to the inventory, not the shulker"
$p = ChestSlot 0 0
Shift-Click $p.X $p.Y
Shot "11-chest-shift"

Write-Output "12. shift-click a grid item out from the chest screen"
$p = ChestCell 0
Shift-Click $p.X $p.Y
Shot "12-chest-grid-out"

Write-Output "13. panel tint per shulker colour"
# Two escapes: the first is eaten by the grid, the second closes the chest screen.
# Chat will not open while a screen is up, and the command would leak as keybinds.
Press-Key 0x1B 80
Start-Sleep -Milliseconds 500
Press-Key 0x1B 80
Start-Sleep -Milliseconds 900
Send-Command "/clear"
$colours = @("shulker_box", "red_shulker_box", "lime_shulker_box", "light_blue_shulker_box",
             "black_shulker_box", "white_shulker_box", "yellow_shulker_box", "magenta_shulker_box")
foreach ($c in $colours) { Send-Command "/give @s $c" }
Start-Sleep -Milliseconds 800
Press-Key 0x45 80
Start-Sleep -Milliseconds 900
for ($i = 0; $i -lt $colours.Count; $i++) {
    $p = Hotbar $i
    Move-McMouse $p.X $p.Y
    Shot ("13-tint-{0}" -f $colours[$i])
}

Write-Output "done, screenshots in $Out"

