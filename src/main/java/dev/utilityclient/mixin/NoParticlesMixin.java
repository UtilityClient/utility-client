package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.NoParticlesModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class NoParticlesMixin {
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void utilityclient$hideBlockParticle(ParticleOptions options, double x, double y, double z,
                                                  double dx, double dy, double dz, CallbackInfo callback) {
        if (shouldHide(options)) {
            callback.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void utilityclient$hideBlockParticle(ParticleOptions options, boolean force, boolean ignoreRange,
                                                  double x, double y, double z, double dx, double dy, double dz,
                                                  CallbackInfo callback) {
        if (shouldHide(options)) {
            callback.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void utilityclient$hideAlwaysVisibleBlockParticle(ParticleOptions options, double x, double y, double z,
                                                              double dx, double dy, double dz, CallbackInfo callback) {
        if (shouldHide(options)) {
            callback.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void utilityclient$hideAlwaysVisibleBlockParticle(ParticleOptions options, boolean force,
                                                              double x, double y, double z, double dx, double dy, double dz,
                                                              CallbackInfo callback) {
        if (shouldHide(options)) {
            callback.cancel();
        }
    }

    private static boolean shouldHide(ParticleOptions options) {
        Module module = ModuleManager.get().find("no-particles");
        return module instanceof NoParticlesModule particles && particles.shouldHideParticle(options);
    }
}
