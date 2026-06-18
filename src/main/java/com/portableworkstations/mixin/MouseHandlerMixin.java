package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents MouseHandler.grabMouse() from centering the cursor during
 * our screen transitions, eliminating the 1-frame center highlight.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow private boolean isGrabbing;
    @Shadow private double x;
    @Shadow private double y;

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void onGrabMouse(CallbackInfo ci) {
        if (!CursorState.pendingHoverClear) return; // normal path

        // Our transition: just mark as grabbing without centering.
        // Cursor stays exactly where the player right-clicked.
        this.isGrabbing = true;
        ci.cancel();
    }
}
