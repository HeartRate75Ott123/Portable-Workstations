package com.portableworkstations.workstation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A custom {@link ContainerLevelAccess} that solves the portable workstation problem:
 * <p>
 * Vanilla menus use {@code access.evaluate()} for two distinct purposes:
 * <ol>
 *   <li><b>Recipe lookups &amp; slot updates</b> — called via {@code access.execute()}
 *       which internally uses the single-argument {@code evaluate(BiFunction)}.
 *       These need a real {@link Level} to function.</li>
 *   <li><b>Distance / block validation</b> — called via the two-argument
 *       {@code evaluate(BiFunction, T defaultValue)} for {@code stillValid()}.
 *       These must return {@code true} so the menu doesn't auto-close.</li>
 * </ol>
 * <p>
 * This implementation provides a real Level for recipe lookups while always
 * returning the default value for stillValid checks, ensuring full menu
 * functionality without requiring the player to stand near a real block.
 */
public final class PortableContainerLevelAccess {

    private PortableContainerLevelAccess() {}

    /**
     * Creates a {@link ContainerLevelAccess} for the given level.
     *
     * @param level the player's level (used for recipe lookups)
     * @return a ContainerLevelAccess that works with portable workstations
     */
    public static ContainerLevelAccess create(Level level) {
        return new ContainerLevelAccess() {
            @Override
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> func) {
                // Used by execute() → provides real level so recipe/slot logic works
                return Optional.of(func.apply(level, BlockPos.ZERO));
            }

            @Override
            public <T> T evaluate(BiFunction<Level, BlockPos, T> func, T defaultValue) {
                // Used by stillValid() → always returns true so menus never auto-close
                return defaultValue;
            }
        };
    }
}
