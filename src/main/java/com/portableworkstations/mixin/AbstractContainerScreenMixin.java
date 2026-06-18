package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (CursorState.pendingHoverClear) {
            this.hoveredSlot = null;
            CursorState.pendingHoverClear = false;
        }
    }
}
