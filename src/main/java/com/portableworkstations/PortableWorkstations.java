package com.portableworkstations;

import com.portableworkstations.config.Config;
import com.portableworkstations.handler.ContainerCloseHandler;
import com.portableworkstations.handler.ServerPayloadHandler;
import com.portableworkstations.workstation.AnvilTracker;
import com.portableworkstations.workstation.PortableFurnaceManager;
import com.portableworkstations.workstation.WorkstationManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PortableWorkstations.MODID)
public class PortableWorkstations {
    public static final String MODID = "portableworkstations";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PortableWorkstations(IEventBus modEventBus, ModContainer modContainer) {
        // Remove old config if it still uses pre-rename section names
        var configPath = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("portableworkstations-common.toml");
        if (java.nio.file.Files.exists(configPath)) {
            try {
                String content = java.nio.file.Files.readString(configPath);
                if (content.contains("[furnace_auto_detect]") || content.contains("entries = [")) {
                    java.nio.file.Files.delete(configPath);
                    LOGGER.info("Deleted outdated config; will regenerate with latest defaults.");
                }
            } catch (java.io.IOException e) {
                LOGGER.warn("Could not check config version: {}", e.getMessage());
            }
        }

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener(ServerPayloadHandler::registerPackets);
        modEventBus.addListener(WorkstationManager::onConfigReload);

        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class,
            event -> {
                if (event.getConfig().getSpec() == Config.SPEC)
                    PortableFurnaceManager.loadSpeedsFromConfig();
            });

        NeoForge.EVENT_BUS.register(ContainerCloseHandler.class);
        NeoForge.EVENT_BUS.register(WorkstationManager.class);
        NeoForge.EVENT_BUS.register(PortableFurnaceManager.class);
        NeoForge.EVENT_BUS.register(AnvilTracker.class);

        LOGGER.info("Portable Workstations initialized.");
    }
}
