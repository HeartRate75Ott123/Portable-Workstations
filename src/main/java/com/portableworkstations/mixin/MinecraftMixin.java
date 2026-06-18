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
 * Cursor position: saved by InventoryClickHandler at right-click time,
 * consumed by this mixin's restore in setScreen TAIL.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    /** Set from InventoryClickHandler before sending the packet. */
    public static double portableworkstations$savedX = 0;
    public static double portableworkstations$savedY = 0;
    public static boolean portableworkstations$hasSaved = false;

    /** Consumed by restore. */
    @Unique
    private boolean pw_consumed = false;

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void restore(Screen next, CallbackInfo ci) {
        if (next == null || !portableworkstations$hasSaved || pw_consumed) return;
        long w = ((Minecraft)(Object)this).getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, portableworkstations$savedX, portableworkstations$savedY);
        pw_consumed = true;
        portableworkstations$hasSaved = false;
    }
}
