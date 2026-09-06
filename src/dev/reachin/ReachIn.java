package dev.reachin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ReachIn.MODID)
public class ReachIn {
    public static final String MODID = "reachin";

    /** Slots in a shulker box. */
    public static final int SLOTS = 27;

    /** -Dreachin.debug=true to trace slot clicks and placement refusals. */
    public static final boolean DEBUG = Boolean.getBoolean("reachin.debug");

    public static void debug(String message) {
        System.out.println("[reachin] " + message);
    }

    public ReachIn(IEventBus modBus) {
        modBus.addListener(Net::register);
        // The method reference only loads the client class when this branch is taken,
        // so a dedicated server never touches it.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(dev.reachin.client.ClientEvents::onBackground);
            NeoForge.EVENT_BUS.addListener(dev.reachin.client.ClientEvents::onForeground);
        }
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof ShulkerBoxBlock;
    }
}
