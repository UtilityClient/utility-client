package dev.utilityclient;

import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.CrosshairModule;
import dev.utilityclient.module.impl.CustomBreakAnimationModule;
import dev.utilityclient.module.impl.HudModule;
import dev.utilityclient.module.impl.PlayerRadarModule;
import dev.utilityclient.module.impl.TargetLockModule;
import dev.utilityclient.module.impl.TargetEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudOverlay {
    private static final int ACCENT = 0xFFD000FF;
    private static final int TEXT = 0xFFF7F3FF;
    private static final int MUTED = 0xFF9A96A8;
    private static final int PANEL = 0xC012121A;
    private static final int PANEL_EDGE = 0xFF2B2B36;
    private static final int GHOST_PANEL = 0x662A1740;
    private static final int GREEN = 0xFF42E88A;

    private HudOverlay() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        ModuleManager modules = ModuleManager.get();
        HudModule hud = (HudModule) modules.find("hud");
        PlayerRadarModule radar = (PlayerRadarModule) modules.find("player-radar");

        if (hud != null && (hud.enabled() || hud.moving())) {
            drawHudBlock(graphics, client, hud, radar);
        }
        if (modules.isEnabled("crosshair")) {
            drawCrosshair(graphics, client, (CrosshairModule) modules.find("crosshair"));
        }
        TargetLockModule targetLock = (TargetLockModule) modules.find("target-lock");
        if (targetLock != null && targetLock.enabled()) {
            drawTargetLock(graphics, client, targetLock);
        }
        TargetEspModule targetEsp = (TargetEspModule) modules.find("target-esp");
        if (targetEsp != null && targetEsp.enabled()) {
            drawTargetEsp(graphics, client, targetEsp);
        }
    }

    // ------------------------------------------------------------------ esp

    /**
     * Draws a marker around every entity the module has collected.
     *
     * <p>Positions come from the module, which projects each entity's bounding box with the
     * game's own projector, so the boxes are correct at any angle, FOV, or GUI scale. This
     * method only turns those eight points into lines.
     *
     * <p>This is the x-ray part of the client. It shows players the server has not sent you,
     * and that is why it will get an account banned on any server with a real anticheat. It is
     * documented as such in the module, on the site, and in a chat warning the first time it
     * is switched on, because a cheat that does not admit what it is is worse than useless.
     */
    private static void drawTargetEsp(GuiGraphicsExtractor graphics, Minecraft client,
                                      TargetEspModule esp) {
        List<TargetEspModule.Marker> markers = esp.collect(client);
        if (markers.isEmpty()) {
            return;
        }

        int thickness = esp.thickness();
        int corner = esp.cornerSize();
        String style = esp.styleName();

        for (TargetEspModule.Marker marker : markers) {
            int width = marker.maxX - marker.minX;
            int height = marker.maxY - marker.minY;
            if (width <= 0 || height <= 0) {
                continue;
            }

            int fill = esp.fillAlpha();
            if (fill > 0) {
                // The fill is drawn at low alpha under the outline, tinted with the marker's
                // own colour so a visible player reads differently from one behind a wall.
                int fillColor = (fill << 24) | (marker.argb & 0x00FFFFFF);
                graphics.fill(marker.minX, marker.minY, marker.maxX, marker.maxY, fillColor);
            }

            boolean corners = "corners".equals(style) || "both".equals(style);
            boolean outline = "box".equals(style) || "both".equals(style);

            if (outline) {
                strokeRect(graphics, marker.minX, marker.minY, width, height, thickness, marker.argb);
            }
            if (corners) {
                strokeCorners(graphics, marker.minX, marker.minY, width, height,
                        Math.min(corner, Math.min(width, height) / 2), thickness, marker.argb);
            }

            if (esp.tracers()) {
                int lineColor = (0xB0000000 | (marker.argb & 0x00FFFFFF));
                int fromX = graphics.guiWidth() / 2;
                int fromY = graphics.guiHeight();
                drawLine(graphics, fromX, fromY, marker.minX + width / 2, marker.minY + height / 2,
                        lineColor);
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
            // top-left
            graphics.fill(x + i, y + i, x + l + i, y + i + 1, color);
            graphics.fill(x + i, y + i, x + i + 1, y + l + i, color);
            // top-right
            graphics.fill(x + width - l - i, y + i, x + width - i, y + i + 1, color);
            graphics.fill(x + width - i - 1, y + i, x + width - i, y + l + i, color);
            // bottom-left
            graphics.fill(x + i, y + height - i - 1, x + l + i, y + height - i, color);
            graphics.fill(x + i, y + height - l - i, x + i + 1, y + height - i, color);
            // bottom-right
            graphics.fill(x + width - l - i, y + height - i - 1, x + width - i, y + height - i, color);
            graphics.fill(x + width - i - 1, y + height - l - i, x + width - i, y + height - i, color);
        }
    }

    /** A straight line, drawn one pixel per step along the major axis. */
    private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                                 int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int steps = Math.max(dx, dy);
        if (steps <= 0) {
            return;
        }
        // Bounded so a badly projected marker cannot spin the renderer for a frame.
        steps = Math.min(steps, 4000);
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            fillRect(graphics, x, y, 1, 1, color);
        }
    }

    // ------------------------------------------------------------ target lock

    /**
     * The circle in the middle of the screen that marks a locked target.
     *
     * <p>It is a ring, sized by the module's FOV setting and eased toward that size by the
     * smoothness setting, so it grows into place rather than appearing at full size. The
     * target's name and distance go underneath it.
     *
     * <p>This is drawn only while a target is locked, and the module only ever locks someone
     * the player can already see. It is a marker, not a tracker: nothing here reveals
     * anything the client was not already showing, and it sends nothing to the server.
     */
    private static void drawTargetLock(GuiGraphicsExtractor graphics, Minecraft client,
                                       TargetLockModule lock) {
        int radius = lock.drawRadius();
        if (radius <= 0) {
            return;
        }
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int rgba = lock.rgba();
        int thickness = lock.lineThickness();

        // Concentric bands rather than a single pass, so the ring has real thickness and a
        // soft inner edge rather than looking like a single row of pixels.
        float inner = radius - thickness;
        if (inner < 0) {
            inner = 0;
        }
        int steps = Math.max(24, radius * 2);
        for (int i = 0; i < steps; i++) {
            double angle = (Math.PI * 2.0 * i) / steps;
            for (int band = 0; band < thickness; band++) {
                double r = inner + band;
                int px = centerX + (int) Math.round(Math.cos(angle) * r);
                int py = centerY + (int) Math.round(Math.sin(angle) * r);
                fillRect(graphics, px, py, 1, 1, rgba);
            }
        }

        // Four short ticks on the axes, so the circle reads as a reticle rather than a plain
        // ring and its centre stays obvious.
        for (int band = 0; band < thickness; band++) {
            int r = (int) Math.round(inner) + band;
            fillRect(graphics, centerX + r, centerY, 1, 1, rgba);
            fillRect(graphics, centerX - r - 1, centerY, 1, 1, rgba);
            fillRect(graphics, centerX, centerY + r, 1, 1, rgba);
            fillRect(graphics, centerX, centerY - r - 1, 1, 1, rgba);
        }

        String label = lock.label(client);
        if (!label.isEmpty()) {
            int labelY = centerY + radius + 6;
            graphics.text(client.font, label,
                    centerX - client.font.width(label) / 2, labelY, rgba, false);
        }
    }

    private static void drawHudBlock(GuiGraphicsExtractor graphics, Minecraft client, HudModule hud,
                                     PlayerRadarModule radar) {
        List<String[]> lines = new ArrayList<>();
        if (hud.showFps.value()) {
            lines.add(new String[]{"FPS", String.valueOf(client.getFps())});
        }
        if (hud.showCoordinates.value()) {
            lines.add(new String[]{"XYZ", String.format(Locale.ROOT, "%.1f / %.1f / %.1f",
                    client.player.getX(), client.player.getY(), client.player.getZ())});
            lines.add(new String[]{"Facing", client.player.getDirection().toString()});
        }
        if (hud.showSession.value()) {
            lines.add(new String[]{"Session", formatDuration(UtilityClient.sessionSeconds())});
        }

        boolean radarActive = radar != null && radar.enabled();
        if (lines.isEmpty() && !radarActive) {
            return;
        }

        int width = textWidth(client, lines);
        int height = 10 + lines.size() * 12;
        int radarSpace = radarActive ? radar.size.value() + 16 : 0;
        int blockHeight = height + radarSpace;

        int x = hud.moving() ? hud.ghostX() : hud.positionX.value();
        int y = hud.moving() ? hud.ghostY() : hud.positionY.value();
        x = Math.max(4, Math.min(graphics.guiWidth() - Math.max(width, radarActive ? radar.size.value() : 0) - 12, x));
        y = Math.max(4, Math.min(graphics.guiHeight() - blockHeight - 6, y));

        boolean wide = radarActive && radar.size.value() > width;
        int panelWidth = wide ? radar.size.value() : width;
        int panelHeight = blockHeight;

        if (hud.moving()) {
            roundRect(graphics, x - 5, y - 5, panelWidth + 10, panelHeight + 10, 8, GHOST_PANEL);
            roundRect(graphics, x - 6, y - 6, panelWidth + 12, panelHeight + 12, 9, ACCENT);
        } else {
            roundRect(graphics, x - 5, y - 5, panelWidth + 10, panelHeight + 10, 8, PANEL_EDGE);
            roundRect(graphics, x - 4, y - 4, panelWidth + 8, panelHeight + 8, 7, PANEL);
        }

        int lineY = y;
        for (String[] line : lines) {
            graphics.text(client.font, line[0], x, lineY, MUTED, false);
            graphics.text(client.font, line[1], x + 42, lineY, TEXT, false);
            lineY += 12;
        }

        if (radarActive) {
            drawRadar(graphics, client, radar, x, y + height + 8, panelWidth);
        }

        if (hud.moving()) {
            drawMoveHints(graphics, client);
        }
    }

    private static void drawMoveHints(GuiGraphicsExtractor graphics, Minecraft client) {
        String title = "MOVING HUD";
        String hint = "Left-click to place  •  ESC or right-click to cancel";
        int boxWidth = Math.min(graphics.guiWidth() - 40, Math.max(client.font.width(hint) + 28, 300));
        int boxHeight = 46;
        int x = (graphics.guiWidth() - boxWidth) / 2;
        int y = graphics.guiHeight() - 78;

        roundRect(graphics, x, y, boxWidth, boxHeight, 9, PANEL_EDGE);
        roundRect(graphics, x + 1, y + 1, boxWidth - 2, boxHeight - 2, 8, PANEL);
        graphics.text(client.font, title, x + 14, y + 9, ACCENT, false);
        graphics.text(client.font, hint, x + 14, y + 26, GREEN, false);
    }

    // ------------------------------------------------------------------ radar

    private static void drawRadar(GuiGraphicsExtractor graphics, Minecraft client, PlayerRadarModule radar,
                                  int x, int y, int panelWidth) {
        int diameter = Math.min(radar.size.value(), panelWidth);
        int centerX = x + diameter / 2;
        int centerY = y + diameter / 2;
        int radiusPx = Math.max(1, diameter / 2 - 2);
        double range = Math.max(1, radar.radius.value());

        // background disc
        for (int py = -radiusPx; py <= radiusPx; py++) {
            int halfWidth = (int) Math.sqrt(Math.max(0, radiusPx * radiusPx - py * py));
            graphics.fill(centerX - halfWidth, centerY + py, centerX + halfWidth + 1, centerY + py + 1, 0x66090A10);
        }

        if (radar.showBorders.value()) {
            int border = 0xFF2B2B36;
            for (int i = 0; i < radiusPx; i++) {
                int inset = radiusPx - i;
                int shade = i < 2 ? ACCENT : border;
                graphics.fill(centerX - inset, centerY - radiusPx + i, centerX + inset, centerY - radiusPx + i + 1, shade);
                graphics.fill(centerX - inset, centerY + radiusPx - i, centerX + inset, centerY + radiusPx - i + 1, shade);
                graphics.fill(centerX - radiusPx + i, centerY - inset, centerX - radiusPx + i + 1, centerY + inset, shade);
                graphics.fill(centerX + radiusPx - i, centerY - inset, centerX + radiusPx - i + 1, centerY + inset, shade);
            }
            // cross axes
            int grid = 0xFF23232E;
            graphics.fill(centerX - radiusPx, centerY, centerX + radiusPx, centerY + 1, grid);
            graphics.fill(centerX, centerY - radiusPx, centerX + 1, centerY + radiusPx, grid);
            // north marker
            graphics.fill(centerX - 1, centerY - radiusPx, centerX + 2, centerY - radiusPx + 3, GREEN);
        }

        LocalPlayer self = client.player;
        float yaw = radar.rotateWithPlayer.value() ? self.getYRot() : 0.0F;

        for (Player other : client.level.players()) {
            if (other == self) {
                continue;
            }
            if (radar.hideSpectators.value() && other.isSpectator()) {
                continue;
            }
            double dx = other.getX() - self.getX();
            double dz = other.getZ() - self.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > range) {
                continue;
            }

            // rotate the relative position around the player facing
            double angle = Math.toRadians(-yaw);
            double rx = dx * Math.cos(angle) - dz * Math.sin(angle);
            double rz = dx * Math.sin(angle) + dz * Math.cos(angle);

            double scale = radiusPx / range;
            int px = centerX + (int) Math.round(rx * scale);
            int py = centerY + (int) Math.round(rz * scale);

            int dotColor;
            if (radar.verticalInfo.value() && Math.abs(other.getY() - self.getY()) > 3.0D) {
                dotColor = other.getY() > self.getY() ? 0xFF63F2B5 : 0xFFFF5577;
            } else {
                dotColor = 0xFF000000 | radar.color.colorValue();
            }

            int dot = radar.dotSize.value();
            graphics.fill(px - dot / 2, py - dot / 2, px - dot / 2 + dot, py - dot / 2 + dot, dotColor);

            if (other instanceof RemotePlayer && (radar.showNames.value() || radar.showDistance.value())) {
                String label = buildLabel(client, other, distance, radar);
                if (!label.isEmpty()) {
                    int textY = py - dot / 2 - 10;
                    if (textY < centerY - radiusPx) {
                        textY = py + dot / 2 + 2;
                    }
                    graphics.text(client.font, label, px - client.font.width(label) / 2, textY, 0xFFE6E2F0, false);
                }
            }
        }

        // self marker
        int selfSize = Math.max(2, radar.dotSize.value());
        int selfColor = 0xFF000000 | radar.selfColor.colorValue();
        graphics.fill(centerX - selfSize / 2, centerY - selfSize / 2,
                centerX - selfSize / 2 + selfSize, centerY - selfSize / 2 + selfSize, selfColor);

        String label = "PLAYERS " + countPlayers(client, radar);
        graphics.text(client.font, label, centerX - client.font.width(label) / 2,
                centerY + radiusPx + 4, MUTED, false);
    }

    private static String buildLabel(Minecraft client, Player other, double distance, PlayerRadarModule radar) {
        StringBuilder builder = new StringBuilder();
        if (radar.showNames.value()) {
            builder.append(other.getName().getString());
        }
        if (radar.showDistance.value()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Math.round(distance)).append("m");
        }
        return builder.toString();
    }

    private static int countPlayers(Minecraft client, PlayerRadarModule radar) {
        int count = 0;
        LocalPlayer self = client.player;
        for (Player other : client.level.players()) {
            if (other == self) {
                continue;
            }
            if (radar.hideSpectators.value() && other.isSpectator()) {
                continue;
            }
            double dx = other.getX() - self.getX();
            double dz = other.getZ() - self.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= radar.radius.value()) {
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------- crosshair

    private static void drawCrosshair(GuiGraphicsExtractor graphics, Minecraft client, CrosshairModule crosshair) {
        if (crosshair == null) {
            return;
        }
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int size = crosshair.size.value();
        int line = crosshair.thickness.value();
        int color = 0xFF000000 | crosshair.color.colorValue();
        String style = crosshair.styleName();

        switch (style) {
            case "none" -> {
                return;
            }
            case "dot" -> {
                fillRect(graphics, centerX, centerY, 1, 1, color);
                return;
            }
            case "plus" -> {
                fillRect(graphics, centerX - size, centerY - line / 2, size * 2, line, color);
                fillRect(graphics, centerX - line / 2, centerY - size, line, size * 2, color);
            }
            case "cross" -> {
                int gap = Math.max(1, size / 3);
                fillRect(graphics, centerX - size, centerY - line / 2, size - gap, line, color);
                fillRect(graphics, centerX + gap, centerY - line / 2, size - gap, line, color);
                fillRect(graphics, centerX - line / 2, centerY - size, line, size - gap, color);
                fillRect(graphics, centerX - line / 2, centerY + gap, line, size - gap, color);
            }
            case "t-shape" -> {
                fillRect(graphics, centerX - size, centerY - line / 2, size * 2, line, color);
                fillRect(graphics, centerX - line / 2, centerY, line, size, color);
            }
            case "x" -> {
                for (int i = -size; i <= size; i++) {
                    fillRect(graphics, centerX + i, centerY + i, line, line, color);
                    fillRect(graphics, centerX + i, centerY - i, line, line, color);
                }
            }
            case "diamond" -> {
                for (int i = 0; i < size; i++) {
                    int width = (size - i) * 2;
                    fillRect(graphics, centerX - width / 2, centerY - i - line / 2, width, line, color);
                    fillRect(graphics, centerX - width / 2, centerY + i + line / 2 - line, width, line, color);
                }
            }
            case "arrow" -> {
                fillRect(graphics, centerX - size, centerY - line / 2, size, line, color);
                fillRect(graphics, centerX + 1, centerY - line / 2, size, line, color);
                for (int i = 0; i < size / 2; i++) {
                    fillRect(graphics, centerX - i, centerY - i - line / 2, 1, line, color);
                    fillRect(graphics, centerX + i, centerY - i - line / 2, 1, line, color);
                }
            }
            case "circle", "ring" -> {
                int steps = Math.max(12, size * 4);
                int ringThickness = "ring".equals(style) ? Math.max(1, size / 4) : 1;
                for (int i = 0; i < steps; i++) {
                    double angle = (Math.PI * 2.0 * i) / steps;
                    int px = centerX + (int) Math.round(Math.cos(angle) * size);
                    int py = centerY + (int) Math.round(Math.sin(angle) * size);
                    fillRect(graphics, px, py, ringThickness, ringThickness, color);
                }
            }
            default -> {
                fillRect(graphics, centerX - size, centerY - line / 2, size * 2, line, color);
                fillRect(graphics, centerX - line / 2, centerY - size, line, size * 2, color);
            }
        }

        if (crosshair.centerDot.value()) {
            fillRect(graphics, centerX, centerY, 1, 1, color);
        }

        if (crosshair.attackIndicator.value()) {
            CustomBreakAnimationModule breakAnimation =
                    (CustomBreakAnimationModule) ModuleManager.get().find("custom-break-animation");
            if (breakAnimation != null && breakAnimation.enabled() && breakAnimation.isBreaking(client)) {
                float progress = breakAnimation.progress();
                int width = (int) Math.round(size * 2.0F * Math.max(0.0F, Math.min(1.0F, progress)));
                if (width > 0) {
                    fillRect(graphics, centerX - size, centerY - size - 6, width, 2, ACCENT);
                }
            }
        }
    }

    private static void fillRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, color);
    }

    private static int textWidth(Minecraft client, List<String[]> lines) {
        int width = 0;
        for (String[] line : lines) {
            width = Math.max(width, client.font.width(line[1]));
        }
        return 42 + width + 6;
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static void roundRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                  int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - 1 - i, x + width - inset, y + height - i, color);
        }
    }
}
