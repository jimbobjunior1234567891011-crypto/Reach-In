package dev.reachin.mixin;

import dev.reachin.MenuBinding;
import dev.reachin.ReachInMenu;
import dev.reachin.ShulkerSlot;
import dev.reachin.client.GridRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only the bits NeoForge has no event for. The panel itself is drawn from
 * ContainerScreenEvent.Render.Background - see {@link dev.reachin.client.ClientEvents}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Shadow public abstract AbstractContainerMenu getMenu();

    /**
     * The panel can overlap the parent screen's own slots, and vanilla returns the
     * first match in menu order - always a parent slot, since the cells are appended
     * last. Without this, clicking a cell that happens to sit over an armour slot
     * operates on the armour slot instead.
     *
     * Points on the panel but not on a cell resolve to nothing, so the panel does
     * not act as a window into whatever it covers.
     */
    @Inject(method = "findSlot", at = @At("HEAD"), cancellable = true)
    private void reachin$findSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        double relX = mouseX - this.leftPos;
        double relY = mouseY - this.topPos;
        Slot cell = GridRenderer.cellAt(getMenu(), relX, relY);
        if (cell != null) {
            cir.setReturnValue(cell);
        } else if (GridRenderer.inPanel(getMenu(), relX, relY, this.leftPos, this.topPos)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * While the grid is open, the shulker's own tooltip would cover it. Suppressing
     * it here also removes Shulker Box Tooltip's preview, which renders through the
     * same pipeline, so the two never stack.
     */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void reachin$renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        MenuBinding binding = ((ReachInMenu) getMenu()).reachin$binding();
        if (!binding.bound()) {
            return;
        }
        if (this.hoveredSlot != null && this.hoveredSlot.index == binding.hostIndex) {
            ci.cancel();
            return;
        }
        // On the panel but not on a cell: whatever the panel covers must not show
        // its tooltip through it.
        double relX = mouseX - this.leftPos;
        double relY = mouseY - this.topPos;
        if (GridRenderer.cellAt(getMenu(), relX, relY) == null
                && GridRenderer.inPanel(getMenu(), relX, relY, this.leftPos, this.topPos)) {
            ci.cancel();
        }
    }

    /**
     * The grid's cells are real slots, so vanilla's slot loop would draw them - but
     * that loop runs before the parent screen's own slots and labels, leaving the
     * panel's contents underneath them. GridRenderer draws these in the foreground
     * pass instead.
     */
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void reachin$renderSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (slot instanceof ShulkerSlot) {
            ci.cancel();
        }
    }

    /** Same reason: the grid draws its own hover highlight in the foreground pass. */
    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void reachin$renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        if (slot instanceof ShulkerSlot) {
            ci.cancel();
        }
    }

    /** Escape closes the grid first, the screen second. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void reachin$keyPressed(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_ESCAPE && GridRenderer.unbind(getMenu())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void reachin$removed(CallbackInfo ci) {
        GridRenderer.unbind(getMenu());
    }
}
