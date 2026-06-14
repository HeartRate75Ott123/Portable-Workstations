package com.portableworkstations.workstation;

import com.portableworkstations.PortableWorkstations;
import com.portableworkstations.mixin.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
 * <p>
 * Each active furnace state ticks once per server tick, exactly like a
 * real {@code FurnaceBlockEntity}, burning fuel and progressing cooking.
 * Smelting continues even after the player closes the GUI and only stops
 * when all items are processed or fuel runs out.
 * <p>
 * States are keyed by {@link UUID} (not {@link ServerPlayer}) to guarantee
 * consistent lookups across network-thread -> main-thread hops where the
 * player object identity may differ.
 */
public class PortableFurnaceManager {

    /** Per-player furnace state keyed by the player's UUID. Cleaned up on logout. */
    private static final Map<UUID, FurnaceState> ACTIVE_FURNACES = new HashMap<>();

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Starts or retrieves the furnace state for the given player.
     *
     * @param player     the server player
     * @param blockId    the item registry name (for logging)
     * @param recipeType SMELTING / BLASTING / SMOKING
     * @return a {@link FurnaceState}
     */
    public static FurnaceState startOrGet(ServerPlayer player, ResourceLocation blockId,
                                           RecipeType<? extends AbstractCookingRecipe> recipeType) {
        UUID uuid = player.getUUID();
        FurnaceState state = ACTIVE_FURNACES.get(uuid);
        if (state != null && state.recipeType.equals(recipeType)) {
            PortableWorkstations.LOGGER.debug("Reused furnace for {} (items: {}/{}/{}, progress={})",
                    player.getName().getString(),
                    state.container.getItem(0).getDisplayName().getString(),
                    state.container.getItem(1).getDisplayName().getString(),
                    state.container.getItem(2).getDisplayName().getString(),
                    state.data.get(2));
            return state;
        }
        if (state != null) {
            stop(player);
        }
        state = new FurnaceState(player, recipeType);
        ACTIVE_FURNACES.put(uuid, state);
        PortableWorkstations.LOGGER.debug("Created furnace for {} ({})", player.getName().getString(), blockId);
        return state;
    }

    /** Removes and stops a player's furnace state. */
    public static void stop(ServerPlayer player) {
        FurnaceState removed = ACTIVE_FURNACES.remove(player.getUUID());
        if (removed != null) {
            PortableWorkstations.LOGGER.debug("Stopped furnace for {}", player.getName().getString());
        }
    }

    /** Removes and stops by raw UUID (for logout). */
    public static void stop(UUID playerUuid) {
        FurnaceState removed = ACTIVE_FURNACES.remove(playerUuid);
        if (removed != null) {
            PortableWorkstations.LOGGER.debug("Stopped furnace for UUID {}", playerUuid);
        }
    }

    /** Stops all portable furnaces (server shutdown). */
    public static void stopAll() {
        ACTIVE_FURNACES.clear();
    }

    /** Returns the furnace state for a player, or {@code null}. */
    @Nullable
    public static FurnaceState get(UUID playerUuid) {
        return ACTIVE_FURNACES.get(playerUuid);
    }

    // ── Block placement sync ────────────────────────────────────────────────

    /**
     * When the player places a furnace-type block from their inventory, any
     * items and cooking progress stored in the portable {@link FurnaceState}
     * are transferred into the placed {@link AbstractFurnaceBlockEntity}.
     * The portable state is then cleared, preventing item duplication.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Only handle furnace-type blocks
        if (!(event.getPlacedBlock().getBlock() instanceof AbstractFurnaceBlock)) return;

        // Map the block to its recipe type
        RecipeType<? extends AbstractCookingRecipe> recipeType;
        if (event.getPlacedBlock().is(Blocks.FURNACE)) {
            recipeType = RecipeType.SMELTING;
        } else if (event.getPlacedBlock().is(Blocks.BLAST_FURNACE)) {
            recipeType = RecipeType.BLASTING;
        } else if (event.getPlacedBlock().is(Blocks.SMOKER)) {
            recipeType = RecipeType.SMOKING;
        } else {
            return;
        }

        FurnaceState state = ACTIVE_FURNACES.get(player.getUUID());
        if (state == null || !state.recipeType.equals(recipeType)) return;

        // Get the placed block entity
        if (!(player.level().getBlockEntity(event.getPos()) instanceof AbstractFurnaceBlockEntity be)) return;

        // Transfer items (slot layout matches: 0=input 1=fuel 2=result)
        boolean hadItems = false;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = state.container.getItem(i);
            if (!stack.isEmpty()) {
                be.setItem(i, stack.copy());
                state.container.setItem(i, ItemStack.EMPTY);
                hadItems = true;
            }
        }

        // Transfer cooking data
        AbstractFurnaceBlockEntityAccessor acc = (AbstractFurnaceBlockEntityAccessor) be;
        if (state.data.get(0) > 0) {
            acc.portableworkstations$setLitTime(state.data.get(0));
            acc.portableworkstations$setLitDuration(state.data.get(1));
            hadItems = true;
        }
        if (state.data.get(2) > 0 || state.data.get(3) > 0) {
            acc.portableworkstations$setCookingProgress(state.data.get(2));
            acc.portableworkstations$setCookingTotalTime(state.data.get(3));
            hadItems = true;
        }
        be.setChanged(); // mark BE dirty so it saves+ticks properly

        // Stop the portable furnace (prevents duping)
        ACTIVE_FURNACES.remove(player.getUUID());

        if (hadItems) {
            PortableWorkstations.LOGGER.debug("Transferred portable furnace to placed block for {}",
                player.getName().getString());
        }
    }

    // ── Per-player furnace state ────────────────────────────────────────────

    public static class FurnaceState {
        public final RecipeType<? extends AbstractCookingRecipe> recipeType;
        public final SimpleContainer container = new SimpleContainer(3);   // 0=input 1=fuel 2=result
        public final SimpleContainerData data = new SimpleContainerData(4); // 0=burnTime 1=fuelDuration 2=cookingProgress 3=cookingTotalTime
        public final UUID playerUuid;

        FurnaceState(ServerPlayer player, RecipeType<? extends AbstractCookingRecipe> recipeType) {
            this.playerUuid = player.getUUID();
            this.recipeType = recipeType;
        }
    }

    // ── Ticking ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_FURNACES.isEmpty()) return;

        for (Map.Entry<UUID, FurnaceState> entry : ACTIVE_FURNACES.entrySet()) {
            FurnaceState state = entry.getValue();
            ServerPlayer player = findPlayer(state.playerUuid);
            if (player == null) continue; // offline — skip, items dropped on logout
            tickFurnace(player, state);
        }
    }

    /** Resolve a ServerPlayer from UUID (works on the server thread). */
    @Nullable
    private static ServerPlayer findPlayer(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(uuid) : null;
    }

