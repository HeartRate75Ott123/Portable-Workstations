# Portable Workstations

**A NeoForge 1.21.1 mod** that lets you right-click workstation items **directly in your inventory** to open their GUIs — no need to place them in the world.

## Features

- **10 supported workstations**: Crafting Table, Anvil, Smithing Table, Stonecutter, Grindstone, Cartography Table, Loom, Furnace, Blast Furnace, Smoker
- **No blocks placed**: The GUI opens from the item itself, just right-click it in your inventory
- **Smart conditions**: Only activates when your cursor is *empty* (so vanilla stack-splitting still works when you're holding items)
- **Leftover recovery**: Items left in the work area (e.g., crafting grid, anvil inputs, furnace slots) are returned to your inventory when you close the GUI. Overflow drops at your feet.
- **Fully configurable**: Enable/disable the mod, add or remove supported blocks, all via a TOML config file
- **Mod compatible**: Uses vanilla menu classes — plays nicely with JEI, REI, and other inventory mods

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233+ |
| Java | 21 |

## Building from Source

```bash
# Clone or download the source, then:
./gradlew build
```

The compiled JAR will be in `build/libs/portableworkstations-1.0.0.jar`.

## Installation

1. Install **NeoForge 21.1.233+** for Minecraft 1.21.1
2. Place the mod JAR (`portableworkstations-1.0.0.jar`) in your `mods/` folder
3. Launch the game

## Configuration

After running the mod once, the config file is created at:

```
config/portableworkstations-common.toml
```

### General Settings

```toml
[general]
    # Set to false to disable the entire mod.
    enabled = true
```

### Workstation Definitions

```toml
[workstations]
    definitions = [
        "minecraft:crafting_table=crafting",
        "minecraft:anvil=anvil",
        "minecraft:furnace=furnace",
        # ... add your own entries or remove lines
    ]
```

**Format**: `"<block_id>=<menu_type>"`

**Available menu types**:

| Menu Type | GUI |
|---|---|
| `crafting` | 3×3 Crafting Table |
| `anvil` | Anvil (rename & repair) |
| `smithing` | Smithing Table (trim & upgrade) |
| `stonecutter` | Stonecutter |
| `grindstone` | Grindstone (disenchant & repair) |
| `cartography` | Cartography Table |
| `loom` | Loom (banner patterns) |
| `furnace` | Furnace |
| `blast_furnace` | Blast Furnace |
| `smoker` | Smoker |

**Example — add a custom modded workstation**:

```toml
definitions = [
    "minecraft:crafting_table=crafting",
    "minecraft:anvil=anvil",
    "mythicmetals:mythic_anvil=anvil"
]
```

## Usage

1. Open your inventory (**E** by default)
2. **Right-click** a workstation item (e.g., a Crafting Table) while your cursor is **empty** (not holding a stack)
3. The workstation GUI opens immediately
4. When you close it, any items remaining in the work slots are returned to your inventory

> **Tip**: If the GUI doesn't open, make sure:
> - The mod is enabled in config (`enabled = true`)
> - You're right-clicking (not left-clicking)
> - Your mouse cursor isn't carrying a stack of items

## Mod Compatibility

- **JEI / REI / EMI**: Fully compatible — the workstation menus are vanilla classes, so recipe viewers work normally
- **Other inventory mods**: The mod only intercepts right-clicks on the *survival* inventory screen (`InventoryScreen`) and only when conditions are exactly right. It does not cancel other mods' events.
- **Custom workstations**: Add any block/item from any mod via the config file
- **Plugin API for custom menus** (`Iron Furnaces`, etc.): Mods can register their own menu factories for custom menu types. In your `@Mod` constructor or `FMLCommonSetupEvent`:
  ```java
  WorkstationManager.registerMenuFactory("iron_furnace", (id, inv, player) -> {
      // Return your custom menu instance. For furnace-type menus the
      // PortableFurnaceManager shares a single FurnaceState per player:
      var state = PortableFurnaceManager.get(player.getUUID());
      if (state != null) {
          // Create your menu using state.container and state.data
      }
      return new MyCustomMenu(id, inv);
  });
  ```
  Then users add `"my_mod:my_furnace=iron_furnace"` to the config definitions.

## How It Works

1. **Client**: `ScreenEvent.MouseButtonPressed.Pre` is intercepted when inside `InventoryScreen`. If the click is a right-click with an empty cursor on a workstation item, the event is cancelled (preventing stack-splitting) and a network packet is sent to the server.
2. **Server**: The packet receiver calls `WorkstationManager.openWorkstation()`, which reads the config to find the matching menu type and opens the corresponding vanilla menu via `player.openMenu()`.
3. **Cleanup**: When the portable menu is closed, `PlayerContainerEvent.Close` fires. Items in non-player-inventory slots are moved back to the player's inventory (or dropped if full).

## Chest / Ender Chest Support (Not Yet Implemented)

**Chest storage** (right-clicking a chest item to open a portable container) is **not implemented** in this version. This feature would require:

- A custom inventory capability (attached to the player or item)
- A custom menu and screen handler
- Network synchronisation for container contents
- Persistent storage (saving to player data or item NBT)

This is a significantly larger feature that merits its own focused implementation. If you need this functionality, please open a feature request or consider it for a future release.

## Project Structure

```
src/main/java/com/example/examplemod/
├── PortableWorkstations.java          # @Mod main class
├── PortableWorkstationsClient.java    # Client-side setup
├── config/
│   └── Config.java                    # ModConfigSpec definitions
├── handler/
│   ├── ContainerCloseHandler.java     # Leftover item recovery
│   ├── InventoryClickHandler.java     # Client right-click detection
│   └── ServerPayloadHandler.java      # Network packet handling
├── mixin/
│   └── CraftingMenuAccessor.java      # @Accessor for craftSlots
├── network/
│   └── OpenWorkstationPayload.java    # C2S packet definition
└── workstation/
    └── WorkstationManager.java        # Menu creation & tracking

src/main/resources/
├── META-INF/neoforge.mods.toml        # Mod metadata (template)
├── portableworkstations.mixins.json   # Mixin configuration
├── assets/portableworkstations/lang/en_us.json
└── data/portableworkstations/workstations.json  # Reference data
```

## License

All Rights Reserved. See [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) for details.
