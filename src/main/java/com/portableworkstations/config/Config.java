package com.portableworkstations.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

public class Config {
    /** Bump this when the default config layout changes (new sections, new default entries). */
    public static final int CURRENT_CONFIG_VERSION = 1;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── General ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue CONFIG_VERSION;

    // ── Workstation Definitions ──────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_DEFINITIONS;

    // ── Modded furnace auto-detection ────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue FURNACE_AUTO_DETECT;
    public static final ModConfigSpec.ConfigValue<String> FURNACE_DEFAULT_TYPE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FURNACE_SPEEDS_CONFIG;

    /** Subset of menu types valid for the furnace_default_type field. */
    private static final Set<String> KNOWN_MENU_TYPES_CONTAINS_FURNACE = Set.of("furnace", "blast_furnace", "smoker");

    static {
        BUILDER.comment("General settings").push("general");

        CONFIG_VERSION = BUILDER
                .comment("Internal config version. Do not modify.")
                .defineInRange("config_version", CURRENT_CONFIG_VERSION, 1, 100);

        ENABLED = BUILDER
                .comment(
                        "Set to false to disable the entire Portable Workstations mod.",
                        "When disabled, right-clicking workstation items in the inventory",
                        "will behave as normal (stack splitting)."
                )
                .define("enabled", true);

        BUILDER.pop();

        // ── Workstation definitions ─────────────────────────────────────────

        BUILDER.comment("Workstation definitions").push("workstations");

        WORKSTATION_DEFINITIONS = BUILDER
                .comment(
                        "List of supported workstation items in the format \"block_id=menu_type\".",
                        "",
                        "Available menu_type values:",
                        "  crafting      — 3×3 crafting table",
                        "  anvil         — Anvil (rename & repair)",
                        "  smithing      — Smithing table (trim & upgrade)",
                        "  stonecutter   — Stonecutter",
                        "  grindstone    — Grindstone (disenchant & repair)",
                        "  cartography   — Cartography table",
                        "  loom          — Loom (banner patterns)",
                        "  furnace       — Furnace",
                        "  blast_furnace — Blast furnace",
                        "  smoker        — Smoker",
                        "",
                        "Example: \"minecraft:anvil=anvil\"",
                        "         \"minecraft:furnace=furnace\""
                )
                .defineListAllowEmpty("definitions", Config::defaultWorkstations, Config::validateWorkstationEntry);

        BUILDER.pop();

        // ── Furnace auto-detection ──────────────────────────────────────────

        BUILDER.comment(
                "Modded furnace auto-detection.",
                "When enabled, any item whose corresponding block extends",
                "AbstractFurnaceBlock (e.g. Quark variants, Mythic Metals furnaces)",
                "will be treated as a portable workstation without needing a",
                "manual config entry."
        ).push("furnace_auto_detect");

        FURNACE_AUTO_DETECT = BUILDER
                .comment("Set to false to disable auto-detection of modded furnaces.")
                .define("enabled", true);

        FURNACE_DEFAULT_TYPE = BUILDER
                .comment(
                        "Which menu_type to use for auto-detected furnaces.",
                        "Default: \"furnace\" — opens a regular FurnaceMenu with RecipeType.SMELTING.",
                        "Other options: blast_furnace, smoker"
                )
                .define("default_type", "furnace", s -> s instanceof String type && KNOWN_MENU_TYPES_CONTAINS_FURNACE.contains(type));

        BUILDER.pop();

        // ── Furnace speed overrides ───────────────────────────────────────────

        BUILDER.comment(
                "Furnace cooking speed overrides.",
                "Format: \"block_id=speed\" where speed is an integer multiplier.",
                "Speed 2 means twice as fast as vanilla furnace, speed 3 = 3x, etc.",
                "Default speed is 1 (vanilla).",
                "",
                "Example: \"ironfurnaces:gold_furnace=2\"",
                "         \"mythicmetals:mythic_furnace=3\""
        ).push("furnace_speeds");

        FURNACE_SPEEDS_CONFIG = BUILDER
                .comment("List of \"block_id=speed\" entries.")
                .defineListAllowEmpty("entries", List::of, Config::validateSpeedEntry);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Default values ──────────────────────────────────────────────────────

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

    /** Known menu types (used for validation and as the switch cases). */
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

    /** Validates a speed entry: must be "resource_location=speed" where speed ≥ 1. */
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
