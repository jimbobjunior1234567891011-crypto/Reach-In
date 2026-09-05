package dev.reachin.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.reachin.MenuBinding;
import dev.reachin.ReachInMenu;
import dev.reachin.client.GridRenderer;
import net.minecraft.client.Minecraft;
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

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;

    @Shadow protected Slot hoveredSlot;

    @Shadow public abstract AbstractContainerMenu getMenu();

    /**
     * While the grid is open, the shulker's own tooltip would cover it. Suppressing
     * it here also removes Shulker Box Tooltip's preview, which renders through the
     * same pipeline, so the two never stack.
     */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void reachin$renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        MenuBinding binding = ((ReachInMenu) getMenu()).reachin$binding();
        if (binding.bound() && this.hoveredSlot != null && this.hoveredSlot.index == binding.hostIndex) {
            ci.cancel();
        }
    }

    /**
     * Right after the screen background, before vanilla's slot loop. The panel has
     * to be drawn here so the real grid cells render on top of it.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.AFTER))
    private void reachin$render(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        Window window = Minecraft.getInstance().getWindow();
        GridRenderer.frame(getMenu(), graphics, mouseX, mouseY,
                this.leftPos, this.topPos, this.imageWidth,
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
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
