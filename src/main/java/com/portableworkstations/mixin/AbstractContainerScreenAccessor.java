package com.portableworkstations.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin invoker that exposes the private {@code findSlot} method of
 * {@link AbstractContainerScreen}.
 * <p>
 * This is needed because {@code AbstractContainerScreen.findSlot(double, double)}
 * is private in vanilla 1.21.1 (previously {@code getSlotAt} in older versions).
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Invoker("findSlot")
    Slot invokeFindSlot(double mouseX, double mouseY);
}
