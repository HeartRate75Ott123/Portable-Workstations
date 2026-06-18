package com.portableworkstations.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── General ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue ENABLED;

    // ── Workstation Definitions ──────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_DEFINITIONS;

    // ── Modded furnace auto-detection ────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue FURNACE_AUTO_DETECT;
    public static final ModConfigSpec.ConfigValue<String> FURNACE_DEFAULT_TYPE;
    /** Speed overrides: "block_id=speed" entries. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FURNACE_SPEEDS_CONFIG;

    private static final Set<String> KNOWN_MENU_TYPES_CONTAINS_FURNACE = Set.of("furnace", "blast_furnace", "smoker");

    static {
        BUILDER.comment("Master mod switch.").push("general");
        ENABLED = BUILDER
                .comment("Set to false to disable the entire mod.")
                .define("enabled", true);
        BUILDER.pop();

        BUILDER.comment("Workstation block → menu type mappings.").push("workstations");
        WORKSTATION_DEFINITIONS = BUILDER
                .comment(
                        "List of \"block_id=menu_type\" entries.",
                        "",
                        "Available menu types: crafting, anvil, smithing, stonecutter,",
                        "grindstone, cartography, loom, furnace, blast_furnace, smoker",
                        "",
                        "Example: \"minecraft:anvil=anvil\""
                )
                .defineListAllowEmpty("definitions", Config::defaultWorkstations, Config::validateWorkstationEntry);
        BUILDER.pop();

        BUILDER.comment(
                "Modded furnace auto-detection.",
                "When enabled, any item whose corresponding block has the LIT",
                "property (Quark, Iron Furnaces, etc.) can be used as a portable",
                "furnace without a manual config entry."
        ).push("furnace_detection");
        FURNACE_AUTO_DETECT = BUILDER
                .comment("Enable auto-detection of modded furnace blocks.")
                .define("auto_detect", true);
        FURNACE_DEFAULT_TYPE = BUILDER
                .comment("Menu type for auto-detected furnaces: furnace, blast_furnace, smoker.")
                .define("default_menu", "furnace",
                        s -> s instanceof String t && KNOWN_MENU_TYPES_CONTAINS_FURNACE.contains(t));
        BUILDER.pop();

        BUILDER.comment(
                "Cooking speed overrides for modded furnaces.",
                "Format: \"block_id=speed\" (speed is an integer; 2 = 2x vanilla)."
        ).push("furnace_speeds");
        FURNACE_SPEEDS_CONFIG = BUILDER
                .comment("List of \"block_id=speed\" overrides.")
                .defineListAllowEmpty("overrides", ArrayList::new, Config::validateSpeedEntry);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static List<String> defaultWorkstations() {
        return List.of(
                "minecraft:crafting_table=crafting",
                "minecraft:anvil=anvil",
                "minecraft:chipped_anvil=anvil",
                "minecraft:damaged_anvil=anvil",
                "minecraft:smithing_table=smithing",
                "minecraft:stonecutter=stonecutter",
                "minecraft:grindstone=grindstone",
                "minecraft:cartography_table=cartography",
                "minecraft:loom=loom",
                "minecraft:furnace=furnace",
                "minecraft:blast_furnace=blast_furnace",
                "minecraft:smoker=smoker"
        );
    }

    private static final Set<String> KNOWN_MENU_TYPES = Set.of(
            "crafting", "anvil", "smithing", "stonecutter",
            "grindstone", "cartography", "loom",
            "furnace", "blast_furnace", "smoker"
    );

    private static boolean validateWorkstationEntry(Object obj) {
        if (!(obj instanceof String entry)) return false;
        int eq = entry.indexOf('=');
        if (eq <= 0 || eq >= entry.length() - 1) return false;
        String blockId = entry.substring(0, eq);
        String menuType = entry.substring(eq + 1);
        if (net.minecraft.resources.ResourceLocation.tryParse(blockId) == null) return false;
        return KNOWN_MENU_TYPES.contains(menuType);
    }

    private static boolean validateSpeedEntry(Object obj) {
        if (!(obj instanceof String entry)) return false;
        int eq = entry.indexOf('=');
        if (eq <= 0 || eq >= entry.length() - 1) return false;
        String blockId = entry.substring(0, eq);
        if (net.minecraft.resources.ResourceLocation.tryParse(blockId) == null) return false;
        try { return Integer.parseInt(entry.substring(eq + 1)) >= 1; }
        catch (NumberFormatException e) { return false; }
    }
}
