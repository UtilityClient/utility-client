package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;

public final class ZoomModule extends Module {
    public final ModuleSetting<Integer> amount;
    private int previousFov;
    private boolean hasPreviousFov;

    public ZoomModule() {
        super("zoom", "Zoom", "Narrows the field of view while enabled.", ModuleCategory.VISUAL, false, false, false);
        amount = addSetting(ModuleSetting.integerSetting("amount", "Zoom amount", "How many FOV points to remove.", 20, 5, 50, 5));
    }

    @Override
    public void onEnable() {
        Minecraft client = Minecraft.getInstance();
        previousFov = client.options.fov().get();
        hasPreviousFov = true;
        applyZoom(client, previousFov);
    }

    @Override
    public void onDisable() {
        if (hasPreviousFov) {
            Minecraft.getInstance().options.fov().set(previousFov);
            hasPreviousFov = false;
        }
    }

    @Override
    public void tick(Minecraft client) {
        if (hasPreviousFov) {
            applyZoom(client, previousFov);
        }
    }

    private void applyZoom(Minecraft client, int baseFov) {
        client.options.fov().set(Math.max(20, baseFov - amount.value()));
    }
}
