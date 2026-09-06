<p align="center">
  <img src="docs/brand/logo.png" alt="Reach-In" width="620">
</p>

<p align="center">
  <b>Hover a shulker box. Reach into it. Never place it down again.</b>
</p>

<p align="center">
  Minecraft 1.21.1 · NeoForge 21.1.x · required on client and server
</p>

---

## What it does

Hover a shulker box anywhere in an inventory and its contents open as a live
9×3 grid beside the screen. Click, right-click, shift-click, drag and
double-click straight into and out of it — the box never leaves your inventory.

The grid is sticky. It stays put while you move toward it, and it never closes
while you're carrying an item. Escape closes the grid without closing the
screen.

The panel takes the colour of the box you're looking into, so a wall of shulkers
stays legible at a glance.

Works in your inventory, chests, barrels, ender chests, and every other
container screen.

## Why it needs the server

Vanilla has no packet for moving an item inside a shulker box that is itself
sitting in an inventory. A client-only mod can *show* you the contents, but any
move it makes is a lie the next server sync erases.

So Reach-In ships on both sides and is required on both. Every move is applied
and validated by the server.

## How it works

The 27 grid cells are **real `Slot` objects appended to the open menu**, not a
custom widget.

Vanilla finds slots by their `x`/`y` and routes every click type through them.
Moving those slots' coordinates to the grid position is enough to make
vanilla's own mouse handling, click semantics, drag distribution and
shift-click work on the shulker's contents. Nothing is reimplemented, so
nothing behaves subtly wrong.

The cells back onto a `Container` view over the shulker item's `CONTAINER`
data component. They are appended the first time a menu is used this way, not
at construction, and go inactive rather than being removed — which in vanilla
means "don't render, don't hit-test".

### Shift-click rules

| From | Goes to |
|------|---------|
| A grid cell | Your main inventory and hotbar |
| A player inventory slot | The shulker |
| A chest or barrel slot | Vanilla behaviour, unchanged |
| The shulker box itself | Vanilla behaviour, unchanged |

### Safety

Every grid operation revalidates that the host slot still holds a shulker box.
If it doesn't, the cells go inactive and clicks are refused. Shulker boxes
can't be put inside shulker boxes, matching vanilla.

While vanilla's own `moveItemStackTo` runs, the grid refuses placement —
`ChestMenu` bounds its quick-move range with `slots.size()`, which would
otherwise quietly spill chest items into the shulker.

## Compatibility

While the grid is open, the shulker's own tooltip is suppressed so it doesn't
cover the panel. That also hides
[Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip)'s preview,
which renders through the same pipeline, so the two never stack. Keep it
installed or don't — either works.

## Layout

The panel sits to the right of the GUI, flipping to the left when there's no room
and centring over the GUI when neither side fits. At GUI scale 3 on a 720p window
nothing fits beside a 176-wide GUI, so it overlays — still fully usable, since
hit-testing prefers the grid, but scale 2 or a larger window is where it looks
right.

## Building

```powershell
.\build.ps1
```

Produces `dist/reachin-0.1.0.jar`.

No Gradle. NeoForge 21.1 runs on Mojang mappings, and a CurseForge/NeoForge
install already carries every jar needed to compile against, so `javac` + `jar`
is the whole toolchain and no refmap is generated. Override `-Install`,
`-NeoForgeVersion`, `-McLibVersion` or `-Jdk` if your paths differ.

## Testing

There's no Gradle here and so no Minecraft test harness. The test is the game
itself, driven from the terminal:

```powershell
.\tools\smoke.ps1
```

That launches an isolated client with only this mod loaded, loads a test world,
and drives twelve steps with real mouse and keyboard input, saving a screenshot
of each. The one that actually proves anything is
`/data get entity @s SelectedItem` — it reads the shulker back through vanilla's
own command, so the contents have to really be on the item's `CONTAINER`
component rather than just something the panel is drawing.

Add `-Trace` to log every slot click with the binding state.

## Design

[`docs/specs/2026-09-05-reach-in-design.md`](docs/specs/2026-09-05-reach-in-design.md)
· [smoke results](docs/smoke/2026-09-05-reachin-v0.1-smoke.md)

## Licence

MIT
