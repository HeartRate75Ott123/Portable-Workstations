package com.portableworkstations.workstation;

import com.portableworkstations.PortableWorkstations;
import com.portableworkstations.config.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core manager that reads workstation definitions from the config,
 * opens the corresponding vanilla menu on the server, and tracks
 * open menus for cleanup and auto-close.
 * <p>
 * All methods are safe to call from the server game thread only.
 */
public class WorkstationManager {

    /** Tracks which open containers were opened by this mod. */
    private static final Set<AbstractContainerMenu> PORTABLE_MENUS = new HashSet<>();

    /** Maps player → item ID used to open their current portable workstation. */
    private static final Map<ServerPlayer, ResourceLocation> PLAYER_WORKSTATION_ITEM = new WeakHashMap<>();
    /** Maps player → inventory slot index of the item used to open the workstation. */
    private static final Map<ServerPlayer, Integer> PLAYER_WORKSTATION_SLOT = new WeakHashMap<>();
    /** Maps player → stack count at open time (used by AnvilTracker for slot resolution). */
    private static final Map<ServerPlayer, Integer> PLAYER_WORKSTATION_COUNT = new WeakHashMap<>();
    /** Transient UUID marker on the item, so indistinguishable stacks can be told apart. */
    private static final Map<ServerPlayer, java.util.UUID> PLAYER_WORKSTATION_MARKER = new WeakHashMap<>();
    /** For split anvil tracking: slot that holds the main stack */
    private static final Map<ServerPlayer, Integer> PLAYER_WORKSTATION_ORIGINAL_SLOT = new WeakHashMap<>();
    /** Menu type of the currently tracked workstation. */
    private static final Map<ServerPlayer, String> PLAYER_MENU_TYPE = new WeakHashMap<>();

    // ── Cache ───────────────────────────────────────────────────────────────

    /** Lazily-built cache: blockId → menuType from config. Rebuilt on reload. */
    private static volatile Map<ResourceLocation, String> WORKSTATION_CACHE = null;

