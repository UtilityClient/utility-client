package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.CrosshairModule;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the vanilla crosshair while the crosshair module is active, so only the
 * client side crosshair model is drawn.
 */
@Mixin(Hud.class)
public abstract class VanillaCrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void utilityclient$hideVanillaCrosshair(CallbackInfo callback) {
        Module module = ModuleManager.get().find("crosshair");
        if (module instanceof CrosshairModule crosshair && crosshair.enabled() && crosshair.removeVanilla.value()) {
            callback.cancel();
        }
    }
}
