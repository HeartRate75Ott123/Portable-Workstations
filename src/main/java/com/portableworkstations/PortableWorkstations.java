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
        // Delete old config BEFORE registering spec, so fresh defaults apply
        nukeOldConfig();

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(ServerPayloadHandler::registerPackets);
        modEventBus.addListener(WorkstationManager::onConfigReload);

        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, event -> {
            if (event.getConfig().getSpec() != Config.SPEC) return;
            PortableFurnaceManager.loadSpeedsFromConfig();
        });

        NeoForge.EVENT_BUS.register(ContainerCloseHandler.class);
        NeoForge.EVENT_BUS.register(WorkstationManager.class);
        NeoForge.EVENT_BUS.register(PortableFurnaceManager.class);
        NeoForge.EVENT_BUS.register(AnvilTracker.class);

        LOGGER.info("Portable Workstations initialized.");
    }

    private static void nukeOldConfig() {
        try {
            Path p = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("portableworkstations-common.toml");
            if (!Files.exists(p)) return;
            String c = Files.readString(p);
            if (c.contains("furnace_auto_detect") || c.contains("entries = [")) {
                Files.delete(p);
                LOGGER.info("Deleted outdated config; fresh defaults generated.");
            }
        } catch (Exception e) {
            LOGGER.warn("Migration check: {}", e.getMessage());
        }
    }
}