    private static Map<ResourceLocation, String> getCache() {
        Map<ResourceLocation, String> c = WORKSTATION_CACHE;
        if (c != null) return c;
        c = new ConcurrentHashMap<>();
        for (String entry : Config.WORKSTATION_DEFINITIONS.get()) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, eq));
                if (id != null) c.put(id, entry.substring(eq + 1));
            }
        }
        WORKSTATION_CACHE = c;
        return c;
    }

    /** Called from the mod event bus when the config is reloaded. */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            WORKSTATION_CACHE = null; // rebuild on next access
        }
    }

    // ── Config querying ─────────────────────────────────────────────────────

    /** Returns true for the three vanilla anvil variants (never needs config). */
    private static boolean isAnvilVariant(ResourceLocation id) {
        String p = id.getPath();
        return p.equals("anvil") || p.equals("chipped_anvil") || p.equals("damaged_anvil");
    }

    public static boolean isWorkstationItem(ResourceLocation itemId) {
        if (getCache().containsKey(itemId)) return true;
        // Auto-detect anvil variants (chipped/damaged) even without config entries
        if (isAnvilVariant(itemId)) return true;
        // Auto-detect modded furnaces (Quark, More Furnaces, etc.)
        return Config.FURNACE_AUTO_DETECT.getAsBoolean()
            && Block.byItem(BuiltInRegistries.ITEM.get(itemId)) instanceof AbstractFurnaceBlock;
    }

    @Nullable
    public static String getMenuType(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id != null) {
            String type = getCache().get(id);
            if (type != null) return type;
            // Auto-detect anvil variants → always "anvil"
            if (isAnvilVariant(id)) return "anvil";
        }
        // Fallback for auto-detected furnaces
        if (Config.FURNACE_AUTO_DETECT.getAsBoolean() && id != null) {
            if (Block.byItem(BuiltInRegistries.ITEM.get(id)) instanceof AbstractFurnaceBlock) {
                return Config.FURNACE_DEFAULT_TYPE.get();
            }
        }
        return null;
    }

    /**
     * Returns the item ID the player used to open their current workstation,
     * or {@code null} if the player has no open portable workstation.
     */
    @Nullable
    public static ResourceLocation getPlayerWorkstationItem(ServerPlayer player) {
        return PLAYER_WORKSTATION_ITEM.get(player);
    }

    /** Returns the inventory slot the player clicked to open the workstation, or -1. */
    public static int getPlayerWorkstationSlot(ServerPlayer player) {
        return PLAYER_WORKSTATION_SLOT.getOrDefault(player, -1);
    }

    /** Returns the stored marker UUID, or null. */
    @Nullable public static java.util.UUID getPlayerWorkstationMarker(ServerPlayer player) {
        return PLAYER_WORKSTATION_MARKER.get(player);
    }

    /** Updates the tracked item ID (used by AnvilTracker after item upgrade). */
    public static void overrideWorkstationItem(ServerPlayer player, ResourceLocation newId) {
        PLAYER_WORKSTATION_ITEM.put(player, newId);
    }

    // ─── CUSTOM_DATA marker helpers ────────────────────────────────────

    /** Returns true if the stack already has our pw_marker UUID. */
    public static boolean hasMarker(ItemStack stack) {
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().hasUUID("pw_marker");
    }

    /** Adds pw_marker to the stack (no split, no rename). Returns the marker UUID. */
    public static UUID markStack(ItemStack stack) {
        UUID marker = UUID.randomUUID();
        var tag = new CompoundTag();
        tag.putUUID("pw_marker", marker);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return marker;
    }

    /** Removes pw_marker + any CUSTOM_NAME from the stack. */
    public static void unmarkStack(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.CUSTOM_NAME);
    }

    public static int countTrackedAnvils(ServerPlayer player) {
        int n = 0;
        for (var stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            var cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null && cd.copyTag().hasUUID("pw_marker")) n++;
        }
        return n;
    }

    /** Clears the marker tag from the item in the tracked slot (called after operation). */
    public static void clearMarkerFromSlot(ServerPlayer player) {
        int slot = PLAYER_WORKSTATION_SLOT.getOrDefault(player, -1);
        java.util.UUID marker = PLAYER_WORKSTATION_MARKER.remove(player);
        if (marker == null || slot < 0 || slot >= player.getInventory().items.size()) return;
        ItemStack stack = player.getInventory().items.get(slot);
        if (!stack.isEmpty()) {
            var cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null && marker.equals(cd.copyTag().getUUID("pw_marker"))) {
                unmarkStack(stack);
                player.getInventory().items.set(slot, stack);
            }
        }
    }

    /** Updates the tracked slot index (used by AnvilTracker on slot migration). */
    public static void updateWorkstationSlot(ServerPlayer player, int newSlot) {
        if (newSlot >= 0) {
            PLAYER_WORKSTATION_SLOT.put(player, newSlot);
            PLAYER_WORKSTATION_COUNT.put(player, player.getInventory().items.get(newSlot).getCount());
        }
    }

    /** Returns the expected stack count at open time, or 0. */
    public static int getPlayerWorkstationCount(ServerPlayer player) {
        return PLAYER_WORKSTATION_COUNT.getOrDefault(player, 0);
    }

    // findSlotForItem removed — slot index is sent from client via OpenWorkstationPayload

    // ── Menu opening ────────────────────────────────────────────────────────

    /**
     * Opens a workstation menu for the given player.
     * <p>
     * Called from the server payload handler.
     *
     * @param player  the server-side player
     * @param blockId the registry name of the clicked item
     */
    public static void openWorkstation(ServerPlayer player, String blockId, int slotIndex) {
        if (!Config.ENABLED.getAsBoolean()) return;

        String menuType = getMenuType(blockId);
        if (menuType == null) return;

        ResourceLocation itemId = ResourceLocation.parse(blockId);
        MenuProvider provider = createMenuProvider(menuType, player);
        if (provider == null) return;

        // Set menu type BEFORE openMenu so closeContainer → ContainerCloseHandler
        // can read the correct type and skip cleanupPlayer for anvil menus.
        PLAYER_MENU_TYPE.put(player, menuType);
        player.openMenu(provider);
        PORTABLE_MENUS.add(player.containerMenu);

        // Let closeContainer → ContainerCloseHandler clean up old tracking
        // (merges back portable anvils) for non-anvil types automatically.
        // For anvils we DON'T want that merge — the portable already has its
        // UUID marker and should stay in its slot; only the WS tracking maps
        // need updating.
        PLAYER_WORKSTATION_ITEM.put(player, itemId);

        // Validate & resolve the slot
        int slot = -1;
        if (slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
            var s = player.getInventory().items.get(slotIndex);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId))
                slot = slotIndex;
        }
        if (slot < 0) return;

        var stack = player.getInventory().items.get(slot);
        boolean isAnvil = "anvil".equals(menuType);

        if (isAnvil) {
            // Temporary marker for this session — removed on GUI close.
            if (countTrackedAnvils(player) >= 1) { player.closeContainer(); return; }
            UUID marker = markStack(stack);
            player.getInventory().items.set(slot, stack);
            PLAYER_WORKSTATION_MARKER.put(player, marker);
            PLAYER_WORKSTATION_SLOT.put(player, slot);
            PLAYER_WORKSTATION_COUNT.put(player, stack.getCount());
            AnvilTracker.initStage(player, marker, stack);
        } else {
            PLAYER_WORKSTATION_SLOT.put(player, slot);
            PLAYER_WORKSTATION_COUNT.put(player, stack.getCount());
        }
    }

    /**
     * Creates the appropriate {@link MenuProvider} for the given menu type.
     * <p>
     * Uses {@link PortableContainerLevelAccess} so recipe lookups work
     * while {@code stillValid()} always returns {@code true}.
     * <p>
     * For furnace-type menus the backing container and data come from
     * {@link PortableFurnaceManager} so smelting ticks in the background.
     * For anvil menus a trackable subclass records damage on the item.
     */
    @Nullable
    private static MenuProvider createMenuProvider(String menuType, ServerPlayer player) {
        Component title = getTitle(menuType);
        var access = PortableContainerLevelAccess.create(player.level());

        return new SimpleMenuProvider((containerId, inventory, unused) -> {
            return switch (menuType) {
                case "crafting"       -> new CraftingMenu(containerId, inventory, access);
                case "anvil"          -> new TrackedAnvilMenu(containerId, inventory, access);
                case "smithing"       -> new SmithingMenu(containerId, inventory, access);
                case "stonecutter"    -> new StonecutterMenu(containerId, inventory, access);
                case "grindstone"     -> new GrindstoneMenu(containerId, inventory, access);
                case "cartography"    -> new CartographyTableMenu(containerId, inventory, access);
                case "loom"           -> new LoomMenu(containerId, inventory, access);
                case "furnace" -> {
                    var state = PortableFurnaceManager.startOrGet(player,
                            ResourceLocation.parse("minecraft:furnace"), RecipeType.SMELTING);
                    yield new FurnaceMenu(containerId, inventory, state.container, state.data);
                }
                case "blast_furnace" -> {
                    var state = PortableFurnaceManager.startOrGet(player,
                            ResourceLocation.parse("minecraft:blast_furnace"), RecipeType.BLASTING);
                    yield new BlastFurnaceMenu(containerId, inventory, state.container, state.data);
                }
                case "smoker" -> {
                    var state = PortableFurnaceManager.startOrGet(player,
                            ResourceLocation.parse("minecraft:smoker"), RecipeType.SMOKING);
                    yield new SmokerMenu(containerId, inventory, state.container, state.data);
                }
                default -> throw new IllegalArgumentException("Unknown menu type: " + menuType);
            };
        }, title);
    }

    /**
     * Returns the GUI title for a portable workstation.
     * Uses a mod-scoped key so it doesn't overwrite the vanilla
     * {@code container.xxx} translations globally.
     */
    private static Component getTitle(String menuType) {
        return Component.translatable("portableworkstations.container." + menuType);
    }

    // ── Tracking ────────────────────────────────────────────────────────────

    public static boolean isPortableMenu(AbstractContainerMenu menu) {
        return PORTABLE_MENUS.contains(menu);
    }

    public static void removePortableMenu(AbstractContainerMenu menu) {
        PORTABLE_MENUS.remove(menu);
    }

    /**
     * Cleans up tracking for a player when their portable workstation closes.
     * <p>
     * Does NOT stop furnace background smelting — that continues until
     * processing is complete or the player logs out.
     */
    /** Returns the menu type of the currently open portable workstation. */
    @Nullable
    public static String getPlayerMenuType(ServerPlayer player) {
        return PLAYER_MENU_TYPE.get(player);
    }

    public static void cleanupPlayer(ServerPlayer player) {
        clearMarkerFromSlot(player);
        PLAYER_MENU_TYPE.remove(player);
        PLAYER_WORKSTATION_ITEM.remove(player);
        PLAYER_WORKSTATION_SLOT.remove(player);
        PLAYER_WORKSTATION_COUNT.remove(player);
        PLAYER_WORKSTATION_MARKER.remove(player);
        PORTABLE_MENUS.remove(player.containerMenu);
    }

    // ── Auto-close when workstation item is lost ───────────────────────────

    /**
     * Checks on each player tick whether the workstation item is still in the
     * player's inventory. If it has been dropped / consumed, the portable
     * workstation GUI is automatically closed.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation expectedItem = PLAYER_WORKSTATION_ITEM.get(player);
        if (expectedItem == null) return;

        // Quick check: is the current menu still one of ours?
        if (!PORTABLE_MENUS.contains(player.containerMenu)) {
            cleanupPlayer(player);
            return;
        }

        // Check if the player still has the workstation item in their inventory
        // (anvil variants normalised via AnvilTracker — chipped/damaged also match)
        boolean stillHasItem = player.getInventory().hasAnyMatching(stack -> {
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id.equals(expectedItem)) return true;
            // Anvil family: tracker normalises all to minecraft:anvil
            if (expectedItem.getPath().equals("anvil")) {
                String p = id.getPath();
                return p.equals("chipped_anvil") || p.equals("damaged_anvil");
            }
            return false;
        });

        if (!stillHasItem) {
            player.closeContainer();
            cleanupPlayer(player);
        }
    }

    // ── Player logout cleanup ───────────────────────────────────────────────

    /**
     * When a player logs out, clean up all portable workstation state including
     * background furnace smelting. Any items left in the furnace are dropped
     * at the logout position so they aren't lost.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Stop background furnace and drop remaining items
        PortableFurnaceManager.FurnaceState state = PortableFurnaceManager.get(player.getUUID());
        if (state != null && state.container != null) {
            for (int i = 0; i < state.container.getContainerSize(); i++) {
                ItemStack stack = state.container.getItem(i);
                if (!stack.isEmpty()) {
                    state.container.setItem(i, ItemStack.EMPTY);
                    player.drop(stack, false);
                }
            }
        }
        PortableFurnaceManager.stop(player.getUUID());
        cleanupPlayer(player);
    }

    // ── Tracked anvil menu (inner class) ────────────────────────────────────

    /**
     * Thin subclass of {@link AnvilMenu} that hooks {@code onTake} to
     * record anvil damage via {@link AnvilTracker}.
     * <p>
     * This is the only place anvil items are "damaged", matching the
     * vanilla 12 % chance. The rest of the menu logic is 100 % vanilla.
     */
    private static class TrackedAnvilMenu extends AnvilMenu {
        TrackedAnvilMenu(int containerId, Inventory inv, ContainerLevelAccess access) {
            super(containerId, inv, access);
        }

        @Override
        protected void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            if (!player.level().isClientSide() && player instanceof ServerPlayer sp) {
                AnvilTracker.onAnvilUsed(sp);
            }
        }
    }
}
