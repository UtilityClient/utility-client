package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a marker on players, including ones the server is deliberately not showing you.
 *
 * <p>Read this before using it. This is entity x-ray, and it is the single most reliably
 * punished thing a client can do. It is not a cosmetic overlay that happens to be slightly
 * unfair: on any server with a competent anticheat, seeing a player through a wall is
 * indistinguishable from a player tracker, and the punishment is a ban, not a warning. The
 * module is written to be active on every server because that is what was asked for, so
 * treat it as a cheat that happens to be convenient on a private server with friends and
 * will cost you an account the first time you load it anywhere else.
 *
 * <p>There is no server gating here, and that is a deliberate choice rather than an
 * oversight. Gating it to specific addresses would have been safer and was offered; the
 * answer was that it should work everywhere.
 *
 * <p>Mechanically, nothing is sent to the server. It reads the entity list the server already
 * sent, projects positions onto the screen, and draws. There is no packet here, so there is
 * no packet to inspect. What gives it away is entirely what you do with the information.
 *
 * <p>Positions are projected with the game's own {@code projectPointToScreen}, so the maths
 * is the same the client uses for the waypoint renderer and is correct under any FOV, GUI
 * scale, resolution, or camera angle including the FOV module and Free Look.
 */
public final class TargetEspModule extends Module {
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
    public final ModuleSetting<Integer> visibleColor;
    public final ModuleSetting<Boolean> showStatus;

    /** Screen positions computed during the render pass, in the order they were drawn. */
    private final List<Marker> markers = new ArrayList<>();

    public TargetEspModule() {
        super("target-esp", "Target ESP",
                "Draws a marker on players, including ones behind walls. This is x-ray and "
                        + "will get you banned on any server with a real anticheat.",
                ModuleCategory.VISUAL, false, true, false);

        boxStyle = addSetting(ModuleSetting.modeSetting("box-style", "Style",
                "How the marker is drawn around each player.", "Corners", "Corners", "Box", "Both"));
        lineThickness = addSetting(ModuleSetting.integerSetting("thickness", "Line thickness",
                "How thick the marker lines are, in pixels.", 1, 1, 4, 1));
        cornerLength = addSetting(ModuleSetting.integerSetting("corner-length", "Corner length",
                "For the corners style, how long each corner arm is, in pixels.", 6, 1, 30, 1));
        boxAlpha = addSetting(ModuleSetting.integerSetting("alpha", "Box alpha",
                "How transparent the filled box is, 0 to 255. 0 hides the fill entirely.", 0, 0, 200, 5));
        maxRange = addSetting(ModuleSetting.doubleSetting("max-range", "Max range",
                "How far away a player can be and still be marked, in blocks.", 64.0, 1.0, 256.0, 1.0));
        showTracers = addSetting(ModuleSetting.booleanSetting("tracers", "Tracers",
                "Draw a line from the bottom of the screen to each marked player.", false));
        showName = addSetting(ModuleSetting.booleanSetting("show-name", "Show name",
                "Print the player's name above the marker.", true));
        showDistance = addSetting(ModuleSetting.booleanSetting("show-distance", "Show distance",
                "Print how far away the player is, in blocks.", true));
        showThroughWalls = addSetting(ModuleSetting.booleanSetting("through-walls", "Through walls",
                "Mark players you cannot currently see. This is the part that is x-ray, and it "
                        + "is on by default because that is the point of the module. Turning it "
                        + "off leaves only players you are already looking at.", true));
        includeSelf = addSetting(ModuleSetting.booleanSetting("include-self", "Include self",
                "Also mark your own player model, which is normally hidden.", false));
        mobsToo = addSetting(ModuleSetting.booleanSetting("mobs-too", "Mobs as well",
                "Also mark hostile mobs, not just players.", false));
        playerColor = addSetting(ModuleSetting.colorSetting("player-color", "Player colour",
                "The marker colour for players.", 0xFF5577));
        mobColor = addSetting(ModuleSetting.colorSetting("mob-color", "Mob colour",
                "The marker colour for mobs.", 0xFF4455));
        visibleColor = addSetting(ModuleSetting.colorSetting("visible-colour", "Visible colour",
                "The marker colour for players you can already see, when through walls is off.",
                0x66FF88));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a one line warning the first time you switch this on.", true));
    }

