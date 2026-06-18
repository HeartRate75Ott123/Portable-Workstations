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

/**
 * Anvil damage — vanilla 12 % random chance per use.
 * 3 damage stages = ~25 uses average for a fresh anvil.
 */
public class AnvilTracker {

    private static final int MAX_STAGES = 3;
    private static final float DAMAGE_CHANCE = 0.12f;
    private static final Random RNG = new Random();

    /** Per-player: slot index → damage stage (0, 1, 2). */
    private static final Map<ServerPlayer, Map<Integer, Integer>> TRACKER = new WeakHashMap<>();

    // ── Public API ──────────────────────────────────────────────────────────

    public static void onAnvilUsed(ServerPlayer player) {
        if (RNG.nextFloat() >= DAMAGE_CHANCE) return;

        int slot = resolveSlot(player);
        if (slot < 0) { player.closeContainer(); return; }

        int stage = perPlayer(player).merge(slot, 1, Integer::sum);

        if (stage >= MAX_STAGES) {
            breakInSlot(player, slot);
            perPlayer(player).remove(slot);
        } else {
            upgradeInSlot(player, slot);
        }
    }

    public static int getDamageLevel(ServerPlayer player, ResourceLocation ignored) {
        int slot = resolveSlot(player);
        if (slot < 0) return 0;
        Integer s = perPlayer(player).get(slot);
        return s != null ? s : 0;
    }

    public static void resetDamage(ServerPlayer player, ResourceLocation ignored) {
        int slot = resolveSlot(player);
        if (slot >= 0) perPlayer(player).remove(slot);
    }

    // ── Slot resolution ─────────────────────────────────────────────────────

    private static int resolveSlot(ServerPlayer player) {
        int slot = WorkstationManager.getPlayerWorkstationSlot(player);
        ResourceLocation itemId = WorkstationManager.getPlayerWorkstationItem(player);
        java.util.UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (itemId == null) return -1;

        Inventory inv = player.getInventory();
        // Quick path: tracked slot still has the right item
        if (slot >= 0 && slot < inv.items.size()) {
            ItemStack s = inv.items.get(slot);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) return slot;
        }
        // Marker path: UUID on the item
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
        // Fallback: count-match then first match
        int expected = WorkstationManager.getPlayerWorkstationCount(player);
        int best = -1;
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) continue;
            if (s.getCount() == expected) { best = i; break; }
            if (best < 0) best = i;
        }
        if (best >= 0) return migrateData(player, slot, best);
        return -1;
    }

    private static int migrateData(ServerPlayer player, int oldSlot, int newSlot) {
        Map<Integer, Integer> map = perPlayer(player);
        if (oldSlot >= 0 && map.containsKey(oldSlot)) map.put(newSlot, map.remove(oldSlot));
        WorkstationManager.updateWorkstationSlot(player, newSlot);
        return newSlot;
    }

    // ── Slot operations ─────────────────────────────────────────────────────

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
        var cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (cd != null && cd.copyTag().hasUUID("pw_marker"))
            newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(cd.copyTag()));
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
        Integer stage = perPlayer(player).get(slot);
        if (stage == null || stage <= 0) return;

        var newBlock = switch (stage) {
            case 1 -> Blocks.CHIPPED_ANVIL;
            case 2 -> Blocks.DAMAGED_ANVIL;
            default -> null;
        };
        if (newBlock != null) player.level().setBlock(event.getPos(), newBlock.defaultBlockState(), 3);
        // Tracker kept — mining + re-opening inherits stage from item variant
    }

    public static void clear() { TRACKER.clear(); }

    private static Map<Integer, Integer> perPlayer(ServerPlayer player) {
        return TRACKER.computeIfAbsent(player, k -> new HashMap<>());
    }
}
