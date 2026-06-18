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
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(PortableWorkstations.MODID)
public class PortableWorkstations {
    public static final String MODID = "portableworkstations";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PortableWorkstations(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener(ServerPayloadHandler::registerPackets);
        modEventBus.addListener(WorkstationManager::onConfigReload);

        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, event -> {
            if (event.getConfig().getSpec() != Config.SPEC) return;
            PortableFurnaceManager.loadSpeedsFromConfig();
            migrateConfigIfNeeded();
        });

        NeoForge.EVENT_BUS.register(ContainerCloseHandler.class);
        NeoForge.EVENT_BUS.register(WorkstationManager.class);
        NeoForge.EVENT_BUS.register(PortableFurnaceManager.class);
        NeoForge.EVENT_BUS.register(AnvilTracker.class);

        LOGGER.info("Portable Workstations initialized.");
    }

    /** One-time migration: deletes old-format config so NeoForge writes the latest layout. */
    private static boolean migrationDone = false;
    private synchronized static void migrateConfigIfNeeded() {
        if (migrationDone) return;
        migrationDone = true;
        Path configPath = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("portableworkstations-common.toml");
        if (!Files.exists(configPath)) return;
        try {
            String content = Files.readString(configPath);
            if (!content.contains("furnace_detection") || !content.contains("overrides")) {
                Files.delete(configPath);
                LOGGER.info("Deleted outdated config; will regenerate with latest defaults.");
            }
        } catch (Exception e) {
            LOGGER.warn("Config migration check failed: {}", e.getMessage());
        }
    }
}
