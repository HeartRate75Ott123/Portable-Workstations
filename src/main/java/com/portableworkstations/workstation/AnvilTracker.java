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

public class AnvilTracker {

    private static final int MAX_USES = 3;
    private static final float DAMAGE_CHANCE = 0.12f;

    /** Per-player: normalized item ID → accumulated damage count. */
    private static final Map<ServerPlayer, Map<ResourceLocation, Integer>> DAMAGE_MAP = new WeakHashMap<>();

    // ── Public API ──────────────────────────────────────────────────────────

    public static void onAnvilUsed(ServerPlayer player) {
        if (player.getRandom().nextFloat() >= DAMAGE_CHANCE) return;

        ResourceLocation rawId = WorkstationManager.getPlayerWorkstationItem(player);
        if (rawId == null) return;

        ResourceLocation itemId = normalize(rawId);
        int damage = DAMAGE_MAP
                .computeIfAbsent(player, k -> new HashMap<>())
                .merge(itemId, 1, Integer::sum);

        if (damage >= MAX_USES) {
            breakAnvil(player);
            DAMAGE_MAP.get(player).remove(itemId);
        } else {
            upgradeAnvilVariant(player, damage);
        }
    }

    public static int getDamageLevel(ServerPlayer player, ResourceLocation itemId) {
        Map<ResourceLocation, Integer> map = DAMAGE_MAP.get(player);
        return map != null ? map.getOrDefault(normalize(itemId), 0) : 0;
    }

    public static void resetDamage(ServerPlayer player, ResourceLocation itemId) {
        Map<ResourceLocation, Integer> map = DAMAGE_MAP.get(player);
        if (map != null) map.remove(normalize(itemId));
    }

    // ── Item visual upgrade ─────────────────────────────────────────────────

    /** Normalises any anvil variant to {@code minecraft:anvil} for tracking. */
    private static ResourceLocation normalize(ResourceLocation id) {
        String p = id.getPath();
        if (p.equals("anvil") || p.equals("chipped_anvil") || p.equals("damaged_anvil")) {
            return ResourceLocation.withDefaultNamespace("anvil");
        }
        return id;
    }

    /** Returns the item registry name that corresponds to the given damage level. */
    private static ResourceLocation variantForDamage(int damage) {
        return switch (damage) {
            case 1 -> ResourceLocation.withDefaultNamespace("chipped_anvil");
            case 2 -> ResourceLocation.withDefaultNamespace("damaged_anvil");
            default -> ResourceLocation.withDefaultNamespace("anvil");
        };
    }

    /**
     * Upgrades ALL matching stacks in the player's inventory to the next
     * visual variant. Without this, a player with multiple anvil stacks
     * would have inconsistent item textures.
     */
    private static void upgradeAnvilVariant(ServerPlayer player, int newDamage) {
        ResourceLocation targetId = variantForDamage(newDamage);
        ResourceLocation sourceId = newDamage == 1
                ? ResourceLocation.withDefaultNamespace("anvil")
                : ResourceLocation.withDefaultNamespace("chipped_anvil");

        Item targetItem = BuiltInRegistries.ITEM.get(targetId);
        Inventory inv = player.getInventory();

        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(sourceId)) {
                inv.items.set(i, new ItemStack(targetItem, stack.getCount()));
                // 不 return — 继续遍历其他槽位
            }
        }
    }

    /**
     * When the anvil "breaks" (counter reaches 3), remove one item.
     * At this point the stack has already been upgraded to {@code damaged_anvil}.
     */
    private static void breakAnvil(ServerPlayer player) {
        Inventory inv = player.getInventory();
        ResourceLocation damagedId = ResourceLocation.withDefaultNamespace("damaged_anvil");

        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(damagedId)) {
                stack.shrink(1);
                if (stack.isEmpty()) inv.items.set(i, ItemStack.EMPTY);
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.ANVIL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
                return;
            }
        }
    }

    // ── Block placement sync ────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getPlacedBlock().is(BlockTags.ANVIL)) return;

        ResourceLocation itemId = ResourceLocation.parse("minecraft:anvil");
        int damage = getDamageLevel(player, itemId);
        if (damage <= 0) return;

        // Replace the placed block with the correct damaged variant.
        var newBlock = switch (damage) {
            case 1 -> Blocks.CHIPPED_ANVIL;
            case 2 -> Blocks.DAMAGED_ANVIL;
            default -> null;
        };
        if (newBlock != null) {
            player.level().setBlock(event.getPos(), newBlock.defaultBlockState(), 3);
        }
        resetDamage(player, itemId);
    }

    public static void clear() { DAMAGE_MAP.clear(); }
}
