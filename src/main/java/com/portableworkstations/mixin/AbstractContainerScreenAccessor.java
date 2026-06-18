package com.portableworkstations.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Invoker("findSlot")
    Slot invokeFindSlot(double mouseX, double mouseY);

    @Accessor("hoveredSlot")
    Slot portableworkstations$getHoveredSlot();

    @Accessor("hoveredSlot")
    void portableworkstations$setHoveredSlot(Slot slot);
}
