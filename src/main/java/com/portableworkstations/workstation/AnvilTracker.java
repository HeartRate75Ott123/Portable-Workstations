package com.portableworkstations.workstation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anvil damage tracking — deterministic, no randomness per use.
 * <p>
 * Each damage stage pre-allocates {@code 5 + random(8)} uses (avg 8.5).
 * A fresh anvil goes through 3 stages → ~25 uses total.
 * Chipped anvil → 2 stages → ~17 uses.
 * Damaged anvil → 1 stage → ~8 uses.
 */
public class AnvilTracker {

    private static final int MAX_STAGES = 3;

    /** Per-player: inventory slot → [usesLeft, stage]. */
    private static final Map<ServerPlayer, Map<Integer, int[]>> TRACKER = new WeakHashMap<>();

    // ── Public API ──────────────────────────────────────────────────────────

    public static void onAnvilUsed(ServerPlayer player) {
        int slot = resolveSlot(player);
        if (slot < 0) { player.closeContainer(); return; }

        int[] data = getOrCreateTracker(player, slot);
        // 1-based: —data[0], check for 0
        data[0]--;

        if (data[0] > 0) return;          // still has uses left in this stage

        // Stage advanced
        data[1]++;                         // damageStage ++

        if (data[1] >= MAX_STAGES) {
            breakInSlot(player, slot);
            perPlayer(player).remove(slot);
        } else {
            upgradeInSlot(player, slot);
            data[0] = nextUses();           // allocate uses for the new stage
        }
    }

    /** Returns current damage stage (0–2) for the tracked slot. */
    public static int getDamageLevel(ServerPlayer player, ResourceLocation ignored) {
        int slot = resolveSlot(player);
        if (slot < 0) return 0;
        int[] data = perPlayer(player).get(slot);
        return data != null ? data[1] : 0;
    }

    public static void resetDamage(ServerPlayer player, ResourceLocation ignored) {
        int slot = resolveSlot(player);
        if (slot >= 0) perPlayer(player).remove(slot);
    }

    // ── Deterministic counter allocation ────────────────────────────────────

    private static final Random RNG = new Random();

    /** Generates 5–12 inclusive (avg ~8.5). */
    private static int nextUses() {
        return 5 + RNG.nextInt(8);
    }

    /** Returns the initial stage based on item variant. */
    private static int initialStage(String path) {
        if (path.equals("damaged_anvil")) return 2;
        if (path.equals("chipped_anvil")) return 1;
        return 0;
    }

    /** Gets or initialises the [usesLeft, stage] array for this slot. */
    private static int[] getOrCreateTracker(ServerPlayer player, int slot) {
        Map<Integer, int[]> map = perPlayer(player);
        int[] data = map.get(slot);
        if (data == null) {
            ItemStack stack = player.getInventory().items.get(slot);
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int stage = initialStage(path);
            int usesLeft = nextUses();
            data = new int[]{usesLeft, stage};
            map.put(slot, data);
        }
        return data;
    }

    private static Map<Integer, int[]> perPlayer(ServerPlayer player) {
        return TRACKER.computeIfAbsent(player, k -> new HashMap<>());
    }

    // ── Slot resolution ─────────────────────────────────────────────────────

    private static int resolveSlot(ServerPlayer player) {
        int slot = WorkstationManager.getPlayerWorkstationSlot(player);
        ResourceLocation itemId = WorkstationManager.getPlayerWorkstationItem(player);
        java.util.UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (itemId == null) return -1;

        Inventory inv = player.getInventory();
        if (slot >= 0 && slot < inv.items.size()) {
            ItemStack s = inv.items.get(slot);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) return slot;
        }
        if (marker != null) {
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.items.get(i);
                if (s.isEmpty()) continue;
                var cd = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (cd != null && marker.equals(cd.copyTag().getUUID("pw_marker"))) {
                    return migrateData(player, slot, i);
                }
            }
        }
        int expectedCount = WorkstationManager.getPlayerWorkstationCount(player);
        int best = -1;
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) continue;
            if (s.getCount() == expectedCount) { best = i; break; }
            if (best < 0) best = i;
        }
        if (best >= 0) return migrateData(player, slot, best);
        return -1;
    }

    private static int migrateData(ServerPlayer player, int oldSlot, int newSlot) {
        Map<Integer, int[]> map = perPlayer(player);
        if (oldSlot >= 0 && map.containsKey(oldSlot)) {
            map.put(newSlot, map.remove(oldSlot));
        }
        WorkstationManager.updateWorkstationSlot(player, newSlot);
        return newSlot;
    }

    // ─── Slot operations ────────────────────────────────────────────────────

    private static void upgradeInSlot(ServerPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (slot >= inv.items.size()) return;
        ItemStack stack = inv.items.get(slot);
        if (stack.isEmpty()) return;

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ResourceLocation targetId;
        if (path.equals("anvil"))            targetId = ResourceLocation.withDefaultNamespace("chipped_anvil");
        else if (path.equals("chipped_anvil")) targetId = ResourceLocation.withDefaultNamespace("damaged_anvil");
        else return;

        var newStack = new ItemStack(BuiltInRegistries.ITEM.get(targetId), stack.getCount());
        var oldCd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (oldCd != null && oldCd.copyTag().hasUUID("pw_marker")) {
            newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(oldCd.copyTag()));
        }
        inv.items.set(slot, newStack);
        WorkstationManager.overrideWorkstationItem(player, targetId);
    }

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

    // ── Block placement ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getPlacedBlock().is(BlockTags.ANVIL)) return;

        int slot = resolveSlot(player);
        if (slot < 0) return;
        int[] data = perPlayer(player).get(slot);
        int stage = data != null ? data[1] : 0;
        if (stage <= 0) return;

        var newBlock = switch (stage) {
            case 1 -> Blocks.CHIPPED_ANVIL;
            case 2 -> Blocks.DAMAGED_ANVIL;
            default -> null;
        };
        if (newBlock != null) player.level().setBlock(event.getPos(), newBlock.defaultBlockState(), 3);
        // Keep tracker data — mining the block and right-clicking again will
        // migrate it via resolveSlot, preserving the damage stage.
    }

    public static void clear() { TRACKER.clear(); }
}
