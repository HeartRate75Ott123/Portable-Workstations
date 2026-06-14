package com.portableworkstations.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor that exposes the {@code craftSlots} field of
 * {@link CraftingMenu}.
 * <p>
 * This can be used as an alternative to iterating {@code menu.slots} when
 * you need direct access to the transient crafting container, for example
 * to inspect or manipulate the 3×3 grid without touching the result slot.
 * <p>
 * Our primary implementation uses the generic {@code slots} iteration
 * approach (see {@link com.example.examplemod.handler.ContainerCloseHandler}),
 * but this accessor is provided as a safer typed alternative for advanced use.
 */
@Mixin(CraftingMenu.class)
public interface CraftingMenuAccessor {

    @Accessor("craftSlots")
    CraftingContainer portableworkstations$getCraftSlots();
}
