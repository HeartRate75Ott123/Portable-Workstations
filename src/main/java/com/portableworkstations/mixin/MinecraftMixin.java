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
 * Preserves the mouse cursor position across screen transitions (close → open).
 * <p>
 * Without this mixin, every {@code setScreen(newScreen)} call centers the cursor
 * because {@code MouseHandler.grabMouse()} calls
 * {@code GLFW.glfwSetCursorPos(window, width/2, height/2)}.
 * <p>
 * This mixin saves the cursor position just before the old screen is removed
 * (HEAD of {@code setScreen}) and restores it after the new screen is fully
 * initialised (TAIL of {@code setScreen}).
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static boolean portableworkstations$shouldRestore = false;
    @Unique
    private static double portableworkstations$savedCursorX = 0;
    @Unique
    private static double portableworkstations$savedCursorY = 0;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void portableworkstations$saveCursor(Screen newScreen, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.screen != null) {
            long window = self.getWindow().getWindow();
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(window, x, y);
            portableworkstations$savedCursorX = x[0];
            portableworkstations$savedCursorY = y[0];
            portableworkstations$shouldRestore = true;
        }
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void portableworkstations$restoreCursor(Screen newScreen, CallbackInfo ci) {
        if (newScreen != null && portableworkstations$shouldRestore) {
            Minecraft self = (Minecraft) (Object) this;
            long window = self.getWindow().getWindow();
            GLFW.glfwSetCursorPos(window,
                    portableworkstations$savedCursorX,
                    portableworkstations$savedCursorY);
            portableworkstations$shouldRestore = false;
        }
    }
}
