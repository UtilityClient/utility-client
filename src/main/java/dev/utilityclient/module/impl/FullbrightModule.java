package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;

public final class FullbrightModule extends Module {
    public final ModuleSetting<Double> brightness;
    private double previousGamma;
    private boolean hasPreviousGamma;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Raises the client light level while enabled.", ModuleCategory.VISUAL, false, false, false);
        brightness = addSetting(ModuleSetting.doubleSetting("brightness", "Brightness", "Gamma level while active.", 1.0D, 0.5D, 1.0D, 0.05D));
    }

    @Override
    public void onEnable() {
        Minecraft client = Minecraft.getInstance();
        previousGamma = client.options.gamma().get();
        hasPreviousGamma = true;
        client.options.gamma().set(brightness.value());
    }

    @Override
    public void onDisable() {
        if (hasPreviousGamma) {
            Minecraft.getInstance().options.gamma().set(previousGamma);
            hasPreviousGamma = false;
        }
    }
}
