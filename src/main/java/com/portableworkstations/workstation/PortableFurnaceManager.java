package com.portableworkstations.workstation;

import com.portableworkstations.mixin.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages background furnace smelting for portable workstations.
 * Ticks once per server tick matching vanilla {@code AbstractFurnaceBlockEntity}
 * behaviour. Smelting continues after the GUI is closed; items drop on logout.
 */
public class PortableFurnaceManager {

    private static final Map<UUID, FurnaceState> ACTIVE_FURNACES = new HashMap<>();

    /** Item ID → cooking speed multiplier. Registered by mods like Iron Furnaces. */
    private static final Map<String, Float> FURNACE_SPEEDS = new HashMap<>();

    /** Register a cooking speed multiplier (default 1 = vanilla) for a furnace item. */
    public static void registerFurnaceSpeed(String itemId, int speed) {
        FURNACE_SPEEDS.put(itemId, Math.max(1, speed));
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public static FurnaceState startOrGet(ServerPlayer player, ResourceLocation blockId,
                                           RecipeType<? extends AbstractCookingRecipe> recipeType) {
        UUID uuid = player.getUUID();
        FurnaceState state = ACTIVE_FURNACES.get(uuid);
        if (state == null) {
            state = new FurnaceState(player, recipeType);
            ACTIVE_FURNACES.put(uuid, state);
        } else {
            state.recipeType = recipeType;
        }
        // Look up speed multiplier for this furnace item
        Integer s = FURNACE_SPEEDS.get(blockId.toString());
        if (s != null) state.speed = s;
        return state;
    }

    public static void stop(ServerPlayer player) { ACTIVE_FURNACES.remove(player.getUUID()); }
    public static void stop(UUID playerUuid) { ACTIVE_FURNACES.remove(playerUuid); }
    public static void stopAll() { ACTIVE_FURNACES.clear(); }
    @Nullable public static FurnaceState get(UUID playerUuid) { return ACTIVE_FURNACES.get(playerUuid); }

    // ── Block placement sync ────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getPlacedBlock().getBlock() instanceof AbstractFurnaceBlock)) return;


        FurnaceState state = ACTIVE_FURNACES.get(player.getUUID());
        if (state == null) return;
        RecipeType<? extends AbstractCookingRecipe> recipeType;
        if (event.getPlacedBlock().is(Blocks.FURNACE))          recipeType = RecipeType.SMELTING;
        else if (event.getPlacedBlock().is(Blocks.BLAST_FURNACE)) recipeType = RecipeType.BLASTING;
        else if (event.getPlacedBlock().is(Blocks.SMOKER))      recipeType = RecipeType.SMOKING;
        else                                                    recipeType = state.recipeType;

        if (!state.recipeType.equals(recipeType)) return;
        if (!(player.level().getBlockEntity(event.getPos()) instanceof AbstractFurnaceBlockEntity be)) return;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = state.container.getItem(i);
            if (!stack.isEmpty()) { be.setItem(i, stack.copy()); state.container.setItem(i, ItemStack.EMPTY); }
        }

