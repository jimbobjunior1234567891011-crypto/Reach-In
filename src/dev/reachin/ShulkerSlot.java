package dev.reachin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One cell of the grid. A real menu slot, so vanilla routes every click type
 * through it; its x/y are moved to the panel each frame on the client.
 *
 * isActive gates rendering and hit-testing together, which is exactly what an
 * unbound grid needs.
 */
public class ShulkerSlot extends Slot {
    private final ShulkerView view;
    private final MenuBinding binding;

    public ShulkerSlot(ShulkerView view, MenuBinding binding, int index) {
        super(view, index, 0, 0);
        this.view = view;
        this.binding = binding;
    }

    @Override
    public boolean isActive() {
        return this.view.valid();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        boolean allowed = this.view.valid() && !this.binding.autoGuard && !ReachIn.isShulker(stack);
        if (ReachIn.DEBUG && !allowed) {
            ReachIn.debug("mayPlace refused: valid=" + this.view.valid()
                    + " guard=" + this.binding.autoGuard + " stack=" + stack);
        }
        return allowed;
    }

    @Override
    public boolean mayPickup(Player player) {
        return this.view.valid();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}
