package dev.reachin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * The grid hooks NeoForge's own container render events rather than mixing into
 * AbstractContainerScreen.render, which NeoForge patches heavily.
 */
public final class ClientEvents {
    private ClientEvents() {
    }

    /** Before the slot loop, so vanilla hit-tests the cells where the panel is. */
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        var window = Minecraft.getInstance().getWindow();
        GridRenderer.place(
                screen.getMenu(),
                event.getMouseX(),
                event.getMouseY(),
                screen.getGuiLeft(),
                screen.getGuiTop(),
                screen.getXSize(),
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight());
    }

    /** After the parent screen's own slots and labels, so the panel is always on top. */
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GridRenderer.draw(
                screen.getMenu(),
                event.getGuiGraphics(),
                event.getMouseX(),
                event.getMouseY(),
                screen.getGuiLeft(),
                screen.getGuiTop());
    }
}
