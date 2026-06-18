package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static boolean portableworkstations$hasSaved = false;
    @Unique
    private static double portableworkstations$savedX = 0;
    @Unique
    private static double portableworkstations$savedY = 0;

    /** Save cursor before close, restore after open — survives grabMouse centering. */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void portableworkstations$save(Screen newScreen, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.screen != null) {
            long w = self.getWindow().getWindow();
            double[] x = new double[1], y = new double[1];
            GLFW.glfwGetCursorPos(w, x, y);
            portableworkstations$savedX = x[0];
            portableworkstations$savedY = y[0];
            portableworkstations$hasSaved = true;
        }
    }

    /** Restore cursor so it stays where the player right-clicked. */
    @Inject(method = "setScreen", at = @At("TAIL"))
    private void portableworkstations$restore(Screen newScreen, CallbackInfo ci) {
        if (newScreen != null && portableworkstations$hasSaved) {
            long w = ((Minecraft)(Object)this).getWindow().getWindow();
            GLFW.glfwSetCursorPos(w, portableworkstations$savedX, portableworkstations$savedY);
            CursorState.pendingHoverClear = true;
            portableworkstations$hasSaved = false;
        }
    }
}
