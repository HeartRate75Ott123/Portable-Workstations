package com.portableworkstations.workstation;

import com.portableworkstations.PortableWorkstations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Tracks anvil "block damage" for the portable anvil workstation.
 * <p>
 * In vanilla, an anvil block in the world survives ~25 uses on average
 * (12 % chance of damage per use, 3 damage stages before break).
 * This class replicates that behaviour on the player's anvil item:
 * <ul>
 *   <li>Each anvil use has a 12 % chance (matching {@code AnvilMenu.onTake})</li>
 *   <li>When the counter reaches 3, ONE item is consumed (simulating break)</li>
 *   <li>The counter resets after each break</li>
 * </ul>
 * <p>
 * Tracking is per-player per-item-type. {@code inv.items} covers both main
 * inventory (slots 9–35) and hotbar (slots 0–8), so a single pass suffices.
 * Only ONE item is removed per break — never the entire stack.
 */
public class AnvilTracker {

    /** Max uses before the anvil "breaks" (normal → chipped → damaged → destroyed). */
    private static final int MAX_USES = 3;

    /** 12 % damage chance per use, matching the hard-coded value in {@code AnvilMenu.onTake}. */
    private static final float DAMAGE_CHANCE = 0.12f;

    /** Per-player: item registry name → accumulated damage count. */
    private static final Map<ServerPlayer, Map<ResourceLocation, Integer>> DAMAGE_MAP = new WeakHashMap<>();

    /**
     * Called every time the player takes a result from the portable anvil.
     * Rolls the 12 % damage check and, if the anvil "breaks", removes
     * <b>one</b> matching item from the player's inventory.
     *
     * @param player the server player
     */
    public static void onAnvilUsed(ServerPlayer player) {
        if (player.getRandom().nextFloat() >= DAMAGE_CHANCE) return;

        ResourceLocation itemId = WorkstationManager.getPlayerWorkstationItem(player);
        if (itemId == null) return;

        int damage = DAMAGE_MAP
                .computeIfAbsent(player, k -> new HashMap<>())
                .merge(itemId, 1, Integer::sum);

        PortableWorkstations.LOGGER.debug(
                "Anvil damage accrued for {} / {} ({}/{})",
                player.getName().getString(), itemId, damage, MAX_USES
        );

        if (damage >= MAX_USES) {
            removeOneFromInventory(player, itemId);
            DAMAGE_MAP.get(player).remove(itemId); // counter resets
        }
    }

    /**
     * Removes exactly one item matching {@code itemId} from the player's
     * inventory. {@code inv.items} (size 36) already includes both the hotbar
     * (indices 0–8) and the main inventory (9–35), so a single pass is correct.
     * The returned item (if any) is in a separate slot and never takes the
     * whole stack.
     */
    private static void removeOneFromInventory(ServerPlayer player, ResourceLocation itemId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                stack.shrink(1);                          // remove exactly one
                if (stack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.ANVIL_BREAK,
                        SoundSource.BLOCKS, 1.0f, 1.0f);
                PortableWorkstations.LOGGER.debug(
                        "Anvil broke, removed one {} from {}",
                        itemId, player.getName().getString()
                );
                return;
            }
        }
    }

    /** Clears all tracking (server shutdown). */
    public static void clear() {
        DAMAGE_MAP.clear();
    }
}
