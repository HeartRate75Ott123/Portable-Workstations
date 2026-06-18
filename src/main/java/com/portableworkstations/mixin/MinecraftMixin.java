package com.portableworkstations.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserve cursor position across close→open screen transitions.
 * Saves before the close, restores after the open — survives grabMouse.
 * For standalone opens (e.g. InventoryScreen → first workstation),
 * save and restore happen in the same setScreen call, so the cursor
 * stays where it was.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static double sx, sy;
    @Unique
    private static boolean saved = false;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void save(Screen next, CallbackInfo ci) {
        if (saved) return; // already captured from the close
        Minecraft self = (Minecraft)(Object)this;
        long w = self.getWindow().getWindow();
        double[] a = new double[1], b = new double[1];
        GLFW.glfwGetCursorPos(w, a, b);
        sx = a[0]; sy = b[0];
        saved = true;
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void restore(Screen next, CallbackInfo ci) {
        if (next == null) { saved = false; return; }
        if (!saved) return;
        Minecraft self = (Minecraft)(Object)this;
        long w = self.getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, sx, sy);
        saved = false;
    }
}
