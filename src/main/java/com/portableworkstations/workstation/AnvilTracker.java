package com.portableworkstations.workstation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

/**
 * Anvil damage tracking that operates on the exact inventory slot
 * the player right-clicked. Other stacks in the inventory are NOT
 * affected, and per-stack damage counters are independent.
 */
public class AnvilTracker {

    private static final int MAX_USES = 3;
    private static final float DAMAGE_CHANCE = 0.12f;

    /** Per-player: inventory slot index → damage count. */
    private static final Map<ServerPlayer, Map<Integer, Integer>> DAMAGE_MAP = new WeakHashMap<>();

    // ── Public API ──────────────────────────────────────────────────────────

    public static void onAnvilUsed(ServerPlayer player) {
        if (player.getRandom().nextFloat() >= DAMAGE_CHANCE) return;

        // Resolve the actual slot — handles mid-gui inventory moves
        int slot = resolveSlot(player);
        if (slot < 0) return;

        Map<Integer, Integer> slotMap = perSlot(player);

        // Initialise counter based on the item variant already in the slot
        if (!slotMap.containsKey(slot)) {
            ItemStack stack = player.getInventory().items.get(slot);
            String p = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int base = p.equals("damaged_anvil") ? 2 : p.equals("chipped_anvil") ? 1 : 0;
            if (base > 0) slotMap.put(slot, base);
        }

        int damage = slotMap.merge(slot, 1, Integer::sum);

        if (damage >= MAX_USES) {
            breakInSlot(player, slot);
            slotMap.remove(slot);
        } else {
            upgradeInSlot(player, slot);
        }
    }

    /** Returns damage level (0–2) for the given player's tracked slot. */
    public static int getDamageLevel(ServerPlayer player, ResourceLocation ignored) {
        int slot = resolveSlot(player);
        return slot >= 0 ? perSlot(player).getOrDefault(slot, 0) : 0;
    }

    public static void resetDamage(ServerPlayer player, ResourceLocation ignored) {
        int slot = resolveSlot(player);
        if (slot >= 0) perSlot(player).remove(slot);
    }

    // ─── Slot resolution (handles mid-gui inventory moves) ───────────────────

    /**
     * Returns the actual slot the player's workstation item is in.
     * If the tracked slot is stale (item moved), searches the inventory and
     * migrates the damage counter to the new slot so tracking isn't lost.
     */
    private static int resolveSlot(ServerPlayer player) {
        int slot = WorkstationManager.getPlayerWorkstationSlot(player);
        ResourceLocation itemId = WorkstationManager.getPlayerWorkstationItem(player);
        java.util.UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (itemId == null) return -1;

        Inventory inv = player.getInventory();
        // 1) Quick path: tracked slot still has the right item
        if (slot >= 0 && slot < inv.items.size()) {
            ItemStack s = inv.items.get(slot);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) {
                return slot;
            }
        }
        // 2) Search by marker UUID (bulletproof for identical stacks)
        if (marker != null) {
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.items.get(i);
                if (s.isEmpty()) continue;
                var cd = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (cd != null && marker.equals(cd.copyTag().getUUID("pw_marker"))) {
                    return migrateCounter(player, slot, i);
                }
            }
        }
        // 3) Fallback: prefer same-count stack → first match
        int expectedCount = WorkstationManager.getPlayerWorkstationCount(player);
        int bestSlot = -1;
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) continue;
            if (s.getCount() == expectedCount) { bestSlot = i; break; }
            if (bestSlot < 0) bestSlot = i;
        }
        if (bestSlot >= 0) return migrateCounter(player, slot, bestSlot);
        return -1;
    }

    /** Moves the damage counter from oldSlot to newSlot and updates WS tracking. */
    private static int migrateCounter(ServerPlayer player, int oldSlot, int newSlot) {
        Map<Integer, Integer> slotMap = perSlot(player);
        if (oldSlot >= 0 && slotMap.containsKey(oldSlot)) {
            slotMap.put(newSlot, slotMap.remove(oldSlot));
        }
        WorkstationManager.updateWorkstationSlot(player, newSlot);
        return newSlot;
    }

    // ─── Slot-precise operations ─────────────────────────────────────────────

    /**
     * Upgrades the item in the tracked slot to the next visual tier
     * (anvil → chipped_anvil → damaged_anvil). No-op if already at max.
     */
    private static void upgradeInSlot(ServerPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (slot >= inv.items.size()) return;
        ItemStack stack = inv.items.get(slot);
        if (stack.isEmpty()) return;

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ResourceLocation targetId;
        if (path.equals("anvil")) {
            targetId = ResourceLocation.withDefaultNamespace("chipped_anvil");
        } else if (path.equals("chipped_anvil")) {
            targetId = ResourceLocation.withDefaultNamespace("damaged_anvil");
        } else {
            return; // already damaged_anvil or unknown
        }
        ItemStack newStack = new ItemStack(BuiltInRegistries.ITEM.get(targetId), stack.getCount());
        // Preserve the UUID marker on the new stack
        var oldCd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (oldCd != null && oldCd.copyTag().hasUUID("pw_marker")) {
            newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(oldCd.copyTag()));
        }
        inv.items.set(slot, newStack);
        WorkstationManager.overrideWorkstationItem(player, targetId);
    }

    /** Removes one item from the specific slot, plays the break sound and cleans up the marker. */
    private static void breakInSlot(ServerPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (slot >= inv.items.size()) return;
        ItemStack stack = inv.items.get(slot);
        if (stack.isEmpty()) return;

        stack.shrink(1);
        if (stack.isEmpty()) inv.items.set(slot, ItemStack.EMPTY);
        WorkstationManager.clearMarkerFromSlot(player);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static Map<Integer, Integer> perSlot(ServerPlayer player) {
        return DAMAGE_MAP.computeIfAbsent(player, k -> new HashMap<>());
    }

    // ── Block placement ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getPlacedBlock().is(BlockTags.ANVIL)) return;

        int slot = resolveSlot(player);
        if (slot < 0) return;
        int damage = perSlot(player).getOrDefault(slot, 0);
        if (damage <= 0) return;

        var newBlock = switch (damage) {
            case 1 -> Blocks.CHIPPED_ANVIL;
            case 2 -> Blocks.DAMAGED_ANVIL;
            default -> null;
        };
        if (newBlock != null) {
            player.level().setBlock(event.getPos(), newBlock.defaultBlockState(), 3);
        }
        perSlot(player).remove(slot);
    }

    public static void clear() { DAMAGE_MAP.clear(); }
}
