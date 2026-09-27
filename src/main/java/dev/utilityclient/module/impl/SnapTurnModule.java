package dev.utilityclient.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class SnapTurnModule extends Module {
    public static final String LOOK_UP_DOWN = "Look up / down";
    public static final String SNAP_TURN = "Snap turn";
    public static final String TURN_AROUND = "Turn around";
    public static final String IGNORE = "Ignore";

    public final ModuleSetting<Integer> angle;
    public final ModuleSetting<String> upDown;
    public final ModuleSetting<Integer> verticalAngle;
    public final ModuleSetting<Boolean> invert;
    public final ModuleSetting<Boolean> levelPitch;

    private boolean left;
    private boolean right;
    private boolean up;
    private boolean down;

    public SnapTurnModule() {
        super("snap-turn", "Snap Turn",
                "Arrow keys snap the camera to exact angles, both turning and looking up or down.",
                ModuleCategory.MOVEMENT, false, true, false);
        angle = addSetting(ModuleSetting.integerSetting("angle", "Turn angle",
                "Degrees for the left and right turn snaps.", 90, 45, 180, 45));
        upDown = addSetting(ModuleSetting.modeSetting("up-down", "Up / Down keys",
                "What the up and down arrows do.", LOOK_UP_DOWN, LOOK_UP_DOWN, SNAP_TURN, TURN_AROUND, IGNORE));
        verticalAngle = addSetting(ModuleSetting.integerSetting("vertical-angle", "Look angle",
                "Degrees for each up and down look step.", 45, 15, 90, 15));
        invert = addSetting(ModuleSetting.booleanSetting("invert", "Invert turn", "Swap left and right snaps.", false));
        levelPitch = addSetting(ModuleSetting.booleanSetting("level-pitch", "Level pitch",
                "Look straight ahead after every turn.", false));
    }

    @Override
    public void onDisable() {
        left = right = up = down = false;
    }

    @Override
    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gui.screen() != null) {
            left = right = up = down = false;
            return;
        }

        Window window = client.getWindow();
        boolean nowLeft = isDown(window, GLFW.GLFW_KEY_LEFT);
        boolean nowRight = isDown(window, GLFW.GLFW_KEY_RIGHT);
        boolean nowUp = isDown(window, GLFW.GLFW_KEY_UP);
        boolean nowDown = isDown(window, GLFW.GLFW_KEY_DOWN);

        float step = angle.value();
        String mode = upDown.value();
        boolean turned = false;
        boolean looked = false;
        float yawDelta = 0.0F;
        float pitchDelta = 0.0F;

        // left and right always turn horizontally
        if (nowLeft && !left) {
            yawDelta -= step;
        }
        if (nowRight && !right) {
            yawDelta += step;
        }

        if (LOOK_UP_DOWN.equals(mode)) {
            float vertical = verticalAngle.value();
            if (nowUp && !up) {
                pitchDelta -= vertical;
            }
            if (nowDown && !down) {
                pitchDelta += vertical;
            }
        } else if (TURN_AROUND.equals(mode)) {
            if ((nowUp && !up) || (nowDown && !down)) {
                yawDelta += 180.0F;
            }
        } else if (SNAP_TURN.equals(mode)) {
            if (nowUp && !up) {
                yawDelta -= step;
            }
            if (nowDown && !down) {
                yawDelta += step;
            }
        }

        left = nowLeft;
        right = nowRight;
        up = nowUp;
        down = nowDown;

        if (invert.value()) {
            yawDelta = -yawDelta;
        }

        if (yawDelta != 0.0F) {
            turned = true;
            float yaw = snap(player.getYRot(), yawDelta, step);
            player.setYRot(yaw);
            player.yRotO = yaw;
        }

        if (pitchDelta != 0.0F) {
            looked = true;
            float vertical = verticalAngle.value();
            float pitch = Mth.clamp(snap(player.getXRot(), pitchDelta, vertical), -90.0F, 90.0F);
            player.setXRot(pitch);
            player.xRotO = pitch;
        }

        // only level the pitch after a turn, never after a deliberate look up or down
        if (turned && !looked && levelPitch.value()) {
            player.setXRot(0.0F);
            player.xRotO = 0.0F;
        }
    }

    /**
     * Rounds the current angle to the closest exact multiple of the step, then moves one full
     * step, so the result always lands on a round angle.
     */
    public static float snap(float currentAngle, float delta, float step) {
        float base = Math.round(currentAngle / step) * step;
        return Mth.wrapDegrees(base + delta);
    }

    private boolean isDown(Window window, int key) {
        return window != null && InputConstants.isKeyDown(window, key);
    }
}
