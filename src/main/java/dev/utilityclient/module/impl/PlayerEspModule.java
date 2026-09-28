package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.EspMarker;
import dev.utilityclient.util.EspProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks every player in range, including ones the server is deliberately not showing you.
 *
 * <p>This is the all-players module. If you only want a marker on whoever you have locked on,
 * that is Target ESP, which reads Target Lock's choice instead of scanning the world.
 *
 * <p><b>Read this before using it.</b> "Through walls" is an x-ray, and it is the most reliably
 * punished thing a Minecraft client can do. It is not a cosmetic overlay that happens to be
 * slightly unfair: on any server with a competent anticheat, seeing a player through a wall is
 * indistinguishable from a player tracker, and the sanction is a ban rather than a warning.
 * Turning that one setting off leaves a perfectly ordinary overlay that marks only players you
 * are already looking at, which is often what people actually want.
 *
 * <p>Nothing is sent to the server. The entity list the server already sent is read, positions
 * are projected, and the result is drawn on screen. There is no packet here, so there is no
 * packet to inspect. What gives it away is entirely what you do with what you see.
 */
public final class PlayerEspModule extends Module implements EspProjection.Settings {
    public final ModuleSetting<String> boxStyle;
    public final ModuleSetting<Integer> lineThickness;
    public final ModuleSetting<Integer> boxAlpha;
    public final ModuleSetting<Integer> cornerLength;
    public final ModuleSetting<Double> maxRange;
    public final ModuleSetting<Boolean> showTracers;
    public final ModuleSetting<Boolean> showName;
    public final ModuleSetting<Boolean> showDistance;
    public final ModuleSetting<Boolean> showThroughWalls;
    public final ModuleSetting<Boolean> includeSelf;
    public final ModuleSetting<Boolean> mobsToo;
    public final ModuleSetting<Integer> playerColor;
    public final ModuleSetting<Integer> mobColor;

    private final List<EspMarker> markers = new ArrayList<>();
    private boolean warned;

    public PlayerEspModule() {
        super("player-esp", "Player ESP",
                "Marks every player in range, including ones behind walls. The through walls part "
                        + "is an x-ray and gets accounts banned on public servers.",
                ModuleCategory.VISUAL, false, true, false);

        boxStyle = addSetting(ModuleSetting.modeSetting("box-style", "Style",
                "How the marker is drawn around each player.", "Corners", "Corners", "Box", "Both"));
        lineThickness = addSetting(ModuleSetting.integerSetting("thickness", "Line thickness",
                "How thick the marker lines are, in pixels.", 1, 1, 4, 1));
        cornerLength = addSetting(ModuleSetting.integerSetting("corner-length", "Corner length",
                "For the corners style, how long each corner arm is, in pixels.", 6, 1, 30, 1));
        boxAlpha = addSetting(ModuleSetting.integerSetting("alpha", "Box alpha",
                "How transparent the filled box is, 0 to 255. 0 hides the fill.", 0, 0, 200, 5));
        maxRange = addSetting(ModuleSetting.doubleSetting("max-range", "Max range",
                "How far away a player can be and still be marked, in blocks.", 64.0, 1.0, 256.0, 1.0));
        showTracers = addSetting(ModuleSetting.booleanSetting("tracers", "Tracers",
                "Draw a line from the bottom of the screen to each marked player.", false));
        showName = addSetting(ModuleSetting.booleanSetting("show-name", "Show name",
                "Print the player's name above the marker.", true));
        showDistance = addSetting(ModuleSetting.booleanSetting("show-distance", "Show distance",
                "Print how far away the player is, in blocks.", true));
        showThroughWalls = addSetting(ModuleSetting.booleanSetting("through-walls", "Through walls",
                "Mark players you cannot currently see. This is the x-ray, and it is on by "
                        + "default because that is the point of the module. Turning it off leaves "
                        + "only players you are already looking at.", true));
        includeSelf = addSetting(ModuleSetting.booleanSetting("include-self", "Include self",
                "Also mark your own player model, which is normally hidden.", false));
        mobsToo = addSetting(ModuleSetting.booleanSetting("mobs-too", "Mobs as well",
                "Also mark hostile mobs, not just players.", false));
        playerColor = addSetting(ModuleSetting.colorSetting("player-color", "Player colour",
                "The marker colour for players.", 0xFF5577));
        mobColor = addSetting(ModuleSetting.colorSetting("mob-color", "Mob colour",
                "The marker colour for mobs.", 0xFF4455));
    }

    @Override
    public void onEnable() {
        // Said once, plainly. A cheat that does not announce itself is worse than no cheat,
        // because the person using it has no idea what they have loaded.
        if (warned) {
            return;
        }
        warned = true;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "[Player ESP] On. With \"Through walls\" enabled this is an x-ray, and it "
                            + "is the most commonly banned thing a Minecraft client does. Only "
                            + "use it on servers you control."));
        }
    }

    /* ---------------------------------------------------------------- collect */

    /** Rebuilds the marker list. Called by the HUD overlay each frame. */
    public List<EspMarker> collect(Minecraft client) {
        markers.clear();
        if (client.player == null || client.level == null) {
            return markers;
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) {
                if (!includeSelf.value()) {
                    continue;
                }
            } else if (!entity.isAlive() || entity.isSpectator()) {
                continue;
            }

            boolean isPlayer = entity instanceof Player;
            if (!isPlayer && (!mobsToo.value() || !(entity instanceof LivingEntity))) {
                continue;
            }
            if (client.player.distanceTo(entity) > maxRange.value()) {
                continue;
            }

            boolean visible = EspProjection.visibleTo(client, entity);
            if (!visible && !showThroughWalls.value()) {
                continue;
            }

            int argb = (isPlayer ? playerColor : mobColor).colorValue() | 0xFF000000;
            String label = EspProjection.label(client, entity,
                    showName.value(), showDistance.value());
            EspMarker marker = EspProjection.project(client, entity, argb, label);
            if (marker != null) {
                markers.add(marker);
            }
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

    /** How many entities are currently marked, for the HUD. */
    public int count(Minecraft client) {
        return collect(client).size();
    }
}
