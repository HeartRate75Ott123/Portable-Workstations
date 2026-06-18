package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After grabMouse() centers + disables the cursor, immediately restore
 * our saved position and switch cursor to NORMAL so it's visible again.
 * Without this, glfwSetCursorPos has no visible effect because the
 * cursor is in GLFW_CURSOR_DISABLED mode.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "setScreen",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/MouseHandler;grabMouse()V",
                     shift = At.Shift.AFTER))
    private void afterGrabMouse(Screen next, CallbackInfo ci) {
        if (!CursorState.hasSaved) return;
        long w = ((Minecraft)(Object)this).getWindow().getWindow();
        GLFW.glfwSetInputMode(w, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(w, CursorState.savedX, CursorState.savedY);
        CursorState.hasSaved = false;
    }
}
