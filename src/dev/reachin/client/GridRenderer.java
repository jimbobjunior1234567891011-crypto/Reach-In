package dev.reachin.client;

import dev.reachin.BindPayload;
import dev.reachin.MenuBinding;
import dev.reachin.ReachIn;
import dev.reachin.ReachInMenu;
import dev.reachin.mixin.SlotAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Decides what the grid is bound to, puts its cells where the panel is, and
 * draws the panel underneath them.
 *
 * Runs once a frame, right after the screen background and before vanilla's
 * slot loop, so the real cells and their items land on top of the panel.
 */
public final class GridRenderer {
    private static final int CELL = 18;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int PAD = 8;
    private static final int HEADER = 16;
    private static final int PANEL_W = PAD * 2 + COLS * CELL;
    private static final int PANEL_H = HEADER + ROWS * CELL + PAD;

    /** How far outside the panel and the shulker's own slot the cursor may stray. */
    private static final int STICKY = 8;

    private static final int BORDER = 0xFF000000;
    private static final int FACE = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int CELL_FACE = 0xFF8B8B8B;
    private static final int CELL_DARK = 0xFF373737;
    private static final int LABEL = 0x404040;

    private GridRenderer() {
    }

    public static void frame(AbstractContainerMenu menu, GuiGraphics graphics, int mouseX, int mouseY,
                             int leftPos, int topPos, int imageWidth, int screenW, int screenH) {
        ReachInMenu reachIn = (ReachInMenu) menu;
        MenuBinding binding = reachIn.reachin$binding();

        Slot hovered = hovered(menu, mouseX - leftPos, mouseY - topPos);
        boolean hoveringCell = hovered != null && binding.installed() && hovered.index >= binding.base;

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

        int panelX = leftPos + imageWidth + 6;
        if (panelX + PANEL_W > screenW - 2) {
            panelX = leftPos - PANEL_W - 6;
        }
        panelX = Math.max(2, Math.min(panelX, screenW - PANEL_W - 2));
        int panelY = Math.max(2, Math.min(topPos + 6, screenH - PANEL_H - 2));

        Slot host = binding.view.host();
        if (!sticky(menu, mouseX, mouseY, panelX, panelY, leftPos + host.x - 1, topPos + host.y - 1)) {
            unbind(menu);
            return;
        }

        // Vanilla reads slot.x/y relative to leftPos/topPos and treats them as the
        // inner 16x16, so each cell's border sits one pixel out from them.
        int gridX = panelX - leftPos + PAD + 1;
        int gridY = panelY - topPos + HEADER + 1;
        for (int i = 0; i < ReachIn.SLOTS; i++) {
            Slot cell = menu.slots.get(binding.base + i);
            ((SlotAccessor) cell).reachin$setX(gridX + (i % COLS) * CELL);
            ((SlotAccessor) cell).reachin$setY(gridY + (i / COLS) * CELL);
        }

        draw(graphics, panelX, panelY, host.getItem());
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
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            if (mouseX >= slot.x - 1 && mouseX < slot.x + 17
                    && mouseY >= slot.y - 1 && mouseY < slot.y + 17) {
                return slot;
            }
        }
        return null;
    }

    private static void draw(GuiGraphics graphics, int x, int y, ItemStack host) {
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, BORDER);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, FACE);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 2, y + 2, BEVEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + PANEL_H - 2, BEVEL_LIGHT);
        graphics.fill(x + 2, y + PANEL_H - 2, x + PANEL_W - 1, y + PANEL_H - 1, BEVEL_DARK);
        graphics.fill(x + PANEL_W - 2, y + 2, x + PANEL_W - 1, y + PANEL_H - 1, BEVEL_DARK);

        Font font = Minecraft.getInstance().font;
        String name = font.plainSubstrByWidth(host.getHoverName().getString(), PANEL_W - PAD * 2);
        graphics.drawString(font, name, x + PAD, y + 5, LABEL, false);

        for (int i = 0; i < ReachIn.SLOTS; i++) {
            int cx = x + PAD + (i % COLS) * CELL;
            int cy = y + HEADER + (i / COLS) * CELL;
            graphics.fill(cx, cy, cx + CELL, cy + CELL, CELL_FACE);
            graphics.fill(cx, cy, cx + CELL - 1, cy + 1, CELL_DARK);
            graphics.fill(cx, cy, cx + 1, cy + CELL - 1, CELL_DARK);
            graphics.fill(cx + 1, cy + CELL - 1, cx + CELL, cy + CELL, BEVEL_LIGHT);
            graphics.fill(cx + CELL - 1, cy + 1, cx + CELL, cy + CELL, BEVEL_LIGHT);
        }
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
