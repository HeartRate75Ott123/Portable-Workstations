package com.portableworkstations.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

/**
 * Mod configuration using NeoForge's ModConfigSpec.
 * <p>
 * Settings:
 * <ul>
 *   <li>{@code general.enabled} — Master toggle for the entire mod.</li>
 *   <li>{@code workstations.definitions} — List of "block_id=menu_type" entries.</li>
 * </ul>
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── General ──────────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue ENABLED;

    // ── Workstation Definitions ──────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_DEFINITIONS;

    static {
        BUILDER.comment("General settings").push("general");

        ENABLED = BUILDER
                .comment(
                        "Set to false to disable the entire Portable Workstations mod.",
                        "When disabled, right-clicking workstation items in the inventory",
                        "will behave as normal (stack splitting)."
                )
                .define("enabled", true);

        BUILDER.pop();

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
                        "Example: \"minecraft:crafting_table=crafting\"",
                        "         \"minecraft:furnace=furnace\""
                )
                .defineListAllowEmpty("definitions", Config::defaultWorkstations, Config::validateWorkstationEntry);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Default values ──────────────────────────────────────────────────────

    private static List<String> defaultWorkstations() {
        return List.of(
                "minecraft:crafting_table=crafting",
                "minecraft:anvil=anvil",
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

    /**
     * Validates a single config entry: must be "resource_location=menu_type".
     */
    private static boolean validateWorkstationEntry(Object obj) {
        if (!(obj instanceof String entry)) return false;
        int eq = entry.indexOf('=');
        if (eq <= 0 || eq >= entry.length() - 1) return false;

        String blockId = entry.substring(0, eq);
        String menuType = entry.substring(eq + 1);

        // Validate blockId as a ResourceLocation
        if (net.minecraft.resources.ResourceLocation.tryParse(blockId) == null) return false;

        // Validate menuType
        return KNOWN_MENU_TYPES.contains(menuType);
    }
}
