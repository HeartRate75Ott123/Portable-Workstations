package com.portableworkstations.handler;

import com.portableworkstations.PortableWorkstations;
import com.portableworkstations.config.Config;
import com.portableworkstations.mixin.AbstractContainerScreenAccessor;
import com.portableworkstations.network.OpenWorkstationPayload;
import com.portableworkstations.workstation.WorkstationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side handler that intercepts right-clicks inside any
 * {@link AbstractContainerScreen} where the hovered slot belongs to the
 * player's own inventory.
 * <p>
 * This covers both the survival {@link InventoryScreen} AND all workstation
 * screens (crafting table, anvil, …), allowing the player to switch between
 * different workstation GUIs seamlessly.
 */
@EventBusSubscriber(modid = PortableWorkstations.MODID, value = Dist.CLIENT)
public class InventoryClickHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        // 1) Must be a container screen (inventory, crafting, anvil, …)
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        // 2) Only right-click (button 1)
        if (event.getButton() != 1) return;

        // 3) Respect the master toggle
        if (!Config.ENABLED.getAsBoolean()) return;

        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        // 4) Only activate when the mouse is NOT carrying a stack
        if (!minecraft.player.containerMenu.getCarried().isEmpty()) return;

        // 5) Find which slot the mouse is over using mixin-invoked findSlot
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        Slot slot = accessor.invokeFindSlot(event.getMouseX(), event.getMouseY());
        if (slot == null || !slot.hasItem()) return;

        // 6) Only act on player-inventory slots (not the workstation's own slots)
        if (slot.container != minecraft.player.getInventory()) return;

        // 7) Check whether the item in this slot is a known workstation
        var itemId = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem());
        if (!WorkstationManager.isWorkstationItem(itemId)) return;

        // ── All conditions met ──────────────────────────────────────────────

        // Cancel the vanilla right-click behavior (stack splitting)
        event.setCanceled(true);

        // Tell the server to open the workstation menu
        // Send the exact inventory slot so the server doesn't have to guess which stack
        PacketDistributor.sendToServer(new OpenWorkstationPayload(itemId.toString(), slot.getSlotIndex()));

    }
}
