package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;

public final class NoParticlesModule extends Module {
    public final ModuleSetting<String> level;
    public final ModuleSetting<Boolean> hideBlockBreakParticles;
    private ParticleStatus previous;
    private boolean hasPrevious;

    public NoParticlesModule() {
        super("no-particles", "No Particles", "Reduces particle rendering for a cleaner view.", ModuleCategory.VISUAL, false, false, false);
        level = addSetting(ModuleSetting.modeSetting("level", "Particle level", "How aggressively particles are reduced.",
                "Minimal", "Minimal", "Decreased"));
        hideBlockBreakParticles = addSetting(ModuleSetting.booleanSetting("hide-block-break", "Hide block-break particles",
                "Hide block texture particles caused by breaking blocks.", true));
    }

    @Override
    public void onEnable() {
        Minecraft client = Minecraft.getInstance();
        previous = client.options.particles().get();
        hasPrevious = true;
        apply();
    }

    @Override
    public void onDisable() {
        if (hasPrevious) {
            Minecraft.getInstance().options.particles().set(previous);
            hasPrevious = false;
        }
    }

    public boolean shouldHideParticle(ParticleOptions options) {
        if (!enabled() || !hideBlockBreakParticles.value() || options == null) {
            return false;
        }
        return options instanceof net.minecraft.core.particles.BlockParticleOption
                || options.getType() == ParticleTypes.BLOCK
                || options.getType() == ParticleTypes.BLOCK_CRUMBLE
                || options.getType() == ParticleTypes.BLOCK_MARKER;
    }

    @Override
    public void tick(Minecraft client) {
        if (hasPrevious) {
            apply();
        }
    }

    private void apply() {
        ParticleStatus status = "Decreased".equalsIgnoreCase(level.value())
                ? ParticleStatus.DECREASED
                : ParticleStatus.MINIMAL;
        Minecraft.getInstance().options.particles().set(status);
    }
}
