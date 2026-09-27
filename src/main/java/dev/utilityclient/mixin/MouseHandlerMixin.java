package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.FreeCamModule;
import dev.utilityclient.module.impl.FreeLookModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the final player rotation so Free Look and Free Cam can consume the mouse
 * deltas instead. Vanilla has already applied sensitivity, smoothing and invert by the
 * time we see them, so the feel matches the real game exactly.
 */
@Mixin(net.minecraft.client.MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void utilityclient$cameraLook(LocalPlayer player, double yawDelta, double pitchDelta) {
        ModuleManager modules = ModuleManager.get();

        Module freeCam = modules.find("freecam");
        if (freeCam instanceof FreeCamModule cam && cam.enabled() && cam.handleLook(yawDelta, pitchDelta)) {
            return;
        }

        Module freeLook = modules.find("freelook");
        if (freeLook instanceof FreeLookModule look && look.enabled() && look.handleLook(yawDelta, pitchDelta)) {
            return;
        }

        player.turn(yawDelta, pitchDelta);
    }
}
