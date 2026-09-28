package dev.utilityclient.util;

import dev.utilityclient.module.impl.PlayerEspModule;
import dev.utilityclient.module.impl.TargetEspModule;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Turns entities into on screen markers.
 *
 * <p>Both ESP modules share this, because they differ only in which entities they are handed.
 * Player ESP passes every player in range; Target ESP passes just the locked one. Keeping the
 * projection here means the two cannot drift apart and show boxes in slightly different places.
 *
 * <p>Projection uses the game's own {@code projectPointToScreen}, the same call the vanilla
 * waypoint renderer uses, so the result is correct under any field of view, GUI scale,
 * resolution, or camera angle, including while Zoom or Free Look is active.
 */
public final class EspProjection {
    private EspProjection() {
    }

    /**
     * Projects an entity's bounding box and returns its screen bounds, or null when it cannot
     * be drawn at all.
     *
     * <p>Any one corner failing means the box would be nonsense, so the whole entity is
     * skipped rather than drawn wrong. That also naturally drops entities behind the camera,
     * whose projection mirrors to a plausible looking point on the opposite side of the
     * screen instead of failing loudly.
     */
    public static EspMarker project(Minecraft client, Entity entity, int argb, String label) {
        if (client.gameRenderer == null) {
            return null;
        }

        double left = entity.getX() - entity.getBbWidth() / 2.0;
        double right = entity.getX() + entity.getBbWidth() / 2.0;
        double back = entity.getZ() - entity.getBbWidth() / 2.0;
        double front = entity.getZ() + entity.getBbWidth() / 2.0;
        double bottom = entity.getY();
        double top = entity.getY() + entity.getBbHeight();

        Vec3 p0 = project(client, left, top, back);
        Vec3 p1 = project(client, right, top, back);
        Vec3 p2 = project(client, right, top, front);
        Vec3 p3 = project(client, left, top, front);
        Vec3 p4 = project(client, left, bottom, back);
        Vec3 p5 = project(client, right, bottom, back);
        Vec3 p6 = project(client, right, bottom, front);
        Vec3 p7 = project(client, left, bottom, front);
        if (p0 == null || p1 == null || p2 == null || p3 == null
                || p4 == null || p5 == null || p6 == null || p7 == null) {
            return null;
        }

        int minX = (int) Math.round(minOf(p0.x, p1.x, p2.x, p3.x, p4.x, p5.x, p6.x, p7.x));
        int maxX = (int) Math.round(maxOf(p0.x, p1.x, p2.x, p3.x, p4.x, p5.x, p6.x, p7.x));
        int minY = (int) Math.round(minOf(p0.y, p1.y, p2.y, p3.y, p4.y, p5.y, p6.y, p7.y));
        int maxY = (int) Math.round(maxOf(p0.y, p1.y, p2.y, p3.y, p4.y, p5.y, p6.y, p7.y));

        // An entity far off screen still projects, often to absurd coordinates, so the box is
        // rejected rather than clamped. Clamping would paint a huge box across the display for
        // a player a few kilometres away, which looks broken and hides the real markers.
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (maxX < 0 || minX > screenW || maxY < 0 || minY > screenH) {
            return null;
        }
        if (maxX - minX <= 0 || maxY - minY <= 0) {
            return null;
        }

        return new EspMarker(minX, minY, maxX, maxY, argb, label, true, entity);
    }

    /** Projects one world point, or null when it is behind the camera or otherwise unusable. */
    private static Vec3 project(Minecraft client, double x, double y, double z) {
        Vec3 result;
        try {
            result = client.gameRenderer.projectPointToScreen(new Vec3(x, y, z));
        } catch (RuntimeException exception) {
            return null;
        }
        // The near plane sits at zero, so anything at or behind it is not drawable. Without
        // this check a player behind you appears mirrored on the far side of the screen.
        return result == null || result.z <= 0.0D ? null : result;
    }

