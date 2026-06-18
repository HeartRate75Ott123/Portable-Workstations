package com.portableworkstations.handler;

import com.portableworkstations.PortableWorkstations;
import com.portableworkstations.workstation.WorkstationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Handles the {@link PlayerContainerEvent.Close} event to salvage any items
 * left inside a portable workstation's work area when the GUI is closed.
 * <p>
 * <b>Furnace-type menus are special:</b> their slots belong to
 * {@link com.example.examplemod.workstation.PortableFurnaceManager} and
 * must NOT be cleared — background smelting continues ticking even after
 * the GUI is closed. The player can reopen the furnace later to retrieve
 * finished items.
 * <p>
 * For all other workstations (crafting table, anvil, etc.) the vanilla
 * {@code removed()} override already calls {@code clearContainer}, so our
 * handler is a safety net / improvement (prefers inventory over ground drop).
 */
@EventBusSubscriber(modid = PortableWorkstations.MODID)
public class ContainerCloseHandler {

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        AbstractContainerMenu menu = event.getContainer();

        // Only handle menus that WE opened
        if (!WorkstationManager.isPortableMenu(menu)) return;
        WorkstationManager.removePortableMenu(menu);

        if (player instanceof ServerPlayer serverPlayer) {
            String mt = WorkstationManager.getPlayerMenuType(serverPlayer);

            // ── Anvil: skip EVERYTHING — don't merge, don't move slots,
            //     don't iterate. The portable anvil stays in its tracked slot.
            if ("anvil".equals(mt)) return;

            // ── Other workstations: clean up tracking normally.
            WorkstationManager.cleanupPlayer(serverPlayer);
        }

        // ── Furnace special case ─────────────────────────────────────────
        if (menu instanceof AbstractFurnaceMenu) return;

        // ── All other workstations ───────────────────────────────────────
        // Move leftover items back to the player's inventory.
        // Result slots (mayPlace == false) are SKIPPED — they hold crafted output
        // that the player must manually collect.
        var playerInventory = player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) continue;

            // Skip result-only slots (cannot accept items → output slots)
            if (!slot.mayPlace(ItemStack.EMPTY)) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            slot.set(ItemStack.EMPTY);
            if (!playerInventory.add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
