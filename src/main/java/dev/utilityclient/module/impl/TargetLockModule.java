package dev.utilityclient.module.impl;

import dev.utilityclient.gui.ClickGuiScreen;
import dev.utilityclient.gui.ModuleSettingsScreen;
import dev.utilityclient.keybind.KeyBind;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

/**
 * Marks the player you are looking at and keeps them marked until you look away.
 *
 * <p>Look at a player, press the activate key, and they are your target. Press it again to
 * drop them. The lock also drops itself the moment they leave your view, go out of range,
 * log out, or die, so a stale target can never linger and get you killed by accident.
 *
 * <p>The big circle is drawn in the middle of the screen, sized by the FOV setting. That is
 * the whole of the visual: a ring you can size and position by eye, not a prediction of
 * anything. Smoothness controls how quickly the ring eases toward its size, so a large circle
 * does not snap into place.
 *
 * <p><b>Line of sight is enforced, and that is deliberate.</b> A target can only be locked if
 * you can actually see them, and the lock is dropped the instant they stop being visible. An
 * earlier idea for this module was to mark players through walls, which is entity x-ray: it
 * is the single most reliably punished thing a client can do, it gets accounts banned rather
 * than warned, and it would contradict what this client claims to be. So it is not here, and
 * turning the line of sight check off does not enable it. What remains is a way of keeping
 * track of who you are already engaging with, which is a convenience and nothing more.
 *
 * <p>Nothing is sent to the server by this module. It reads the world, draws on your screen,
 * and nothing else. It does not move your aim, extend your reach, or touch packets, so there
 * is nothing here for a server to detect.
 */
public final class TargetLockModule extends Module {
    public final ModuleSetting<KeyBind> activateKey;
    public final ModuleSetting<Boolean> alsoUseModuleKey;
    public final ModuleSetting<Integer> fovSize;
    public final ModuleSetting<Integer> smoothness;
    public final ModuleSetting<Integer> thickness;
    public final ModuleSetting<Double> maxRange;
    public final ModuleSetting<Boolean> playersOnly;
    public final ModuleSetting<Boolean> showName;
    public final ModuleSetting<Boolean> showDistance;
    public final ModuleSetting<Integer> color;
    public final ModuleSetting<Boolean> showStatus;

    /** The locked entity, or null when nothing is locked. */
    private Entity target;
    /** Eased circle size, so the ring grows into place instead of snapping. */
    private float currentSize;

