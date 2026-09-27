package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Decouples the camera from the player body, so the view can turn while the body stays
 * facing the way it was already walking. Purely client side rendering, the server only
 * ever sees the player where they were standing.
 */
public final class FreeLookModule extends Module {
    public static final String THIRD_BACK = "Third person back";
    public static final String THIRD_FRONT = "Third person front";
    public static final String KEEP_MINE = "Keep my perspective";

    public final ModuleSetting<Double> sensitivity;
    public final ModuleSetting<Boolean> verticalLock;
    public final ModuleSetting<Boolean> snapOnDisable;
    public final ModuleSetting<String> perspective;
    public final ModuleSetting<Boolean> noclip;

    private float yaw;
    private float pitch;

    private CameraType previousCameraType;
    private CameraType appliedCameraType;
    private boolean swappedCamera;

    public FreeLookModule() {
        super("freelook", "Free Look", "Look around without turning your body.", ModuleCategory.MOVEMENT, false, true, false);
        sensitivity = addSetting(ModuleSetting.doubleSetting("sensitivity", "Sensitivity",
                "Multiplier applied to your mouse movement.", 1.0D, 0.1D, 3.0D, 0.1D));
        verticalLock = addSetting(ModuleSetting.booleanSetting("vertical-lock", "Lock vertical",
                "Keep the pitch steady while looking around.", false));
        perspective = addSetting(ModuleSetting.modeSetting("perspective", "Perspective",
                "Which view to use while Free Look is on.", THIRD_BACK, THIRD_BACK, THIRD_FRONT, KEEP_MINE));
        snapOnDisable = addSetting(ModuleSetting.booleanSetting("snap-on-disable", "Snap on disable",
                "Turn your body to face where you were looking when you switch it off.", false));
        noclip = addSetting(ModuleSetting.booleanSetting("noclip", "Camera through walls",
                "Stop the camera from being pulled in when a wall is behind you.", true));
    }

    @Override
    public void onEnable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            setEnabled(false);
            return;
        }
        yaw = player.getYRot();
        pitch = player.getXRot();
        applyPerspective();
    }

    @Override
    public void onDisable() {
        restorePerspective();

        if (!snapOnDisable.value()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
    }

    /**
     * Free Look is far easier to read from outside the body, so the view is switched for
     * you. The perspective you had before is remembered and put back afterwards.
     */
    private void applyPerspective() {
        swappedCamera = false;
        String wanted = perspective.value();
        if (KEEP_MINE.equals(wanted)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) {
            return;
        }
        CameraType target = THIRD_FRONT.equals(wanted) ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;
        CameraType current = client.options.getCameraType();
        if (current == target) {
            return;
        }
        previousCameraType = current;
        appliedCameraType = target;
        swappedCamera = true;
        client.options.setCameraType(target);
    }

    private void restorePerspective() {
        if (!swappedCamera || appliedCameraType == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        // Only undo it when the view is still exactly the one we set, so a manual F5 press wins.
        if (client.options != null && previousCameraType != null
                && client.options.getCameraType() == appliedCameraType) {
            client.options.setCameraType(previousCameraType);
        }
        swappedCamera = false;
        appliedCameraType = null;
    }

    /**
     * Takes over the mouse rotation.
     *
     * @return true when the body rotation was consumed and must not be applied
     */
    public boolean handleLook(double yawDelta, double pitchDelta) {
        if (!enabled()) {
            return false;
        }
        float multiplier = sensitivity.value().floatValue();
        yaw += (float) (yawDelta * multiplier);
        if (!verticalLock.value()) {
            pitch = Mth.clamp(pitch + (float) (pitchDelta * multiplier), -90.0F, 90.0F);
        }
        return true;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