        var acc = (AbstractFurnaceBlockEntityAccessor) be;
        if (state.data.get(0) > 0) { acc.portableworkstations$setLitTime(state.data.get(0)); acc.portableworkstations$setLitDuration(state.data.get(1)); }
        if (state.data.get(2) > 0 || state.data.get(3) > 0) { acc.portableworkstations$setCookingProgress(state.data.get(2)); acc.portableworkstations$setCookingTotalTime(state.data.get(3)); }
        // Set lit=true so the placed furnace shows the fire animation
        if (state.data.get(0) > 0) {
            var litState = event.getPlacedBlock().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true);
            if (litState != null) player.level().setBlock(event.getPos(), litState, 3);
        }
        be.setChanged();

        ACTIVE_FURNACES.remove(player.getUUID());
    }

    // ── Per-player furnace state ────────────────────────────────────────────

    public static class FurnaceState {
        /** Current recipe type; updated each time a menu is opened (shared state). */
        public RecipeType<? extends AbstractCookingRecipe> recipeType;
        public final SimpleContainer container = new SimpleContainer(3);
        public final SimpleContainerData data = new SimpleContainerData(4);
        public final UUID playerUuid;

        /** Cooking speed multiplier (1 = vanilla). Iron Furnaces registers e.g. 2 for gold. */
        int speed = 1;

        /** Accumulated experience from completed smelts not yet awarded. */
        float pendingXp = 0;
        /** Last known result-slot count — detects when the player takes items. */
        int lastResultCount = 0;
        /** Last known input item identity — detects input changes mid-smelt. */
        ItemStack lastInput = ItemStack.EMPTY;

        FurnaceState(ServerPlayer player, RecipeType<? extends AbstractCookingRecipe> recipeType) {
            this.playerUuid = player.getUUID();
            this.recipeType = recipeType;
        }
    }

    // ── Ticking ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_FURNACES.isEmpty()) return;

        ACTIVE_FURNACES.forEach((uuid, state) -> {
            ServerPlayer player = findPlayer(state.playerUuid);
            if (player != null) tickFurnace(player, state);
        });
    }

    @Nullable
    private static ServerPlayer findPlayer(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(uuid) : null;
    }

    private static void tickFurnace(ServerPlayer player, FurnaceState state) {
        Level level = player.level();
        SimpleContainer c = state.container;
        SimpleContainerData d = state.data;

        // ── Early exit when furnace is completely idle ──────────────────────
        ItemStack input = c.getItem(0);
        ItemStack fuel = c.getItem(1);
        ItemStack result = c.getItem(2);
        boolean burning = d.get(0) > 0;
        if (!burning && input.isEmpty() && fuel.isEmpty() && result.isEmpty()) {
            state.lastResultCount = 0;
            return;                     // nothing to do — skip recipe lookup
        }

        // ── 0) Detect input change mid-smelt — reset progress & speed ───────
        boolean inputChanged = !ItemStack.isSameItemSameComponents(input, state.lastInput);
        if (inputChanged && (d.get(2) > 0 || d.get(3) > 0)) {
            d.set(2, 0);   // reset cooking progress
            d.set(3, 0);   // reset total time → will be re-queried from recipe
        }
        state.lastInput = input.copy();

        // ── 1) Decrement burn time ─────────────────────────────────────────
        if (burning) d.set(0, d.get(0) - 1);
        burning = d.get(0) > 0;

        // ── 2) Recipe lookup ───────────────────────────────────────────────
        RecipeHolder<? extends AbstractCookingRecipe> recipe = null;
        boolean haveWork = false;
        if (!input.isEmpty()) {
            recipe = level.getRecipeManager()
                .getRecipeFor(state.recipeType, new SingleRecipeInput(input), level)
                .orElse(null);
            haveWork = recipe != null && canOutputAccept(c, recipe, level);
        }

        // ── 3) Consume fuel (only when there's work) ───────────────────────
        if (!burning && haveWork && !fuel.isEmpty()) {
            int burnTime = AbstractFurnaceBlockEntity.getFuel().getOrDefault(fuel.getItem(), 0);
            if (burnTime > 0) {
                fuel.shrink(1);
                c.setItem(1, fuel);
                d.set(0, burnTime);
                d.set(1, burnTime);
                burning = true;
            }
        }

        // ── 4) Cooking ─────────────────────────────────────────────────────
        if (burning && haveWork) {
            if (d.get(3) == 0) d.set(3, recipe.value().getCookingTime());
            d.set(2, d.get(2) + state.speed);

            if (d.get(2) >= d.get(3)) {
                completeSmelt(c, recipe, level);
                state.pendingXp += recipe.value().getExperience();
                d.set(2, 0);
                d.set(3, 0);
            }
        } else if (d.get(2) > 0 && !haveWork) {
            d.set(2, 0);
        }

        // ── 5) Award XP when player takes result ──────────────────────────
        if (state.pendingXp > 0) {
            int resultCount = c.getItem(2).getCount();
            if (resultCount < state.lastResultCount) {
                int xp = (int) state.pendingXp;
                if (xp > 0) {
                    ExperienceOrb.award(player.serverLevel(), player.position(), xp);
                }
                state.pendingXp = 0;
            }
            state.lastResultCount = resultCount;
        } else {
            state.lastResultCount = c.getItem(2).getCount();
        }
    }

    // ── Recipe helpers ──────────────────────────────────────────────────────

    private static boolean canOutputAccept(SimpleContainer c,
                                           RecipeHolder<? extends AbstractCookingRecipe> recipe, Level level) {
        ItemStack output = recipe.value().getResultItem(level.registryAccess());
        if (output.isEmpty()) return false;
        ItemStack result = c.getItem(2);
        if (result.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(result, output) && result.getCount() < result.getMaxStackSize();
    }

    private static void completeSmelt(SimpleContainer c,
                                      RecipeHolder<? extends AbstractCookingRecipe> recipe, Level level) {
        ItemStack input = c.getItem(0);
        ItemStack output = recipe.value().getResultItem(level.registryAccess());
        ItemStack result = c.getItem(2);
        if (result.isEmpty()) { c.setItem(2, output.copy()); }
        else { result.grow(output.getCount()); c.setItem(2, result.copy()); }
        input.shrink(1);
        c.setItem(0, input);
    }

}