    @Override
    public void onEnable() {
        if (showStatus.value() && !warned) {
            warned = true;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(
                        "[Target ESP] On. This marks players through walls and is the most "
                                + "commonly banned thing a client does. Only use it on servers "
                                + "you control."));
            }
        }
    }

    private boolean warned;

    /* ---------------------------------------------------------------- collect */

    /**
     * Gathers the markers to draw. Called by the HUD overlay each frame.
     *
     * <p>Everything is done in screen space: the entity's bounding box corners are projected
     * with the game's own projector, and the marker is then drawn from those eight points. It
     * is a little more honest than guessing a size from distance, because it stays correct
     * for a player at an angle, crouched, or scaled.
     */
    public List<Marker> collect(Minecraft client) {
        markers.clear();
        if (client.player == null || client.level == null) {
            return markers;
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) {
                if (!includeSelf.value()) {
                    continue;
                }
            } else if (!entity.isAlive()) {
                continue;
            }

            boolean isPlayer = entity instanceof Player;
            if (!isPlayer) {
                if (!mobsToo.value() || !(entity instanceof LivingEntity)) {
                    continue;
                }
            }
            if (entity.isSpectator()) {
                continue;
            }

            double distance = client.player.distanceTo(entity);
            if (distance > maxRange.value()) {
                continue;
            }

            boolean visible = client.player.hasLineOfSight((LivingEntity) entity);
            if (!visible && !showThroughWalls.value()) {
                continue;
            }

            int argb = isPlayer
                    ? (visible && !showThroughWalls.value()
                    ? visibleColor.colorValue() | 0xFF000000
                    : playerColor.colorValue() | 0xFF000000)
                    : mobColor.colorValue() | 0xFF000000;

            Vec3 projected = projectCentre(client, entity);
            if (projected == null) {
                continue;
            }

            float height = entity.getBbHeight();
            float width = entity.getBbWidth();
            double eye = client.player.getEyeY();

            // Project the eight corners of the bounding box. If any of them is unusable the
            // marker would be nonsense, so the whole entity is skipped rather than drawn wrong.
            double left = -width / 2.0;
            double right = width / 2.0;
            double top = entity.getBbHeight();
            double bottom = 0.0;

            Vec3 p0 = project(client, entity.getX() + left, entity.getY() + top, entity.getZ() - width / 2.0);
            Vec3 p1 = project(client, entity.getX() + right, entity.getY() + top, entity.getZ() - width / 2.0);
            Vec3 p2 = project(client, entity.getX() + right, entity.getY() + top, entity.getZ() + width / 2.0);
            Vec3 p3 = project(client, entity.getX() + left, entity.getY() + top, entity.getZ() + width / 2.0);
            Vec3 p4 = project(client, entity.getX() + left, entity.getY() + bottom, entity.getZ() - width / 2.0);
            Vec3 p5 = project(client, entity.getX() + right, entity.getY() + bottom, entity.getZ() - width / 2.0);
            Vec3 p6 = project(client, entity.getX() + right, entity.getY() + bottom, entity.getZ() + width / 2.0);
            Vec3 p7 = project(client, entity.getX() + left, entity.getY() + bottom, entity.getZ() + width / 2.0);
            if (p0 == null || p1 == null || p2 == null || p3 == null
                    || p4 == null || p5 == null || p6 == null || p7 == null) {
                continue;
            }

            int minX = (int) Math.round(minOf(p0.x, p1.x, p2.x, p3.x, p4.x, p5.x, p6.x, p7.x));
            int maxX = (int) Math.round(maxOf(p0.x, p1.x, p2.x, p3.x, p4.x, p5.x, p6.x, p7.x));
            int minY = (int) Math.round(minOf(p0.y, p1.y, p2.y, p3.y, p4.y, p5.y, p6.y, p7.y));
            int maxY = (int) Math.round(maxOf(p0.y, p1.y, p2.y, p3.y, p4.y, p5.y, p6.y, p7.y));

            // A player far off screen still projects, often to absurd coordinates. Clamping
            // the box to the screen stops a marker a few kilometres away painting a line
            // across the whole display.
            int screenW = client.getWindow().getGuiScaledWidth();
            int screenH = client.getWindow().getGuiScaledHeight();
            if (maxX < 0 || minX > screenW || maxY < 0 || minY > screenH) {
                continue;
            }
            minX = Math.max(-64, minX);
            minY = Math.max(-64, minY);
            maxX = Math.min(screenW + 64, maxX);
            maxY = Math.min(screenH + 64, maxY);

            String label = buildLabel(entity, distance, isPlayer);
            markers.add(new Marker(minX, minY, maxX, maxY, argb, label, visible, isPlayer,
                    entity, eye));
        }
        return markers;
    }

    private String buildLabel(Entity entity, double distance, boolean isPlayer) {
        StringBuilder builder = new StringBuilder();
        if (showName.value()) {
            builder.append(entity.getName().getString());
        }
        if (showDistance.value()) {
            if (!builder.isEmpty()) {
                builder.append("  ");
            }
            builder.append(Math.round(distance)).append("m");
        }
        return builder.toString();
    }

    private static double minOf(double... values) {
        double best = Double.MAX_VALUE;
        for (double value : values) {
            best = Math.min(best, value);
        }
        return best;
    }

    private static double maxOf(double... values) {
        double best = -Double.MAX_VALUE;
        for (double value : values) {
            best = Math.max(best, value);
        }
        return best;
    }

    /**
     * Projects a world point to GUI pixels, or null when it cannot be projected.
     *
     * <p>Behind the camera the projection runs the wrong way and produces a mirrored point
     * rather than failing, so the depth has to be checked. That check is what stops markers
     * for players behind you from appearing flipped on the opposite side of the screen.
     */
    private static Vec3 project(Minecraft client, double x, double y, double z) {
        if (client.gameRenderer == null) {
            return null;
        }
        Vec3 result;
        try {
            result = client.gameRenderer.projectPointToScreen(new Vec3(x, y, z));
        } catch (RuntimeException exception) {
            return null;
        }
        if (result == null) {
            return null;
        }
        // The near plane is at 0, so anything at or behind it is not drawable.
        if (result.z <= 0.0D) {
            return null;
        }
        return result;
    }

    private static Vec3 projectCentre(Minecraft client, Entity entity) {
        return project(client, entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
    }

    /* ---------------------------------------------------------------- settings */

    public String styleName() {
        return boxStyle.value();
    }

    public int thickness() {
        return lineThickness.value();
    }

    public int cornerSize() {
        return cornerLength.value();
    }

    public int fillAlpha() {
        return boxAlpha.value();
    }

    public boolean tracers() {
        return showTracers.value();
    }

    /* ---------------------------------------------------------------- read */

    /** One entity's marker, in GUI pixels. */
    public static final class Marker {
        public final int minX;
        public final int minY;
        public final int maxX;
        public final int maxY;
        public final int argb;
        public final String label;
        public final boolean visible;
        public final boolean player;
        public final Entity entity;
        public final double eyeY;

        Marker(int minX, int minY, int maxX, int maxY, int argb, String label,
               boolean visible, boolean player, Entity entity, double eyeY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.argb = argb;
            this.label = label;
            this.visible = visible;
            this.player = player;
            this.entity = entity;
            this.eyeY = eyeY;
        }

        public String name() {
            return entity.getName().getString().toLowerCase(Locale.ROOT);
        }
    }

    /** How many entities would be marked, for the HUD. */
    public int visibleCount(Minecraft client) {
        return collect(client).size();
    }

    /** The server this session is on, or "singleplayer" / "unknown". */
    public static String serverName(Minecraft client) {
        ServerData data = client.getCurrentServer();
        if (data == null) {
            return client.getSingleplayerServer() != null ? "singleplayer" : "unknown";
        }
        return data.ip;
    }
}