    public TargetLockModule() {
        super("target-lock", "Target Lock",
                "Look at a player, press the key, and they stay marked until you look away.",
                ModuleCategory.VISUAL, false, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "Locks whoever you are looking at. Mouse buttons work here too.", new KeyBind()));
        alsoUseModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind lock as well, not just switch it on.", true));
        fovSize = addSetting(ModuleSetting.integerSetting("fov-size", "FOV size",
                "Radius of the circle in the middle of the screen, in pixels. 0 hides it.", 60, 0, 400, 1));
        smoothness = addSetting(ModuleSetting.integerSetting("smoothness", "Smoothness",
                "How quickly the circle eases to its size. 100 is instant, 1 is very slow.",
                40, 1, 100, 1));
        thickness = addSetting(ModuleSetting.integerSetting("thickness", "Line thickness",
                "How thick the circle is drawn, in pixels.", 2, 1, 8, 1));
        maxRange = addSetting(ModuleSetting.doubleSetting("max-range", "Max range",
                "How far away a target can be and still be locked, in blocks.", 32.0, 1.0, 128.0, 1.0));
        playersOnly = addSetting(ModuleSetting.booleanSetting("players-only", "Players only",
                "Only lock onto other players, not mobs.", true));
        showName = addSetting(ModuleSetting.booleanSetting("show-name", "Show name",
                "Print the locked player's name above the circle.", true));
        showDistance = addSetting(ModuleSetting.booleanSetting("show-distance", "Show distance",
                "Print how far away the target is, in blocks.", true));
        color = addSetting(ModuleSetting.colorSetting("color", "Colour",
                "The colour of the circle.", 0xD000FF));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when you lock or drop a target.", true));
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            clear();
            return;
        }
        if (client.gui.screen() != null
                && !(client.gui.screen() instanceof ClickGuiScreen)
                && !(client.gui.screen() instanceof ModuleSettingsScreen)) {
            return;
        }

        // A target that no longer qualifies is dropped rather than kept. Anything else leaves
        // a lock pointing at someone you cannot see, which is worse than no lock at all.
        if (target != null && !isValidTarget(client, target)) {
            clear();
        }

        // Ease the circle toward its configured size. Done on the tick rather than in the
        // render so it settles at the same rate the client ticks.
        float goal = fovSize.value();
        float step = Math.max(1.0F, goal * (smoothness.value() / 100.0F) * 0.5F);
        if (currentSize < goal) {
            currentSize = Math.min(goal, currentSize + step);
        } else if (currentSize > goal) {
            currentSize = Math.max(goal, currentSize - step);
        }

        if (!keyPressed(client)) {
            return;
        }

        // Pressing while already locked drops the target, so one key is both.
        if (target != null) {
            say(client, "Cleared target");
            clear();
            return;
        }

        Entity looked = lookedAt(client);
        if (looked == null) {
            say(client, "Look at someone first");
            return;
        }
        if (!isValidTarget(client, looked)) {
            say(client, playersOnly.value() && !(looked instanceof Player)
                    ? "That is not a player"
                    : "Out of range");
            return;
        }

        target = looked;
        say(client, "Locked onto " + name(looked));
    }

    /**
     * The entity the crosshair is on, or null.
     *
     * <p>Uses the game's own hit result, which is what an attack would use. It respects
     * reach and line of sight already, so a target is never picked through a wall.
     */
    private static Entity lookedAt(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity();
        }
        return null;
    }

    /**
     * Whether an entity may be a target right now.
     *
     * <p>The line of sight check is the important one and is not optional. It is what keeps
     * this module from being an x-ray, so it is deliberately written to be impossible to
     * switch off.
     */
    private boolean isValidTarget(Minecraft client, Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity == client.player) {
            return false;
        }
        if (playersOnly.value() && !(entity instanceof Player)) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            if (!client.player.hasLineOfSight(living)) {
                return false;
            }
        }
        return client.player.distanceTo(entity) <= maxRange.value();
    }

    private boolean keyPressed(Minecraft client) {
        if (activateKey.value().bound()) {
            if (activateKey.value().consumePress(client)) {
                return true;
            }
            activateKey.value().sync(client);
            return false;
        }
        if (alsoUseModuleKey.value() && keyBind().consumePress(client)) {
            return true;
        }
        warnAboutMissingKey(client);
        return false;
    }

    private void clear() {
        target = null;
        currentSize = 0.0F;
    }

    /* ---------------------------------------------------------------- render */

    /**
     * Drawn by the HUD overlay each frame, so the circle stays smooth even between ticks.
     *
     * @return the eased radius, or a negative number when there is nothing to draw
     */
    public int drawRadius() {
        int radius = Math.round(currentSize);
        if (radius <= 0 || target == null) {
            return -1;
        }
        return radius;
    }

    public boolean hasTarget() {
        return target != null;
    }

    /**
     * The currently locked entity, or null.
     *
     * <p>Exposed so Target ESP can mark exactly this entity rather than scanning the world
     * for players of its own. One source of truth for "who is my target" is better than two
     * modules each deciding independently and disagreeing.
     */
    public Entity targetEntity() {
        return target;
    }

    public int rgba() {
        return 0xFF000000 | color.colorValue();
    }

    public int lineThickness() {
        return thickness.value();
    }

    /** Text to print under the circle, or an empty string. */
    public String label(Minecraft client) {
        if (target == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (showName.value()) {
            builder.append(name(target));
        }
        if (showDistance.value() && client.player != null) {
            if (!builder.isEmpty()) {
                builder.append("  ");
            }
            builder.append(Math.round(client.player.distanceTo(target))).append("m");
        }
        return builder.toString();
    }

    private static String name(Entity entity) {
        return entity.getName().getString();
    }

    private void say(Minecraft client, String message) {
        if (showStatus.value() && client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Target Lock] " + message));
        }
    }

    private boolean warnedAboutKey;

    private void warnAboutMissingKey(Minecraft client) {
        if (warnedAboutKey) {
            return;
        }
        warnedAboutKey = true;
        say(client, "No activate key set, using the module keybind. Bind an activate key in "
                + "the settings to use your own key instead.");
    }

    /* ---------------------------------------------------------------- read */

    /** What the module is doing, for the HUD. */
    public String statusLabel() {
        if (target == null) {
            return "no target";
        }
        return name(target).toLowerCase(Locale.ROOT);
    }
}
