package dev.reachin;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A Container view over the CONTAINER component of whatever shulker box sits in
 * the bound host slot.
 *
 * Contents are cached rather than decoded per call, because the whole slot system
 * mutates the stacks returned by getItem in place. setChanged writes the cache
 * back to the host stack.
 *
 * The instance last written is remembered. If the component is later replaced by
 * anything else - a server sync, a hopper, another player - the identity check in
 * sync() fails and the cache reloads.
 */
public class ShulkerView implements Container {
    private Slot host;
    private ItemContainerContents loadedFrom;
    private NonNullList<ItemStack> items = NonNullList.withSize(ReachIn.SLOTS, ItemStack.EMPTY);

    public void bind(Slot host) {
        this.host = host;
        this.loadedFrom = null;
    }

    public void unbind() {
        this.host = null;
        this.loadedFrom = null;
        this.items = NonNullList.withSize(ReachIn.SLOTS, ItemStack.EMPTY);
    }

    public Slot host() {
        return this.host;
    }

    /** True while the host slot still holds a shulker box. */
    public boolean valid() {
        return this.host != null && ReachIn.isShulker(this.host.getItem());
    }

    private NonNullList<ItemStack> sync() {
        if (!valid()) {
            return this.items;
        }
        ItemContainerContents current = this.host.getItem()
                .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (current != this.loadedFrom) {
            NonNullList<ItemStack> fresh = NonNullList.withSize(ReachIn.SLOTS, ItemStack.EMPTY);
            current.copyInto(fresh);
            this.items = fresh;
            this.loadedFrom = current;
        }
        return this.items;
    }

    @Override
    public int getContainerSize() {
        return ReachIn.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : sync()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= ReachIn.SLOTS) {
            return ItemStack.EMPTY;
        }
        return sync().get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack removed = ContainerHelper.removeItem(sync(), index, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack removed = ContainerHelper.takeItem(sync(), index);
        setChanged();
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= ReachIn.SLOTS) {
            return;
        }
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        sync().set(index, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        if (!valid()) {
            return;
        }
        ItemContainerContents written = ItemContainerContents.fromItems(this.items);
        this.loadedFrom = written;
        this.host.getItem().set(DataComponents.CONTAINER, written);
        this.host.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return valid();
    }

    @Override
    public void clearContent() {
        sync();
        for (int i = 0; i < ReachIn.SLOTS; i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** Vanilla refuses shulker boxes inside shulker boxes. So does Reach-In. */
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return !ReachIn.isShulker(stack);
    }
}
