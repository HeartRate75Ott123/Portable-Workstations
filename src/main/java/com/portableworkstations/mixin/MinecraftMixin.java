package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static boolean portableworkstations$shouldRestore = false;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void portableworkstations$markTransition(Screen newScreen, CallbackInfo ci) {
        portableworkstations$shouldRestore = newScreen != null
                && ((Minecraft) (Object) this).screen != null;
    }

    /** Prevent grabMouse() from centering the cursor during our screen transition. */
    @Redirect(method = "setScreen",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;grabMouse()V"))
    private void portableworkstations$redirectGrabMouse(MouseHandler handler) {
        if (!portableworkstations$shouldRestore) {
            handler.grabMouse(); // normal path: game → game, or first GUI open
        }
        // Our transition: skip grabMouse entirely, cursor stays at saved position.
        // No GLFW center event is generated, no highlight flash.
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void portableworkstations$clearFlag(Screen newScreen, CallbackInfo ci) {
        if (newScreen != null) {
            CursorState.pendingHoverClear = portableworkstations$shouldRestore;
        }
        portableworkstations$shouldRestore = false;
    }
}
