package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.NoParticlesModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Catches every particle that is about to enter the particle engine, no matter which
 * code path created it (level particles, "always visible" particles, block break
 * fragments, ...). The particle engine is the single funnel all particles pass through.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void utilityclient$dropHiddenParticle(ParticleOptions options, double x, double y, double z,
                                                 double dx, double dy, double dz,
                                                 CallbackInfoReturnable<Particle> callback) {
        if (shouldHide(options)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void utilityclient$skipNullParticle(Particle particle, CallbackInfo callback) {
        if (particle == null) {
            callback.cancel();
        }
    }

    private static boolean shouldHide(ParticleOptions options) {
        Module module = ModuleManager.get().find("no-particles");
        return module instanceof NoParticlesModule particles && particles.shouldHideParticle(options);
    }
}
