package dev.reachin.client;

import dev.reachin.BindPayload;
import dev.reachin.MenuBinding;
import dev.reachin.ReachIn;
import dev.reachin.ReachInMenu;
import dev.reachin.ShulkerSlot;
import dev.reachin.mixin.SlotAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The grid runs across two passes of one frame.
 *
 * {@link #place} runs on the background event, before vanilla's slot loop, so the
 * cells are already where the panel is by the time vanilla hit-tests them and works
 * out the hovered slot.
 *
 * {@link #draw} runs on the foreground event, after the parent screen has drawn its
 * own slots and labels, so the panel is always on top no matter where it lands. That
 * is why the cells' own vanilla rendering is suppressed and the items are drawn here
 * instead - drawn by the slot loop they would end up underneath the parent GUI.
 */
public final class GridRenderer {
    private static final int CELL = 18;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int PAD = 8;
    private static final int HEADER = 16;
    public static final int PANEL_W = PAD * 2 + COLS * CELL;
    public static final int PANEL_H = HEADER + ROWS * CELL + PAD;

    /** How far outside the panel and the shulker's own slot the cursor may stray. */
    private static final int STICKY = 8;

    /** Clear of the GUI on either side, or failing that centred over it. */
    private static final int GAP = 6;

    private static final int BORDER = 0xFF000000;
    private static final int FACE = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int CELL_FACE = 0xFF8B8B8B;
    private static final int CELL_DARK = 0xFF373737;
    private static final int LABEL_DARK = 0x404040;
    private static final int LABEL_LIGHT = 0xE0E0E0;

    /**
     * An undyed shulker box has no DyeColor but its texture is purple, so it is
     * tinted to match rather than left grey. The title says which box it is, so
     * looking like a purple one costs nothing.
     */
    private static final int UNDYED = 0x8A4E9E;

    // Kept low on purpose. The panel should read as a vanilla GUI that happens to
    // carry the box's colour, not as a slab of dye - and the bevels have to stay
    // far enough apart in luminance to still look like bevels.
    private static final float TINT_FACE = 0.30F;
    private static final float TINT_BEVEL = 0.18F;
    private static final float TINT_CELL = 0.25F;

    private GridRenderer() {
    }

    /** Background pass: decide what is bound and move the cells onto the panel. */
    public static void place(AbstractContainerMenu menu, int mouseX, int mouseY,
                             int leftPos, int topPos, int imageWidth, int screenW, int screenH) {
        ReachInMenu reachIn = (ReachInMenu) menu;
        MenuBinding binding = reachIn.reachin$binding();

        Slot hovered = hovered(menu, mouseX - leftPos, mouseY - topPos);
        boolean hoveringCell = hovered instanceof ShulkerSlot;

        if (!hoveringCell && hovered != null
                && ReachIn.isShulker(hovered.getItem())
                && hovered.index != binding.hostIndex) {
            reachIn.reachin$install();
            if (reachIn.reachin$bind(hovered.index)) {
                send(new BindPayload(menu.containerId, hovered.index));
            }
        }

        if (!binding.bound()) {
            if (binding.hostIndex >= 0) {
                unbind(menu);
            }
            return;
        }

        int panelX = leftPos + imageWidth + GAP;
        if (panelX + PANEL_W > screenW - 2) {
            panelX = leftPos - PANEL_W - GAP;
        }
        if (panelX < 2) {
            // No room either side. The panel draws above the GUI, so overlapping it
            // centred reads better than jamming it against the screen edge.
            panelX = leftPos + (imageWidth - PANEL_W) / 2;
        }
        panelX = Math.max(2, Math.min(panelX, Math.max(2, screenW - PANEL_W - 2)));
        int panelY = Math.max(2, Math.min(topPos + GAP, Math.max(2, screenH - PANEL_H - 2)));

        Slot host = binding.view.host();
        if (!sticky(menu, mouseX, mouseY, panelX, panelY, leftPos + host.x - 1, topPos + host.y - 1)) {
            unbind(menu);
            return;
        }

        binding.panelX = panelX;
        binding.panelY = panelY;

        // Vanilla reads slot.x/y relative to leftPos/topPos and treats them as the
        // inner 16x16, so each cell's border sits one pixel out from them.
        int gridX = panelX - leftPos + PAD + 1;
        int gridY = panelY - topPos + HEADER + 1;
        for (int i = 0; i < ReachIn.SLOTS; i++) {
            Slot cell = menu.slots.get(binding.base + i);
            ((SlotAccessor) cell).reachin$setX(gridX + (i % COLS) * CELL);
            ((SlotAccessor) cell).reachin$setY(gridY + (i / COLS) * CELL);
        }
    }

    /**
     * Foreground pass: draw the panel, the cells and their items.
     *
     * The pose is already translated by (leftPos, topPos) here, which is the same
     * space slot.x/y live in.
     */
    public static void draw(AbstractContainerMenu menu, GuiGraphics graphics,
                            int mouseX, int mouseY, int leftPos, int topPos) {
        MenuBinding binding = ((ReachInMenu) menu).reachin$binding();
        if (!binding.bound() || !binding.installed()) {
            return;
        }

        int x = binding.panelX - leftPos;
        int y = binding.panelY - topPos;

        // Two things fight the panel here. Items and the player preview go through a
        // deferred buffer source that batches by render type rather than draw order,
        // so the queue has to be committed first. And the preview is drawn with depth
        // testing, so being later in the frame is not enough to be in front - the
        // panel needs its own Z. 200 clears the screen's own items while staying
        // under vanilla's tooltip layer at 400.
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);

        ItemStack hostStack = binding.view.host().getItem();
        int dye = dyeOf(hostStack);
        int face = tint(FACE, dye, TINT_FACE);
        int bevelLight = tint(BEVEL_LIGHT, dye, TINT_BEVEL);
        int bevelDark = tint(BEVEL_DARK, dye, TINT_FACE);
        int cellFace = tint(CELL_FACE, dye, TINT_CELL);
        int cellDark = tint(CELL_DARK, dye, TINT_CELL);

        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, BORDER);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, face);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 2, y + 2, bevelLight);
        graphics.fill(x + 1, y + 1, x + 2, y + PANEL_H - 2, bevelLight);
        graphics.fill(x + 2, y + PANEL_H - 2, x + PANEL_W - 1, y + PANEL_H - 1, bevelDark);
        graphics.fill(x + PANEL_W - 2, y + 2, x + PANEL_W - 1, y + PANEL_H - 1, bevelDark);

        Font font = Minecraft.getInstance().font;
        String name = font.plainSubstrByWidth(hostStack.getHoverName().getString(), PANEL_W - PAD * 2);
        graphics.drawString(font, name, x + PAD, y + 5, labelFor(face), false);

        for (int i = 0; i < ReachIn.SLOTS; i++) {
            int cx = x + PAD + (i % COLS) * CELL;
            int cy = y + HEADER + (i / COLS) * CELL;
            graphics.fill(cx, cy, cx + CELL, cy + CELL, cellFace);
            graphics.fill(cx, cy, cx + CELL - 1, cy + 1, cellDark);
            graphics.fill(cx, cy, cx + 1, cy + CELL - 1, cellDark);
            graphics.fill(cx + 1, cy + CELL - 1, cx + CELL, cy + CELL, bevelLight);
            graphics.fill(cx + CELL - 1, cy + 1, cx + CELL, cy + CELL, bevelLight);
        }

        // Commit the panel before the items go in, for the same batching reason.
        graphics.flush();

        int relX = mouseX - leftPos;
        int relY = mouseY - topPos;
        for (int i = 0; i < ReachIn.SLOTS; i++) {
            Slot cell = menu.slots.get(binding.base + i);
            ItemStack stack = cell.getItem();
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, cell.x, cell.y);
                graphics.renderItemDecorations(font, stack, cell.x, cell.y);
            }
            if (relX >= cell.x - 1 && relX < cell.x + 17 && relY >= cell.y - 1 && relY < cell.y + 17) {
                AbstractContainerScreen.renderSlotHighlight(graphics, cell.x, cell.y, 0);
            }
        }

        graphics.flush();
        graphics.pose().popPose();
    }

    /** The dye colour of a shulker box item, or the undyed box's own purple. */
    private static int dyeOf(ItemStack stack) {
        DyeColor dye = ShulkerBoxBlock.getColorFromItem(stack.getItem());
        return dye == null ? UNDYED : dye.getTextureDiffuseColor() & 0xFFFFFF;
    }

    /** Blends a vanilla GUI colour toward the dye, keeping the alpha it came with. */
    private static int tint(int argb, int dye, float amount) {
        int alpha = argb & 0xFF000000;
        int r = Math.round((((argb >> 16) & 0xFF) * (1 - amount)) + (((dye >> 16) & 0xFF) * amount));
        int g = Math.round((((argb >> 8) & 0xFF) * (1 - amount)) + (((dye >> 8) & 0xFF) * amount));
        int b = Math.round(((argb & 0xFF) * (1 - amount)) + ((dye & 0xFF) * amount));
        return alpha | (r << 16) | (g << 8) | b;
    }

    /** Black text on a light panel, near-white on a dark one. */
    private static int labelFor(int face) {
        int r = (face >> 16) & 0xFF;
        int g = (face >> 8) & 0xFF;
        int b = face & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 140.0 ? LABEL_DARK : LABEL_LIGHT;
    }

    /** True while the cursor is in the box spanning the shulker's slot and the panel. */
    private static boolean sticky(AbstractContainerMenu menu, int mouseX, int mouseY,
                                  int panelX, int panelY, int hostX, int hostY) {
        if (!menu.getCarried().isEmpty()) {
            return true;
        }
        int x0 = Math.min(panelX, hostX) - STICKY;
        int y0 = Math.min(panelY, hostY) - STICKY;
        int x1 = Math.max(panelX + PANEL_W, hostX + CELL) + STICKY;
        int y1 = Math.max(panelY + PANEL_H, hostY + CELL) + STICKY;
        return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
    }

    private static Slot hovered(AbstractContainerMenu menu, int mouseX, int mouseY) {
        Slot cell = cellAt(menu, mouseX, mouseY);
        if (cell != null) {
            return cell;
        }
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            if (over(slot, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean over(Slot slot, double relX, double relY) {
        return relX >= slot.x - 1 && relX < slot.x + 17
                && relY >= slot.y - 1 && relY < slot.y + 17;
    }

    /**
     * The grid cell under the cursor, in coordinates relative to the GUI origin.
     *
     * The panel can sit over the parent screen's own slots, and vanilla's findSlot
     * returns the first match in menu order - which is always a parent slot, since
     * the cells are appended last. So the cells have to be checked first, both here
     * and in the screen's hit-testing.
     */
    public static Slot cellAt(AbstractContainerMenu menu, double relX, double relY) {
        MenuBinding binding = ((ReachInMenu) menu).reachin$binding();
        if (!binding.bound() || !binding.installed()) {
            return null;
        }
        for (int i = 0; i < ReachIn.SLOTS; i++) {
            Slot cell = menu.slots.get(binding.base + i);
            if (over(cell, relX, relY)) {
                return cell;
            }
        }
        return null;
    }

    /** True if the point is anywhere on the panel, cells or not. */
    public static boolean inPanel(AbstractContainerMenu menu, double relX, double relY,
                                  int leftPos, int topPos) {
        MenuBinding binding = ((ReachInMenu) menu).reachin$binding();
        if (!binding.bound() || !binding.installed()) {
            return false;
        }
        int x = binding.panelX - leftPos;
        int y = binding.panelY - topPos;
        return relX >= x && relX < x + PANEL_W && relY >= y && relY < y + PANEL_H;
    }

    /** Returns true if a grid was open and has now been closed. */
    public static boolean unbind(AbstractContainerMenu menu) {
        ReachInMenu reachIn = (ReachInMenu) menu;
        if (reachIn.reachin$binding().hostIndex < 0) {
            return false;
        }
        reachIn.reachin$unbind();
        send(BindPayload.unbind(menu.containerId));
        return true;
    }

    private static void send(BindPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
