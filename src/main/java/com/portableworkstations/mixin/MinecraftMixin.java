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
    private boolean pw_consumed = false;

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void restore(Screen next, CallbackInfo ci) {
        if (next == null || !CursorState.hasSaved || pw_consumed) return;
        long w = ((Minecraft)(Object)this).getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, CursorState.savedX, CursorState.savedY);
        pw_consumed = true;
        CursorState.hasSaved = false;
    }
}
