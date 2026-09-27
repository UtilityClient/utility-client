package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Detaches the camera from the player and flies it around the world.
 * Everything here is client side rendering only: the server still sees the player standing
 * exactly where they were, and no packets are altered.
 */
public final class FreeCamModule extends Module {
    public final ModuleSetting<Double> speed;
    public final ModuleSetting<Boolean> verticalMovement;
    public final ModuleSetting<Boolean> fastWithControl;
    public final ModuleSetting<Boolean> snapOnDisable;

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean placed;

    public FreeCamModule() {
        super("freecam", "Free Cam", "Detach the camera and fly it around freely.", ModuleCategory.MOVEMENT, false, true, false);
        speed = addSetting(ModuleSetting.doubleSetting("speed", "Speed",
                "Blocks per second while flying.", 12.0D, 1.0D, 60.0D, 1.0D));
        verticalMovement = addSetting(ModuleSetting.booleanSetting("vertical", "Vertical movement",
                "Allow space and shift to move up and down.", true));
        fastWithControl = addSetting(ModuleSetting.booleanSetting("fast", "Ctrl to speed up",
                "Hold control while flying to move three times faster.", true));
        snapOnDisable = addSetting(ModuleSetting.booleanSetting("snap-on-disable", "Snap on disable",
                "Turn your body to face where the camera was pointing.", true));
    }

    @Override
    public void onEnable() {
        placed = false;
    }

    @Override
    public void onDisable() {
        if (snapOnDisable.value() && placed) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.setYRot(yaw);
                player.yRotO = yaw;
                player.setXRot(pitch);
                player.xRotO = pitch;
            }
        }
        placed = false;
    }

    /**
     * Takes over the mouse rotation while the camera is detached.
     */
    public boolean handleLook(double yawDelta, double pitchDelta) {
        if (!enabled()) {
            return false;
        }
        yaw += (float) yawDelta;
        pitch = Mth.clamp(pitch + (float) pitchDelta, -90.0F, 90.0F);
        return true;
    }

    /**
     * Moves the detached camera using the movement keys.
     */
    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null) {
            return;
        }

        double current = speed.value();
        if (fastWithControl.value() && isControlDown(client)) {
            current *= 3.0D;
        }
        double step = current / 20.0D;

        double radiansYaw = Math.toRadians(yaw);
        double radiansPitch = Math.toRadians(pitch);
        double forwardX = -Math.sin(radiansYaw) * Math.cos(radiansPitch);
        double forwardY = -Math.sin(radiansPitch);
        double forwardZ = Math.cos(radiansYaw) * Math.cos(radiansPitch);
        double rightX = -Math.cos(radiansYaw);
        double rightZ = -Math.sin(radiansYaw);

        double moveX = 0.0D;
        double moveY = 0.0D;
        double moveZ = 0.0D;

        if (client.options.keyUp.isDown()) {
            moveX += forwardX;
            moveY += forwardY;
            moveZ += forwardZ;
        }
        if (client.options.keyDown.isDown()) {
            moveX -= forwardX;
            moveY -= forwardY;
            moveZ -= forwardZ;
        }
        if (client.options.keyRight.isDown()) {
            moveX += rightX;
            moveZ += rightZ;
        }
        if (client.options.keyLeft.isDown()) {
            moveX -= rightX;
            moveZ -= rightZ;
        }
        if (verticalMovement.value()) {
            if (client.options.keyJump.isDown()) {
                moveY += 1.0D;
            }
            if (client.options.keyShift.isDown()) {
                moveY -= 1.0D;
            }
        }

        double length = Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);
        if (length > 1.0E-6D) {
            double scale = step / length;
            x += moveX * scale;
            y += moveY * scale;
            z += moveZ * scale;
        }
    }

    private boolean isControlDown(Minecraft client) {
        long handle = client.getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /**
     * Reads where the camera currently sits so the detached camera starts from the eyes.
     */
    public void captureStart(Camera camera) {
        if (placed) {
            return;
        }
        Vec3 current = camera.position();
        x = current.x;
        y = current.y;
        z = current.z;
        yaw = camera.yRot();
        pitch = camera.xRot();
        placed = true;
    }

    public double cameraX() {
        return x;
    }

    public double cameraY() {
        return y;
    }

    public double cameraZ() {
        return z;
    }

    public void setLook(float newYaw, float newPitch) {
        this.yaw = newYaw;
        this.pitch = newPitch;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
