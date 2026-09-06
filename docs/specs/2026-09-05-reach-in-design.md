# Shulker Tooltips: Reach-In — design

**Date:** 2026-09-05
**Target:** NeoForge 21.1.x, Minecraft 1.21.1, Java 21
**Sides:** both. Client draws and reads input; the server owns every item.

## Problem

Hovering a shulker box in an inventory shows you what is inside, but you
cannot touch it. To move a single item you place the box, open it, take the
item, break the box. Existing mods either preview only (Shulker Box Tooltip)
or replace the screen (ShulkerBoxSlot). Neither lets you reach into the box
while it sits in your inventory.

## What it does

Hover a shulker box in any container screen. A 9x3 grid appears beside the
GUI, drawn in the vanilla panel style. The grid is live: click, right-click,
shift-click, drag, double-click-collect and hotbar-number-swap all work, on
the real contents, validated by the server.

The grid is sticky. It stays while the cursor is anywhere in the box formed
by the shulker's slot and the grid, so you can move toward it without it
vanishing. It never closes while you are carrying an item. Escape closes it
without closing the screen.

## Why the server has to be involved

Vanilla has no packet for moving an item inside a shulker that is itself in
an inventory. A client-only mod can render a preview but any move it makes
is a lie the next server sync erases. So Reach-In ships on both sides and is
required on both.

## Architecture

The trick is that the 27 grid cells are **real `Slot` objects appended to the
open menu**, not a custom widget. Vanilla finds slots by their `x`/`y` and
routes every click type through them. Moving those slots' coordinates to the
grid position is enough to make vanilla's own mouse handling, click
semantics, drag distribution and shift-click work on the shulker — nothing is
reimplemented.

### ShulkerView

A `Container` over one `ItemStack`'s `DataComponents.CONTAINER`
(`ItemContainerContents`, 27 slots).

Contents are cached in a `NonNullList` so vanilla can mutate stacks in place,
which the whole slot system assumes. `setChanged()` writes a fresh
`ItemContainerContents` back to the host stack and marks the host slot dirty.
The instance just written is remembered; if the component is later replaced
by something else — a server sync, a hopper, another player — the identity
check fails and the cache reloads. That is the only desync path and it
closes itself.

Refuses shulker-inside-shulker, matching vanilla.

### Menu augmentation

27 `ShulkerSlot`s are appended to a menu the first time that menu binds, not
at construction — a menu nobody uses this feature on is untouched. Both sides
append exactly 27 at the same point, so indices agree.

Unbinding does not remove them; it marks them inactive, which in vanilla
means both "do not render" and "cannot be hovered or clicked". Rebinding to
another shulker reuses the same slots.

### Binding

One C2S payload, `{containerId, hostIndex}`, with `hostIndex < 0` meaning
unbind. The server checks the id matches the open menu, that the index is in
the parent range, and that the stack is really a shulker box before pointing
the view at it.

The client binds locally at the same moment so the grid is live on the frame
it appears. The server's normal `broadcastChanges` then overwrites the grid
with authoritative contents, so a wrong guess corrects itself immediately.

### Shift-click rules

- From a grid cell -> into the player's main inventory and hotbar. Not simply
  "everything before the grid": in the player's own menu that range also covers
  the crafting slots, the armour slots and the offhand, and since the move runs
  in reverse it reaches the offhand first. Taking an item out of a shulker put
  it in your offhand until this was narrowed to container indices below 36.
- From a player inventory slot -> into the shulker.
- From a container slot (chest, barrel) -> unchanged vanilla behaviour.
- The shulker box itself -> unchanged vanilla behaviour.

While vanilla's own `moveItemStackTo` runs, an `autoGuard` flag makes the
grid cells refuse placement. Without it, `ChestMenu.quickMoveStack` — which
bounds its range with `slots.size()` — would quietly spill chest items into
the shulker.

### Guards

