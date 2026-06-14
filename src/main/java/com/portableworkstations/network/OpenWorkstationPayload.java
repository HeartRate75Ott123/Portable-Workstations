package com.portableworkstations.network;

import com.portableworkstations.PortableWorkstations;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network payload sent from client → server when the player right-clicks
 * a workstation item in their inventory.
 * <p>
 * Uses NeoForge's {@link CustomPacketPayload} + {@link StreamCodec} system.
 *
 * @param blockId The registry name of the clicked item (e.g. "minecraft:crafting_table")
 */
public record OpenWorkstationPayload(String blockId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenWorkstationPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(PortableWorkstations.MODID, "open_workstation")
            );

    /**
     * Stream codec: encodes/decodes the block ID as a UTF-8 string.
     */
    public static final StreamCodec<ByteBuf, OpenWorkstationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenWorkstationPayload::blockId,
                    OpenWorkstationPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
