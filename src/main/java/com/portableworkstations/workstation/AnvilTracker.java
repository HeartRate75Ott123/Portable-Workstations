package com.portableworkstations.workstation;

import net.minecraft.core.component.DataComponents;
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
 * Anvil damage — UUID is the primary key, slot is just a lookup handle.
 * This guarantees damage always follows the exact item the player clicked,
 * even across inventory moves, GUI reopen, or partial use cycles.
 */
public class AnvilTracker {

    private static final int MAX_STAGES = 3;
    private static final float DAMAGE_CHANCE = 0.12f;
    private static final Random RNG = new Random();

    /** Per-player: UUID → damageStage (0 = anvil, 1 = chipped, 2 = damaged). */
    private static final Map<ServerPlayer, Map<UUID, Integer>> TRACKER = new WeakHashMap<>();

    /**
     * Initialises the tracker stage from the item variant.
     * Called from WorkstationManager when a new UUID is assigned.
     */
    public static void initStage(ServerPlayer player, UUID marker, ItemStack stack) {
        if (marker == null || perPlayer(player).containsKey(marker)) return;
        String p = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int stage = p.equals("damaged_anvil") ? 2 : p.equals("chipped_anvil") ? 1 : 0;
        perPlayer(player).put(marker, stage);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public static void onAnvilUsed(ServerPlayer player) {
        if (RNG.nextFloat() >= DAMAGE_CHANCE) return;

        UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (marker == null) { player.closeContainer(); return; }

        int stage = perPlayer(player).merge(marker, 1, Integer::sum);

        if (stage >= MAX_STAGES) {
            breakMarkedItem(player, marker);
            perPlayer(player).remove(marker);
        } else {
            upgradeMarkedItem(player, marker);
        }
    }

    public static int getDamageLevel(ServerPlayer player, ResourceLocation ignored) {
        UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (marker == null) return 0;
        Integer s = perPlayer(player).get(marker);
        return s != null ? s : 0;
    }

    public static void resetDamage(ServerPlayer player, ResourceLocation ignored) {
        UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (marker != null) perPlayer(player).remove(marker);
    }

    // ── Marker-based inventory operations ───────────────────────────────────

    /** Finds the inventory slot containing the item with the given UUID marker. */
    private static int findSlotByMarker(ServerPlayer player, UUID marker) {
        if (marker == null) return -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack s = player.getInventory().items.get(i);
            if (s.isEmpty()) continue;
            var cd = s.get(DataComponents.CUSTOM_DATA);
            if (cd != null && marker.equals(cd.copyTag().getUUID("pw_marker"))) return i;
        }
        return -1;
    }

    private static void breakMarkedItem(ServerPlayer player, UUID marker) {
        int slot = findSlotByMarker(player, marker);
        if (slot < 0) return;
        ItemStack stack = player.getInventory().items.get(slot);
        if (stack.isEmpty()) return;
        stack.shrink(1);
        if (stack.isEmpty()) player.getInventory().items.set(slot, ItemStack.EMPTY);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static void upgradeMarkedItem(ServerPlayer player, UUID marker) {
        int slot = findSlotByMarker(player, marker);
        if (slot < 0) return;
        var inv = player.getInventory().items;
        ItemStack stack = inv.get(slot);
        if (stack.isEmpty()) return;

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ResourceLocation targetId;
        if (path.equals("anvil"))            targetId = ResourceLocation.withDefaultNamespace("chipped_anvil");
        else if (path.equals("chipped_anvil")) targetId = ResourceLocation.withDefaultNamespace("damaged_anvil");
        else return;

        if (stack.getCount() == 1) {
            // Already a single portable — just upgrade in place
            var upgraded = new ItemStack(BuiltInRegistries.ITEM.get(targetId), 1);
            var cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) upgraded.set(DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(cd.copyTag()));
            upgraded.remove(DataComponents.CUSTOM_NAME);
            inv.set(slot, upgraded);
            WorkstationManager.overrideWorkstationItem(player, targetId);
            return;
        }

        // Stack > 1: split 1 off, upgrade it, put in a free slot, move marker
        stack.shrink(1);
        var tracked = new ItemStack(BuiltInRegistries.ITEM.get(targetId), 1);
        // Copy marker from original stack
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) tracked.set(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(cd.copyTag()));
        // Remove marker from original stack (no longer the tracked item)
        WorkstationManager.unmarkStack(stack);
        inv.set(slot, stack);

        // Place the tracked item in the first free slot, or drop if full
        boolean placed = false;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i).isEmpty()) {
                inv.set(i, tracked);
                WorkstationManager.updateWorkstationSlot(player, i);
                placed = true;
                break;
            }
        }
        if (!placed) player.drop(tracked, false);
        WorkstationManager.overrideWorkstationItem(player, targetId);
    }

    // ── Block placement ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getPlacedBlock().is(BlockTags.ANVIL)) return;

        UUID marker = WorkstationManager.getPlayerWorkstationMarker(player);
        if (marker == null) return;
        Integer stage = perPlayer(player).get(marker);
        if (stage == null || stage <= 0) return;

        var newBlock = switch (stage) {
            case 1 -> Blocks.CHIPPED_ANVIL;
            case 2 -> Blocks.DAMAGED_ANVIL;
            default -> null;
        };
        if (newBlock != null) player.level().setBlock(event.getPos(), newBlock.defaultBlockState(), 3);
        // Tracker kept — UUID survives mining + re-open
    }

    public static void clear() { TRACKER.clear(); }

    private static Map<UUID, Integer> perPlayer(ServerPlayer player) {
        return TRACKER.computeIfAbsent(player, k -> new HashMap<>());
    }
}
