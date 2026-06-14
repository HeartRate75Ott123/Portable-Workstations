package com.portableworkstations.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the package-private cooking-data fields of
 * {@link AbstractFurnaceBlockEntity} so they can be set when
 * transferring a portable furnace state into a placed block entity.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {

    @Accessor("litTime")
    void portableworkstations$setLitTime(int litTime);

    @Accessor("litDuration")
    void portableworkstations$setLitDuration(int litDuration);

    @Accessor("cookingProgress")
    void portableworkstations$setCookingProgress(int progress);

    @Accessor("cookingTotalTime")
    void portableworkstations$setCookingTotalTime(int totalTime);
}
