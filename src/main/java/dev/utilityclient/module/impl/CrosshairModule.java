package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;

import java.util.Locale;

public final class CrosshairModule extends Module {
    public final ModuleSetting<String> style;
    public final ModuleSetting<Integer> size;
    public final ModuleSetting<Integer> thickness;
    public final ModuleSetting<Integer> color;
    public final ModuleSetting<Boolean> centerDot;
    public final ModuleSetting<Boolean> removeVanilla;
    public final ModuleSetting<Boolean> attackIndicator;

    public CrosshairModule() {
        super("crosshair", "Crosshair", "Adds a clean, centered crosshair.", ModuleCategory.VISUAL, false, true, false);
        style = addSetting(ModuleSetting.modeSetting("style", "Model", "Shape of the crosshair.",
                "Plus", "Plus", "Dot", "Circle", "Cross", "T-Shape", "X", "Ring", "Diamond", "Arrow", "None"));
        size = addSetting(ModuleSetting.integerSetting("size", "Size", "Length of the crosshair arms.", 6, 1, 24, 1));
        thickness = addSetting(ModuleSetting.integerSetting("thickness", "Thickness", "Line thickness in pixels.", 1, 1, 5, 1));
        color = addSetting(ModuleSetting.colorSetting("color", "Color", "Crosshair colour.", 0xFFFFFF));
        centerDot = addSetting(ModuleSetting.booleanSetting("center-dot", "Center dot", "Show a dot in the middle.", false));
        removeVanilla = addSetting(ModuleSetting.booleanSetting("remove-vanilla", "Remove vanilla crosshair",
                "Hide the default Minecraft crosshair while this module is on.", true));
        attackIndicator = addSetting(ModuleSetting.booleanSetting("attack-indicator", "Attack indicator",
                "Show a small marker when you are mining or attacking.", true));
    }

    public String styleName() {
        return style.value().toLowerCase(Locale.ROOT);
    }
}