    /** True when the player can see this entity, used to colour visible and hidden separately. */
    public static boolean visibleTo(Minecraft client, Entity entity) {
        LocalPlayer self = client.player;
        if (self == null) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            return self.hasLineOfSight(living);
        }
        return true;
    }

    /** Builds the name and distance label both modules show. */
    public static String label(Minecraft client, Entity entity,
                               boolean showName, boolean showDistance) {
        StringBuilder builder = new StringBuilder();
        if (showName) {
            builder.append(entity.getName().getString());
        }
        if (showDistance && client.player != null) {
            if (!builder.isEmpty()) {
                builder.append("  ");
            }
            builder.append(Math.round(client.player.distanceTo(entity))).append("m");
        }
        return builder.toString();
    }

    /** Lower cased entity name, for the HUD. */
    public static String nameOf(Entity entity) {
        return entity.getName().getString().toLowerCase(Locale.ROOT);
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

    /* ------------------------------------------------------------------ draw */

    /**
     * Draws a list of markers using whichever settings object was handed in.
     *
     * <p>Deliberately takes the settings as a small interface rather than a concrete module,
     * which is what lets Player ESP and Target ESP share one painter with different options.
     */
    public static void paint(GuiGraphicsExtractor graphics, Minecraft client,
                             List<EspMarker> markers, Settings settings) {
        if (markers.isEmpty()) {
            return;
        }
        int thickness = settings.thickness();
        String style = settings.style();

        for (EspMarker marker : markers) {
            int width = marker.width();
            int height = marker.height();
            if (width <= 0 || height <= 0) {
                continue;
            }

            int fill = settings.fillAlpha();
            if (fill > 0) {
                graphics.fill(marker.minX, marker.minY, marker.maxX, marker.maxY,
                        (fill << 24) | (marker.argb & 0x00FFFFFF));
            }

            boolean corners = "corners".equals(style) || "both".equals(style);
            boolean outline = "box".equals(style) || "both".equals(style);
            if (outline) {
                strokeRect(graphics, marker.minX, marker.minY, width, height, thickness, marker.argb);
            }
            if (corners) {
                strokeCorners(graphics, marker.minX, marker.minY, width, height,
                        Math.min(settings.cornerSize(), Math.min(width, height) / 2),
                        thickness, marker.argb);
            }

            if (settings.tracers()) {
                int lineColor = 0xB0000000 | (marker.argb & 0x00FFFFFF);
                drawLine(graphics, graphics.guiWidth() / 2, graphics.guiHeight(),
                        marker.minX + width / 2, marker.minY + height / 2, lineColor);
            }

            if (!marker.label.isEmpty()) {
                int labelY = marker.minY - 10;
                if (labelY < 2) {
                    labelY = marker.minY + 2;
                }
                graphics.text(client.font, marker.label,
                        marker.minX + width / 2 - client.font.width(marker.label) / 2,
                        labelY, marker.argb, false);
            }
        }
    }

    /** The drawing options a painter needs, so both modules can supply their own. */
    public interface Settings {
        String style();

        int thickness();

        int cornerSize();

        int fillAlpha();

        boolean tracers();
    }

    private static void strokeRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int thickness, int color) {
        int t = Math.max(1, thickness);
        for (int i = 0; i < t; i++) {
            graphics.fill(x + i, y + i, x + width - i, y + i + 1, color);
            graphics.fill(x + i, y + height - i - 1, x + width - i, y + height - i, color);
            graphics.fill(x + i, y + i, x + i + 1, y + height - i, color);
            graphics.fill(x + width - i - 1, y + i, x + width - i, y + height - i, color);
        }
    }

    private static void strokeCorners(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                      int length, int thickness, int color) {
        int t = Math.max(1, thickness);
        int l = Math.max(1, length);
        for (int i = 0; i < t; i++) {
            graphics.fill(x + i, y + i, x + l + i, y + i + 1, color);
            graphics.fill(x + i, y + i, x + i + 1, y + l + i, color);
            graphics.fill(x + width - l - i, y + i, x + width - i, y + i + 1, color);
            graphics.fill(x + width - i - 1, y + i, x + width - i, y + l + i, color);
            graphics.fill(x + i, y + height - i - 1, x + l + i, y + height - i, color);
            graphics.fill(x + i, y + height - l - i, x + i + 1, y + height - i, color);
            graphics.fill(x + width - l - i, y + height - i - 1, x + width - i, y + height - i, color);
            graphics.fill(x + width - i - 1, y + height - l - i, x + width - i, y + height - i, color);
        }
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                 int color) {
        int steps = Math.min(4000, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        if (steps <= 0) {
            return;
        }
        // Bounded, so one badly projected marker cannot stall a frame.
        for (int i = 0; i <= steps; i++) {
            graphics.fill(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps,
                    x0 + (x1 - x0) * i / steps + 1, y0 + (y1 - y0) * i / steps + 1, color);
        }
    }
}
