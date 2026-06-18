package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Force cursor to saved position at the very end of setScreen, after
 * all vanilla mouse handler calls (releaseMouse/grabMouse) have run.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void restore(Screen next, CallbackInfo ci) {
        if (!CursorState.hasSaved) return;
        long w = ((Minecraft)(Object)this).getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, CursorState.savedX, CursorState.savedY);
        // Only consume on actual opens (next != null). Closes keep the flag
        // alive so the following open can still restore.
        if (next != null) CursorState.hasSaved = false;
    }
}
