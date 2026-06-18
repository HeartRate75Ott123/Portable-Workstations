package com.portableworkstations.handler;

import com.portableworkstations.network.OpenWorkstationPayload;
import com.portableworkstations.workstation.WorkstationManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the server-bound payload and processes it on the network thread.
 */
public class ServerPayloadHandler {

    /**
     * Called during {@link RegisterPayloadHandlersEvent} on the mod event bus.
     */
    public static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1"); // protocol version
        registrar.playToServer(
                OpenWorkstationPayload.TYPE,
                OpenWorkstationPayload.STREAM_CODEC,
                ServerPayloadHandler::handle
        );
    }

    /**
     * Handles an incoming {@link OpenWorkstationPayload}.
     * <p>
     * Validates conditions (mod enabled, item is a known workstation) and
     * opens the appropriate menu for the player.
     */
    private static void handle(OpenWorkstationPayload payload, IPayloadContext context) {
        // Ensure we run on the main game thread
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            WorkstationManager.openWorkstation(serverPlayer, payload.blockId(), payload.slotIndex());
        });
    }
}
