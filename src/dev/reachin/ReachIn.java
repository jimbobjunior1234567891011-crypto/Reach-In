package dev.reachin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(ReachIn.MODID)
public class ReachIn {
    public static final String MODID = "reachin";

    /** Slots in a shulker box. */
    public static final int SLOTS = 27;

    public ReachIn(IEventBus modBus) {
        modBus.addListener(Net::register);
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof ShulkerBoxBlock;
    }
}
