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
 * Every screen transition: save cursor in HEAD, restore in TAIL.
 * This survives grabMouse centering because restore runs last.
 * No flag, no conditional logic — always executes.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static double sx, sy;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void save(Screen next, CallbackInfo ci) {
        Minecraft self = (Minecraft)(Object)this;
        long w = self.getWindow().getWindow();
        double[] a = new double[1], b = new double[1];
        GLFW.glfwGetCursorPos(w, a, b);
        sx = a[0];
        sy = b[0];
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void restore(Screen next, CallbackInfo ci) {
        if (next == null) return;
        Minecraft self = (Minecraft)(Object)this;
        long w = self.getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, sx, sy);
    }
}
