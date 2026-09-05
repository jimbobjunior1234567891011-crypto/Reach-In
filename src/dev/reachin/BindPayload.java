package dev.reachin;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: point the grid at a shulker box. hostIndex below zero unbinds. */
public record BindPayload(int containerId, int hostIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BindPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ReachIn.MODID, "bind"));

    public static final StreamCodec<ByteBuf, BindPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BindPayload::containerId,
            ByteBufCodecs.VAR_INT, BindPayload::hostIndex,
            BindPayload::new);

    public static BindPayload unbind(int containerId) {
        return new BindPayload(containerId, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
