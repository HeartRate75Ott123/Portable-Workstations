package com.portableworkstations;

import com.portableworkstations.handler.InventoryClickHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-side initialisation for Portable Workstations.
 * <p>
 * Registers the config screen and the inventory click handler.
 */
@Mod(value = PortableWorkstations.MODID, dist = Dist.CLIENT)
public class PortableWorkstationsClient {

    public PortableWorkstationsClient(ModContainer container) {
        // Provide a config screen accessible from the mod list
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Register the click handler on the game event bus (client-only)
        NeoForge.EVENT_BUS.register(InventoryClickHandler.class);

        PortableWorkstations.LOGGER.info("Portable Workstations client setup complete.");
    }
}
