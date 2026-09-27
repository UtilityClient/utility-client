package dev.utilityclient.module;

import dev.utilityclient.config.ConfigManager;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.AntiLeakerModule;
import dev.utilityclient.module.impl.AutoTradeModule;
import dev.utilityclient.module.impl.AutoWalkModule;
import dev.utilityclient.module.impl.BlockHighlightModule;
import dev.utilityclient.module.impl.ClickHolderModule;
import dev.utilityclient.module.impl.CrosshairModule;
import dev.utilityclient.module.impl.CustomBreakAnimationModule;
import dev.utilityclient.module.impl.FullbrightModule;
import dev.utilityclient.module.impl.FreeCamModule;
import dev.utilityclient.module.impl.FreeLookModule;
import dev.utilityclient.module.impl.HudModule;
import dev.utilityclient.module.impl.NoDroppedItemsModule;
import dev.utilityclient.module.impl.NoParticlesModule;
import dev.utilityclient.module.impl.PlayerRadarModule;
import dev.utilityclient.module.impl.SnapTurnModule;
import dev.utilityclient.module.impl.ZoomModule;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private static ModuleManager instance;

    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
    }

    public static void init() {
        if (instance != null) {
            return;
        }

        instance = new ModuleManager();
        instance.register(new HudModule());
        instance.register(new PlayerRadarModule());
        instance.register(new CrosshairModule());
        instance.register(new CustomBreakAnimationModule());
        instance.register(new BlockHighlightModule());
        instance.register(new FullbrightModule());
        instance.register(new NoParticlesModule());
        instance.register(new NoDroppedItemsModule());
        instance.register(new SnapTurnModule());
        instance.register(new AutoWalkModule());
        instance.register(new ClickHolderModule());
        instance.register(new AutoTradeModule());
        instance.register(new AntiLeakerModule());
        instance.register(new FreeLookModule());
        instance.register(new FreeCamModule());
        instance.register(new ZoomModule());
    }

    public static ModuleManager get() {
        if (instance == null) {
            init();
        }
        return instance;
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> modules() {
        return Collections.unmodifiableList(modules);
    }

    public Module find(String id) {
        for (Module module : modules) {
            if (module.id().equalsIgnoreCase(id)) {
                return module;
            }
        }
        return null;
    }

    public boolean isEnabled(String id) {
        Module module = find(id);
        return module != null && module.enabled();
    }

    public void tick(Minecraft client) {
        boolean menuOpen = client.gui.screen() != null;
        boolean toggled = false;
        for (Module module : modules) {
            toggled |= module.pollKeyBind(client, !menuOpen);
            if (module.enabled()) {
                module.tick(client);
            }
            if (module instanceof HudModule hud) {
                hud.tickMoveMode(client);
            }
        }
        if (toggled) {
            ConfigManager.save();
        }
    }
}
