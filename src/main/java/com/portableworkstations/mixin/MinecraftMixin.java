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

@Mixin(Minecraft.class)
public class MinecraftMixin {

    /** Set GLFW cursor + MouseHandler internal position. */
    private static void setPos(Minecraft self, double x, double y) {
        long w = self.getWindow().getWindow();
        GLFW.glfwSetCursorPos(w, x, y);
        var acc = (MouseHandlerAccessor) self.mouseHandler;
        acc.setXpos(x);
        acc.setYpos(y);
    }

    /** Early restore: right after releaseMouse, before any init/render. */
    @Inject(method = "setScreen",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/MouseHandler;releaseMouse()V",
                     shift = At.Shift.AFTER))
    private void afterReleaseMouse(Screen next, CallbackInfo ci) {
        if (!CursorState.hasSaved) return;
        setPos((Minecraft)(Object)this, CursorState.savedX, CursorState.savedY);
    }

    /** Late restore: at method return (catches close→grabMouse, handles all paths). */
    @Inject(method = "setScreen", at = @At("RETURN"))
    private void restore(Screen next, CallbackInfo ci) {
        if (!CursorState.hasSaved) return;
        setPos((Minecraft)(Object)this, CursorState.savedX, CursorState.savedY);
        if (next != null) CursorState.hasSaved = false;
    }
}
