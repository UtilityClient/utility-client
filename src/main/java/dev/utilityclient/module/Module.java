package dev.utilityclient.module;

import dev.utilityclient.keybind.KeyBind;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String id;
    private final String displayName;
    private final String description;
    private final ModuleCategory category;
    private final boolean defaultEnabled;
    private final boolean hasInfo;
    private final boolean flagged;
    private final List<ModuleSetting<?>> settings = new ArrayList<>();
    private final KeyBind keyBind = new KeyBind();
    private boolean enabled;

    protected Module(String id, String displayName, String description, ModuleCategory category, boolean enabledByDefault) {
        this(id, displayName, description, category, enabledByDefault, false, false);
    }

    /**
     * @param hasInfo shows the small info badge next to the name in the menu
     * @param flagged shows the red diamond, used to mark modules that spend items
     */
    protected Module(String id, String displayName, String description, ModuleCategory category,
                     boolean enabledByDefault, boolean hasInfo, boolean flagged) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.defaultEnabled = enabledByDefault;
        this.enabled = enabledByDefault;
        this.hasInfo = hasInfo;
        this.flagged = flagged;
    }

    /** Whether the menu draws the info badge beside this module's name. */
    public final boolean hasInfo() {
        return hasInfo;
    }

    /** Whether the menu draws the red warning diamond beside this module's name. */
    public final boolean flagged() {
        return flagged;
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final String description() {
        return description;
    }

    public final ModuleCategory category() {
        return category;
    }

    public final KeyBind keyBind() {
        return keyBind;
    }

    /**
     * Toggles the module when its keybind goes down.
     *
     * @param allow true while no menu is open
     * @return true when the module was toggled by the keybind
     */
    public final boolean pollKeyBind(Minecraft client, boolean allow) {
        if (!allow) {
            keyBind.sync(client);
            return false;
        }
        if (keyBind.consumePress(client)) {
            toggle();
            return true;
        }
        return false;
    }

    public final void resetToDefaults() {
        for (ModuleSetting<?> setting : settings) {
            setting.reset();
        }
        keyBind.clear();
        setEnabled(defaultEnabled);
    }

    protected final <T> ModuleSetting<T> addSetting(ModuleSetting<T> setting) {
        settings.add(setting);
        return setting;
    }

    public final List<ModuleSetting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final boolean enabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void tick(Minecraft client) {
    }

    public void onSettingsChanged() {
    }
}
