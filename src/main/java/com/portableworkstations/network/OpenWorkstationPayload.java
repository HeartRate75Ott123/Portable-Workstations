package com.portableworkstations.network;

import com.portableworkstations.PortableWorkstations;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenWorkstationPayload(String blockId, int slotIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenWorkstationPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(PortableWorkstations.MODID, "open_workstation")
            );

    public static final StreamCodec<ByteBuf, OpenWorkstationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenWorkstationPayload::blockId,
                    ByteBufCodecs.VAR_INT, OpenWorkstationPayload::slotIndex,
                    OpenWorkstationPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
