package com.portableworkstations;

import com.portableworkstations.config.Config;
import com.portableworkstations.handler.ContainerCloseHandler;
import com.portableworkstations.handler.ServerPayloadHandler;
import com.portableworkstations.workstation.PortableFurnaceManager;
import com.portableworkstations.workstation.WorkstationManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Main mod class for Portable Workstations.
 * <p>
 * Allows players to right-click workstation items (crafting table, anvil, etc.)
 * in their inventory to open the corresponding GUI directly.
 */
@Mod(PortableWorkstations.MODID)
public class PortableWorkstations {
    public static final String MODID = "portableworkstations";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PortableWorkstations(IEventBus modEventBus, ModContainer modContainer) {
        // Register common config (COMMON type so both client and server use it)
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register network payloads on the mod bus
        modEventBus.addListener(ServerPayloadHandler::registerPackets);

        // Register event handlers on the global game bus
        NeoForge.EVENT_BUS.register(ContainerCloseHandler.class);
        NeoForge.EVENT_BUS.register(WorkstationManager.class);
        NeoForge.EVENT_BUS.register(PortableFurnaceManager.class);

        LOGGER.info("Portable Workstations initialized.");
    }
}
