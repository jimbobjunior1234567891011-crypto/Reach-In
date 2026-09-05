package dev.reachin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {
    private Net() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                BindPayload.TYPE,
                BindPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handle(payload, context.player())));
    }

    private static void handle(BindPayload payload, Player player) {
        if (player == null) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != payload.containerId()) {
            return;
        }
        ReachInMenu reachIn = (ReachInMenu) menu;
        if (payload.hostIndex() < 0 || !reachIn.reachin$bind(payload.hostIndex())) {
            reachIn.reachin$unbind();
        }
    }
}
