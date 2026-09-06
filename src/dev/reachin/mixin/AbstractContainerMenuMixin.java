package dev.reachin.mixin;

import dev.reachin.MenuBinding;
import dev.reachin.ReachIn;
import dev.reachin.ReachInMenu;
import dev.reachin.ShulkerSlot;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements ReachInMenu {

    @Shadow @Final public NonNullList<Slot> slots;

    @Shadow protected abstract Slot addSlot(Slot slot);

    @Shadow protected abstract boolean moveItemStackTo(ItemStack stack, int from, int to, boolean reverse);

    @Unique private MenuBinding reachin$state;

    @Override
    public MenuBinding reachin$binding() {
        if (this.reachin$state == null) {
            this.reachin$state = new MenuBinding();
        }
        return this.reachin$state;
    }

    @Override
    public void reachin$install() {
        MenuBinding binding = reachin$binding();
        if (binding.installed()) {
            return;
        }
        binding.base = this.slots.size();
        for (int i = 0; i < ReachIn.SLOTS; i++) {
            this.addSlot(new ShulkerSlot(binding.view, binding, i));
        }
    }

    @Override
    public boolean reachin$bind(int hostIndex) {
        reachin$install();
        MenuBinding binding = reachin$binding();
        if (hostIndex < 0 || hostIndex >= binding.base) {
            return false;
        }
        Slot host = this.slots.get(hostIndex);
        if (!ReachIn.isShulker(host.getItem())) {
            return false;
        }
        binding.hostIndex = hostIndex;
        binding.view.bind(host);
        return true;
    }

    @Override
    public void reachin$unbind() {
        MenuBinding binding = reachin$binding();
        binding.hostIndex = -1;
        binding.view.unbind();
    }

    /**
     * Shift-click routing. Everything else - plain clicks, right-click splits,
     * drag distribution, double-click collect, hotbar swaps - is left to vanilla,
     * which handles the grid correctly because its cells are real slots.
     */
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void reachin$clicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        MenuBinding binding = reachin$binding();
        if (ReachIn.DEBUG) {
            ReachIn.debug("clicked side=" + (player.level().isClientSide ? "C" : "S")
                    + " slotId=" + slotId + " type=" + clickType
                    + " base=" + binding.base + " host=" + binding.hostIndex
                    + " valid=" + binding.view.valid() + " guard=" + binding.autoGuard);
        }
        if (clickType != ClickType.QUICK_MOVE || !binding.installed() || !binding.view.valid()) {
            return;
        }
        if (slotId < 0 || slotId >= this.slots.size()) {
            return;
        }
        Slot source = this.slots.get(slotId);
        if (!source.hasItem()) {
            return;
        }

        if (slotId >= binding.base) {
            reachin$quickOut(source, binding, player);
            ci.cancel();
            return;
        }
        if (slotId != binding.hostIndex && source.container instanceof Inventory) {
            reachin$quickIn(source, binding, player);
            ci.cancel();
            return;
        }

        // Vanilla's own quick move is about to run over a range bounded by
        // slots.size(), which now includes the grid. Keep it out.
        binding.autoGuard = true;
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void reachin$clickedEnd(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        reachin$binding().autoGuard = false;
    }

    /**
     * The player's main inventory and hotbar, as a menu index range.
     *
     * Not simply everything before the grid: in the player's own menu that range also
     * covers the crafting grid, the armour slots and - as the very last slot, which a
     * reversed move reaches first - the offhand. Taking an item out of a shulker would
     * put it in your offhand.
     *
     * Container slot indices below 36 are exactly the main inventory and hotbar; 36-39
     * are armour and 40 is the offhand.
     */
    @Unique
    private int[] reachin$playerRange(MenuBinding binding) {
        int start = -1;
        int end = -1;
        for (int i = 0; i < binding.base; i++) {
            Slot slot = this.slots.get(i);
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36) {
                if (start < 0) {
                    start = i;
                }
                end = i + 1;
            }
        }
        return start < 0 ? new int[]{0, binding.base} : new int[]{start, end};
    }

    /** Grid cell to the player, hotbar end first, the way vanilla takes items out. */
    @Unique
    private void reachin$quickOut(Slot source, MenuBinding binding, Player player) {
        ItemStack stack = source.getItem();
        ItemStack before = stack.copy();
        int[] range = reachin$playerRange(binding);
        binding.autoGuard = true;
        boolean moved = this.moveItemStackTo(stack, range[0], range[1], true);
        binding.autoGuard = false;
        if (!moved) {
            return;
        }
        if (stack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, before);
    }

    /** Player inventory into the grid. */
    @Unique
    private void reachin$quickIn(Slot source, MenuBinding binding, Player player) {
        ItemStack stack = source.getItem();
        if (ReachIn.isShulker(stack)) {
            return;
        }
        ItemStack before = stack.copy();
        boolean moved = this.moveItemStackTo(stack, binding.base, binding.base + ReachIn.SLOTS, false);
        if (!moved) {
            return;
        }
        if (stack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, before);
    }
}
