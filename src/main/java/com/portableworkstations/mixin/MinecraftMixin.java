package com.portableworkstations.mixin;

import com.portableworkstations.client.CursorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks close→open screen transitions so MouseHandlerMixin can prevent
 * the cursor centering that normally causes a 1-frame highlight flash.
 * Only affects our mod's workstation GUIs; all other screen changes
 * (vanilla inventory, other mods, chat, etc.) are completely untouched.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static boolean portableworkstations$closePending = false;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreenHead(Screen newScreen, CallbackInfo ci) {
        if (newScreen == null) {
            portableworkstations$closePending = true;
        } else if (portableworkstations$closePending) {
            // close → open sequence detected → skip cursor centering
            CursorState.pendingHoverClear = true;
            portableworkstations$closePending = false;
        }
    }
}
