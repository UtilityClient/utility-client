package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;

public final class PlayerRadarModule extends Module {
    public final ModuleSetting<Integer> radius;
    public final ModuleSetting<Integer> size;
    public final ModuleSetting<Integer> dotSize;
    public final ModuleSetting<Integer> color;
    public final ModuleSetting<Integer> selfColor;
    public final ModuleSetting<Boolean> showNames;
    public final ModuleSetting<Boolean> showDistance;
    public final ModuleSetting<Boolean> rotateWithPlayer;
    public final ModuleSetting<Boolean> showBorders;
    public final ModuleSetting<Boolean> hideSpectators;
    public final ModuleSetting<Boolean> verticalInfo;

    public PlayerRadarModule() {
        super("player-radar", "Player Radar", "Shows nearby players around you on a small radar.", ModuleCategory.MISC, false, true, false);
        radius = addSetting(ModuleSetting.integerSetting("radius", "Radius", "How many blocks the radar covers.", 64, 8, 256, 8));
        size = addSetting(ModuleSetting.integerSetting("size", "Radar size", "Diameter of the radar in pixels.", 90, 40, 200, 5));
        dotSize = addSetting(ModuleSetting.integerSetting("dot-size", "Dot size", "Size of each player dot.", 3, 1, 8, 1));
        color = addSetting(ModuleSetting.colorSetting("color", "Player color", "Colour of other player dots.", 0x63E6FF));
        selfColor = addSetting(ModuleSetting.colorSetting("self-color", "Self color", "Colour of your own dot.", 0x42E88A));
        showNames = addSetting(ModuleSetting.booleanSetting("show-names", "Show names", "Write the name next to each dot.", false));
        showDistance = addSetting(ModuleSetting.booleanSetting("show-distance", "Show distance", "Write the distance in blocks next to each dot.", true));
        rotateWithPlayer = addSetting(ModuleSetting.booleanSetting("rotate", "Rotate with player", "Turn the radar with your camera.", true));
        showBorders = addSetting(ModuleSetting.booleanSetting("borders", "Show borders", "Draw the radar frame and grid lines.", true));
        hideSpectators = addSetting(ModuleSetting.booleanSetting("hide-spectators", "Hide spectators", "Do not show spectator players.", true));
        verticalInfo = addSetting(ModuleSetting.booleanSetting("vertical", "Show height difference",
                "Colour dots by whether players are above or below you.", true));
    }
}
