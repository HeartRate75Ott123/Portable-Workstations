package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manages cursor flag: when transitioning between screens for our workstation
 * GUIs, set pendingHoverClear so MouseHandlerMixin skips the centering.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreenHead(Screen newScreen, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        CursorState.pendingHoverClear = self.screen != null && newScreen != null;
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void onSetScreenTail(Screen newScreen, CallbackInfo ci) {
        // Flag is read once by MouseHandlerMixin; clear after use.
        // If grabMouse() wasn't called, the flag stays until next transition.
        CursorState.pendingHoverClear = false;
    }
}
