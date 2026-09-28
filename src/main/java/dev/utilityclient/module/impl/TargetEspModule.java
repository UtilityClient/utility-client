package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.EspMarker;
import dev.utilityclient.util.EspProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks only the player Target Lock has chosen, including when they are behind a wall.
 *
 * <p>The counterpart to Player ESP. Player ESP scans the world and marks everyone in range;
 * this one marks one entity, and that entity is whatever Target Lock currently holds. If you
 * have nothing locked, it draws nothing at all.
 *
 * <p>It deliberately does not choose its own target. Two modules each deciding independently
 * who the target is would eventually disagree, and the bug that comes from that is very hard
 * to see. Reading Target Lock's choice means there is exactly one answer at any moment.
 *
 * <p><b>Read this before using it.</b> "Through walls" is an x-ray and it is the most reliably
 * punished thing a Minecraft client does. It is here for private servers with friends. Turn
 * that one setting off and this becomes an ordinary marker on the person you are already
 * fighting, which is a convenience rather than a cheat.
 *
 * <p>Nothing is sent to the server. It reads the entity the client already has, projects it,
 * and draws.
 */
public final class TargetEspModule extends Module implements EspProjection.Settings {
    public final ModuleSetting<String> boxStyle;
    public final ModuleSetting<Integer> lineThickness;
    public final ModuleSetting<Integer> boxAlpha;
    public final ModuleSetting<Integer> cornerLength;
    public final ModuleSetting<Boolean> showTracers;
    public final ModuleSetting<Boolean> showName;
    public final ModuleSetting<Boolean> showDistance;
    public final ModuleSetting<Boolean> showThroughWalls;
    public final ModuleSetting<Boolean> keepWhenOutOfView;
    public final ModuleSetting<Integer> color;
    public final ModuleSetting<Boolean> showStatus;

    private final List<EspMarker> markers = new ArrayList<>();
    private boolean warned;

    public TargetEspModule() {
        super("target-esp", "Target ESP",
                "Marks only the player Target Lock is holding, including through walls.",
                ModuleCategory.VISUAL, false, true, false);

        boxStyle = addSetting(ModuleSetting.modeSetting("box-style", "Style",
                "How the marker is drawn around the target.", "Corners", "Corners", "Box", "Both"));
        lineThickness = addSetting(ModuleSetting.integerSetting("thickness", "Line thickness",
                "How thick the marker lines are, in pixels.", 2, 1, 4, 1));
        cornerLength = addSetting(ModuleSetting.integerSetting("corner-length", "Corner length",
                "For the corners style, how long each corner arm is, in pixels.", 8, 1, 30, 1));
        boxAlpha = addSetting(ModuleSetting.integerSetting("alpha", "Box alpha",
                "How transparent the filled box is, 0 to 255. 0 hides the fill.", 24, 0, 200, 5));
        showTracers = addSetting(ModuleSetting.booleanSetting("tracers", "Tracers",
                "Draw a line from the bottom of the screen to the target.", false));
        showName = addSetting(ModuleSetting.booleanSetting("show-name", "Show name",
                "Print the target's name above the marker.", true));
        showDistance = addSetting(ModuleSetting.booleanSetting("show-distance", "Show distance",
                "Print how far away the target is, in blocks.", true));
        showThroughWalls = addSetting(ModuleSetting.booleanSetting("through-walls", "Through walls",
                "Keep marking the target when you cannot see them. This is the x-ray, and it is "
                        + "on by default because that is the point of the module.", true));
        keepWhenOutOfView = addSetting(ModuleSetting.booleanSetting("keep-when-hidden", "Keep when out of sight",
                "Keep drawing the marker when the target is not on screen at all. Target Lock "
                        + "already drops anyone you cannot see, so this rarely matters.", true));
        color = addSetting(ModuleSetting.colorSetting("color", "Colour",
                "The marker colour.", 0xFF5577));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a one line warning the first time you switch this on.", true));
    }

    @Override
    public void onEnable() {
        if (warned) {
            return;
        }
        warned = true;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && showStatus.value()) {
            client.player.sendSystemMessage(Component.literal(
                    "[Target ESP] On. With \"Through walls\" enabled this is an x-ray, and it is "
                            + "the most commonly banned thing a Minecraft client does. Only use "
                            + "it on servers you control."));
        }
    }

    /* ---------------------------------------------------------------- collect */

    /** Rebuilds the marker list from whatever Target Lock currently holds. */
    public List<EspMarker> collect(Minecraft client) {
        markers.clear();
        if (client.player == null || client.level == null) {
            return markers;
        }

        TargetLockModule lock = (TargetLockModule) dev.utilityclient.module.ModuleManager.get()
                .find("target-lock");
        if (lock == null) {
            return markers;
        }
        Entity target = lock.targetEntity();
        if (target == null || !target.isAlive()) {
            return markers;
        }

        EspProjection.Frame frame = EspProjection.Frame.capture(client);
        if (frame == null) {
            return markers;
        }

        boolean visible = EspProjection.visibleTo(client, target);
        if (!visible && !showThroughWalls.value()) {
            return markers;
        }
        if (!visible && !keepWhenOutOfView.value()) {
            return markers;
        }

        int argb = color.colorValue() | 0xFF000000;
        String label = EspProjection.label(client, target,
                showName.value(), showDistance.value());
        EspMarker marker = EspProjection.project(frame, target, argb, label);
        if (marker != null) {
            markers.add(marker);
        }
        return markers;
    }

    /* ---------------------------------------------------------------- painter */

    @Override
    public String style() {
        return boxStyle.value();
    }

    @Override
    public int thickness() {
        return lineThickness.value();
    }

    @Override
    public int cornerSize() {
        return cornerLength.value();
    }

    @Override
    public int fillAlpha() {
        return boxAlpha.value();
    }

    @Override
    public boolean tracers() {
        return showTracers.value();
    }

    /* ---------------------------------------------------------------- read */

    /** What the HUD should show, which is nothing at all when there is no target. */
    public String statusLabel() {
        TargetLockModule lock = (TargetLockModule) dev.utilityclient.module.ModuleManager.get()
                .find("target-lock");
        if (lock == null || !lock.hasTarget()) {
            return "no target";
        }
        return EspProjection.nameOf(lock.targetEntity());
    }
}