    private static void tickFurnace(ServerPlayer player, FurnaceState state) {
        Level level = player.level();
        SimpleContainer container = state.container;
        SimpleContainerData data = state.data;

        ItemStack input = container.getItem(0);
        ItemStack fuel = container.getItem(1);
        ItemStack result = container.getItem(2);

        boolean wasBurning = data.get(0) > 0;

        // 1) Vanilla fuel burn: always decrement when lit, even with nothing to smelt.
        if (wasBurning) {
            data.set(0, data.get(0) - 1);
        }

        // 2) Refuel when burn time expires (vanilla: consumes fuel regardless of work).
        boolean justLit = false;
        if (data.get(0) <= 0) {
            if (!fuel.isEmpty()) {
                Map<Item, Integer> fuelMap = AbstractFurnaceBlockEntity.getFuel();
                int burnTime = fuelMap.getOrDefault(fuel.getItem(), 0);
                if (burnTime > 0) {
                    fuel.shrink(1);
                    container.setItem(1, fuel);
                    data.set(0, burnTime);
                    data.set(1, burnTime);
                    wasBurning = true;
                    justLit = true;
                }
            }
        }

        // 3) Look up recipe and cook when possible.
        RecipeHolder<? extends AbstractCookingRecipe> recipe = findRecipe(level, container, state.recipeType);
        boolean isBurning = data.get(0) > 0;
        boolean haveWork = recipe != null && canOutputAccept(container, recipe, level);

        if (isBurning && haveWork) {
            if (data.get(3) == 0) {
                data.set(3, recipe.value().getCookingTime());
            }
            data.set(2, data.get(2) + 1);

            // Log progress occasionally (start + every 50 ticks)
            if (data.get(2) == 1 || data.get(2) % 50 == 0) {
                PortableWorkstations.LOGGER.debug("Furnace {}: cooking {}/{}, recipe={}",
                    player.getName().getString(), data.get(2), data.get(3), recipe.id());
            }

            if (data.get(2) >= data.get(3)) {
                completeSmelt(container, recipe, level);
                data.set(2, 0);
                data.set(3, 0);
                PortableWorkstations.LOGGER.debug("Furnace {}: smelt complete",
                    player.getName().getString());
            }
        } else if (data.get(2) > 0 && !haveWork) {
            data.set(2, 0); // reset partial progress
        }

        // 4) Compact logging — only on state transitions
        if (justLit) {
            PortableWorkstations.LOGGER.debug("Furnace {}: lit ({} ticks of fuel)",
                player.getName().getString(), data.get(0));
        }
        if (wasBurning && !isBurning) {
            PortableWorkstations.LOGGER.debug("Furnace {}: fuel exhausted",
                player.getName().getString());
        }
    }

    // ── Recipe helpers ──────────────────────────────────────────────────────

    @Nullable
    private static RecipeHolder<? extends AbstractCookingRecipe> findRecipe(
            Level level, SimpleContainer container,
            RecipeType<? extends AbstractCookingRecipe> recipeType) {
        ItemStack input = container.getItem(0);
        if (input.isEmpty()) return null;
        return level.getRecipeManager()
                .getRecipeFor(recipeType, new SingleRecipeInput(input), level)
                .orElse(null);
    }

    private static boolean canOutputAccept(SimpleContainer container,
                                           RecipeHolder<? extends AbstractCookingRecipe> recipe, Level level) {
        ItemStack output = recipe.value().getResultItem(level.registryAccess());
        if (output.isEmpty()) return false;
        ItemStack result = container.getItem(2);
        if (result.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(result, output)) return false;
        return result.getCount() < result.getMaxStackSize();
    }

    private static void completeSmelt(SimpleContainer container,
                                      RecipeHolder<? extends AbstractCookingRecipe> recipe, Level level) {
        ItemStack input = container.getItem(0);
        ItemStack output = recipe.value().getResultItem(level.registryAccess());
        ItemStack result = container.getItem(2);
        if (result.isEmpty()) {
            container.setItem(2, output.copy());
        } else {
            result.grow(output.getCount());
            container.setItem(2, result.copy());
        }
        input.shrink(1);
        container.setItem(0, input);
    }
}
