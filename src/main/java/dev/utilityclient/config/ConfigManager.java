package dev.utilityclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.ModuleSetting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("utility-client.json");

    private ConfigManager() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data == null || data.modules == null) {
                return;
            }

            ModuleManager manager = ModuleManager.get();
            for (Module module : manager.modules()) {
                Map<String, Object> savedSettings = data.settings == null ? null : data.settings.get(module.id());
                if (savedSettings != null) {
                    for (ModuleSetting<?> setting : module.settings()) {
                        setting.loadValue(savedSettings.get(setting.id()));
                    }
                }
                if (data.keybinds != null) {
                    module.keyBind().load(data.keybinds.get(module.id()));
                }
                Boolean enabled = data.modules.get(module.id());
                if (enabled != null) {
                    module.setEnabled(enabled);
                }
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[Utility Client] Could not load configuration: " + exception.getMessage());
        }
    }

    public static Path path() {
        return CONFIG_PATH;
    }

    public static void reset() {
        for (Module module : ModuleManager.get().modules()) {
            module.resetToDefaults();
        }
        save();
    }

    public static void save() {
        ConfigData data = new ConfigData();
        data.modules = new LinkedHashMap<>();
        data.settings = new LinkedHashMap<>();
        data.keybinds = new LinkedHashMap<>();
        for (Module module : ModuleManager.get().modules()) {
            data.modules.put(module.id(), module.enabled());
            data.keybinds.put(module.id(), module.keyBind().save());
            Map<String, Object> moduleSettings = new LinkedHashMap<>();
            for (ModuleSetting<?> setting : module.settings()) {
                moduleSettings.put(setting.id(), setting.saveValue());
            }
            data.settings.put(module.id(), moduleSettings);
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            System.err.println("[Utility Client] Could not save configuration: " + exception.getMessage());
        }
    }

    private static final class ConfigData {
        private Map<String, Boolean> modules;
        private Map<String, Map<String, Object>> settings;
        private Map<String, String> keybinds;
    }
}