Every grid operation revalidates that the host slot still holds a shulker
box. If it does not, the view reports invalid, the cells go inactive, and
clicks are refused. The client unbinds on screen close and on Escape.

Identity of the host *stack object* is deliberately not checked: server syncs
replace that object routinely, and treating that as tampering would unbind
after every single move.

## Rendering

The grid is drawn from `fill` calls in the vanilla GUI palette — panel face
`#C6C6C6` with the standard bevel, slot cells `#8B8B8B` with the standard
inset. No texture, no new asset, and it sits correctly in both the default
look and most resource packs' colour range.

Each colour is then blended toward the box's dye — 30% on the faces, 18% on the
light bevels. Low on purpose: enough that a wall of shulkers is legible at a
glance, not so much that the panel stops reading as a Minecraft GUI. The bevels
have to keep their luminance spacing or they stop looking like bevels, which is
why the highlight is tinted less than the face.

An undyed box has no `DyeColor` but its texture is purple, so it is tinted to
match rather than left grey; the title names the box, so looking like a purple
one costs nothing. The title switches between dark and near-white text based on
the tinted face's luminance, so a black box's panel stays readable.

The panel anchors to the right edge of the GUI, flipping left when there is no
room, and centring over the GUI when neither side fits. At GUI scale 3 on a
720p window nothing fits, so the overlay case is normal rather than an edge
case, and the panel has to draw above the parent screen rather than under it.

Two NeoForge events, not mixins into `AbstractContainerScreen.render` —
NeoForge patches that method so it never calls `super.render`, leaving nothing
stable to inject at.

- `ContainerScreenEvent.Render.Background` runs before the slot loop. Binding
  and cell placement happen there, so vanilla hit-tests the cells where the
  panel actually is, in the same frame.
- `ContainerScreenEvent.Render.Foreground` runs after the parent screen's own
  slots and labels. All drawing happens there, which is why the cells' vanilla
  rendering is suppressed and their items are drawn by hand.

Being later in the frame is not enough to be in front. Items and the player
preview go through a deferred buffer source that batches by render type rather
than draw order, and the preview is depth-tested. So the panel commits that
queue with `flush()` and draws at Z 200 — above the screen's own items, below
vanilla's tooltip layer at 400.

## Hit-testing

Vanilla's `findSlot` returns the first slot in menu order whose box contains
the cursor. The cells are appended last, so wherever the panel overlaps the
parent GUI a parent slot always wins — clicking a cell that sits over the
armour column moves armour instead.

So `findSlot` checks the cells first. Points on the panel that are not on a
cell resolve to nothing, so the panel is never a window into what it covers,
and the same rule keeps tooltips from leaking through it.

## Scope

**In:** player inventory and every container screen. Left-click, right-click
and shift-click are the tested surface; drag-distribute, double-click-collect
and hotbar swap come along free from vanilla and ship untested.

**Out of v1:** creative inventory, a keybind to open the grid with no screen
open, nesting, search, sorting.

## Testing

The build is javac against a NeoForge install, with no Gradle and therefore no
Minecraft test harness. So the test is the game itself, driven from the
terminal rather than by hand: `tools/run-client.ps1` launches an isolated
client with only this mod loaded, `tools/mcctl.ps1` sends real input and
captures the window, and `tools/smoke.ps1` sequences a twelve-step run with a
screenshot per step.

The step that actually proves it is `/data get entity @s SelectedItem`, which
reads the shulker back through vanilla's own command — the contents have to be
on the item's `CONTAINER` component, not just something the panel is drawing.

Results in `docs/smoke/`. Still uncovered: multiplayer with a second player on
the same shulker, and the vanilla click semantics that come along for free
(drag distribution, double-click collect, hotbar number swap).

## Build

`build.ps1` compiles with javac against the CurseForge NeoForge install and
jars to `dist/`. Same approach as FreeRot: NeoForge 21.1 runs on Mojang
mappings, so no remapping step and no refmap are needed.
