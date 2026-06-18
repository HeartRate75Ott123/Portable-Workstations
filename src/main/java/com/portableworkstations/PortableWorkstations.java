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
import java.util.ArrayList;
import java.util.List;

@Mod(PortableWorkstations.MODID)
public class PortableWorkstations {
    public static final String MODID = "portableworkstations";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Saved user customizations from old config, applied after fresh generation. */
    private static List<String> savedDefinitions = null;

    public PortableWorkstations(IEventBus modEventBus, ModContainer modContainer) {
        migrateConfig();

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(ServerPayloadHandler::registerPackets);
        modEventBus.addListener(WorkstationManager::onConfigReload);

        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, event -> {
            if (event.getConfig().getSpec() != Config.SPEC) return;
            PortableFurnaceManager.loadSpeedsFromConfig();
            // Re-apply user's custom definitions after config load
            if (savedDefinitions != null) {
                Config.WORKSTATION_DEFINITIONS.set(savedDefinitions);
                savedDefinitions = null;
                LOGGER.info("Restored custom workstation definitions from old config.");
            }
        });

        NeoForge.EVENT_BUS.register(ContainerCloseHandler.class);
        NeoForge.EVENT_BUS.register(WorkstationManager.class);
        NeoForge.EVENT_BUS.register(PortableFurnaceManager.class);
        NeoForge.EVENT_BUS.register(AnvilTracker.class);

        LOGGER.info("Portable Workstations initialized.");
    }

    /** Migrate old-format config → new format, preserving user's custom definitions. */
    private static void migrateConfig() {
        try {
            Path p = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("portableworkstations-common.toml");
            if (!Files.exists(p)) return;
            String content = Files.readString(p);
            if (!content.contains("furnace_auto_detect") && !content.contains("entries = ["))
                return; // already up-to-date

            // Extract user's custom definitions from old config
            List<String> userDefs = new ArrayList<>();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.startsWith("\"") && line.contains("=")) {
                    String entry = line.replaceAll("[\",]", "").trim();
                    if (!entry.isEmpty()) userDefs.add(entry);
                }
            }
            if (!userDefs.isEmpty()
                    && !userDefs.equals(Config.defaultWorkstations())) {
                savedDefinitions = userDefs;
                LOGGER.info("Preserved {} custom workstation definition(s).", userDefs.size());
            }

            Files.delete(p);
            LOGGER.info("Migrated config to latest format.");
        } catch (Exception e) {
            LOGGER.warn("Config migration failed: {}", e.getMessage());
        }
    }
}
