package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static double sx, sy;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void save(Screen next, CallbackInfo ci) {
        if (!CursorState.pendingHoverClear) return;
        long w = ((Minecraft)(Object)this).getWindow().getWindow();
        double[] a = new double[1], b = new double[1];
        GLFW.glfwGetCursorPos(w, a, b);
        sx = a[0]; sy = b[1];
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void restore(Screen next, CallbackInfo ci) {
        // Belt-and-suspenders: even if MouseHandlerMixin already cancelled
        // grabMouse, this restoration confirms the cursor is at the right spot.
        if (!CursorState.pendingHoverClear || next == null) return;
        long w = ((Minecraft)(Object)this).getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, sx, sy);
    }
}
