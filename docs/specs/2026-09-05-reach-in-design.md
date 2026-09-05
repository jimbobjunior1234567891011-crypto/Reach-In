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

- From a grid cell -> into the player inventory.
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

The panel anchors to the right edge of the GUI, flipping left when there is
no room and clamping to the screen. Anchoring to the GUI rather than the slot
keeps it from ever covering the parent screen's own slots.

Drawn immediately after the screen background and before vanilla's slot loop,
so the real cells and their items render on top of it.

## Scope

**In:** player inventory and every container screen. Left-click, right-click
and shift-click are the tested surface; drag-distribute, double-click-collect
and hotbar swap come along free from vanilla and ship untested.

**Out of v1:** creative inventory, a keybind to open the grid with no screen
open, nesting, search, sorting.

## Testing

The build is javac against a NeoForge install, with no Gradle and therefore
no Minecraft test harness. The gate is a compile-clean build plus a hand
smoke test in game:

1. Hover a shulker in your inventory — grid appears with correct contents.
2. Left-click an item out, left-click it back. Close and reopen. It persisted.
3. Right-click to split a stack inside the grid.
4. Shift-click a grid item — lands in the player inventory.
5. Shift-click an inventory item — lands in the shulker.
6. Open a chest. Shift-click a chest item — goes to the inventory, never
   into the shulker.
7. Escape closes the grid, not the screen.
8. Place the box down and confirm the contents match what the grid showed.
9. On the server with a second player, confirm no dupe when both interact.

## Build

`build.ps1` compiles with javac against the CurseForge NeoForge install and
jars to `dist/`. Same approach as FreeRot: NeoForge 21.1 runs on Mojang
mappings, so no remapping step and no refmap are needed.
