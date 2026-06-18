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

    /**
     * If the config file is from an older version, rewrite it so all new
     * default entries (workstations, speed overrides, etc.) appear on disk.
     * User-modified values are preserved because the spec only writes
     * values that are missing or changed from defaults.
     */
    private static void autoMigrateConfig() {
        if (Config.CONFIG_VERSION.get() >= Config.CURRENT_CONFIG_VERSION) return;
        LOGGER.info("Config version {} -> {}, updating config file",
                Config.CONFIG_VERSION.get(), Config.CURRENT_CONFIG_VERSION);
        Config.CONFIG_VERSION.set(Config.CURRENT_CONFIG_VERSION);
    }

    public PortableWorkstations(IEventBus modEventBus, ModContainer modContainer) {
        // Sync config version from mod version (e.g. "1.1.0" → 110)
        var rawVer = modContainer.getModInfo().getVersion().toString();
        try { Config.CURRENT_CONFIG_VERSION = Integer.parseInt(rawVer.replace(".", "")); }
        catch (NumberFormatException e) { Config.CURRENT_CONFIG_VERSION = 1; }

        // Register common config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register network payloads on the mod bus
        modEventBus.addListener(ServerPayloadHandler::registerPackets);
        // Rebuild workstation cache when config reloads
        modEventBus.addListener(WorkstationManager::onConfigReload);
        // Load furnace speeds from config on first load + auto-migrate outdated config
        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class,
            event -> {
                if (event.getConfig().getSpec() == Config.SPEC) {
                    PortableFurnaceManager.loadSpeedsFromConfig();
                    autoMigrateConfig();
                }
            });

        // Register event handlers on the global game bus
        NeoForge.EVENT_BUS.register(ContainerCloseHandler.class);
        NeoForge.EVENT_BUS.register(WorkstationManager.class);
        NeoForge.EVENT_BUS.register(PortableFurnaceManager.class);
        NeoForge.EVENT_BUS.register(AnvilTracker.class);

        LOGGER.info("Portable Workstations initialized.");
    }
}
