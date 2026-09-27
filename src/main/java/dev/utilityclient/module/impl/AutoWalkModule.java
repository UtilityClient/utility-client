package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.world.entity.player.Input;

/**
 * Keeps a movement key held down for you. Purely an input convenience: the same movement
 * packets the game already sends are sent, just without holding the key.
 */
public final class AutoWalkModule extends Module {
    public final ModuleSetting<String> direction;
    public final ModuleSetting<Boolean> sprint;

    public AutoWalkModule() {
        super("auto-walk", "Auto Walk", "Keeps you walking in a direction without holding the key.", ModuleCategory.MOVEMENT, false, false, false);
        direction = addSetting(ModuleSetting.modeSetting("direction", "Direction", "Which way to keep walking.",
                "Straight", "Straight", "Left", "Right", "Backward"));
        sprint = addSetting(ModuleSetting.booleanSetting("sprint", "Always sprint", "Sprint while auto walking.", false));
    }

    /**
     * Adds the held direction on top of whatever the player is pressing.
     */
    public Input apply(Input input) {
        if (input == null) {
            input = Input.EMPTY;
        }
        String dir = direction.value();
        boolean forward = input.forward();
        boolean backward = input.backward();
        boolean left = input.left();
        boolean right = input.right();

        switch (dir) {
            case "Left" -> left = true;
            case "Right" -> right = true;
            case "Backward" -> backward = true;
            default -> forward = true;
        }

        return new Input(forward, backward, left, right, input.jump(), input.shift(),
                sprint.value() || input.sprint());
    }
}
