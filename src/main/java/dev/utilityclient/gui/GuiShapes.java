package dev.utilityclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared shape drawing for the menu screens.
 *
 * <p>Every screen used to carry its own copy of a rounded rectangle routine, and every copy
 * did the same thing wrong: it chamfered the corners with a straight diagonal instead of an
 * arc, so a radius of 16 looked like a 45 degree cut rather than a curve. This does the
 * real thing.
 *
 * <p>For each row of a corner it works out how far in the curve is, using the circle
 * equation, and only fills from there. The offsets are worked out once per radius and then
 * cached, so the per-frame cost is a couple of fills per row rather than a square root per
 * pixel.
 */
public final class GuiShapes {
    private GuiShapes() {
    }

    /** Offsets per radius, built on first use. The menu mixes many radii in one frame. */
    private static final int MAX_RADIUS = 32;
    private static final int[][] ARC_CACHE = new int[MAX_RADIUS + 1][];

    /**
     * Horizontal inset for each row of a corner arc of the given radius.
     *
     * <p>Row 0 is the topmost row and is inset by the full radius, narrowing to 1 by the
     * bottom of the arc, which is what makes it a curve rather than a diagonal.
     */
    private static int[] offsets(int radius) {
        int r = Math.max(1, Math.min(MAX_RADIUS, radius));
        int[] cached = ARC_CACHE[r];
        if (cached != null) {
            return cached;
        }

        int[] built = new int[r];
        double r2 = (double) r * r;
        for (int row = 0; row < r; row++) {
            // Distance from this row up to the centre of the arc.
            double dy = r - row;
            double remaining = r2 - dy * dy;
            if (remaining < 0) {
                remaining = 0;
            }
            int reach = (int) Math.round(Math.sqrt(remaining));
            // Never let the arc eat the whole radius, that would clip the corner flat.
            int inset = r - reach;
            built[row] = Math.max(1, Math.min(r, inset));
        }
        ARC_CACHE[r] = built;
        return built;
    }

    /** Filled rectangle with genuinely circular corners. */
    public static void roundRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                 int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (color == 0x00000000) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }

        int[] arc = offsets(r);

        // Middle band, between the two corner regions.
        graphics.fill(x + r, y, x + width - r, y + height, color);

        // Top band: only the columns the arc allows on each side.
        for (int row = 0; row < r; row++) {
            int inset = arc[row];
            int py = y + row;
            graphics.fill(x + inset, py, x + width - inset, py + 1, color);
            int by = y + height - 1 - row;
            graphics.fill(x + inset, by, x + width - inset, by + 1, color);
        }
    }

    /**
     * A panel: border ring in {@code border}, then the fill one pixel inside it. The fill
     * uses a slightly smaller radius so the border stays even all the way round, which is
     * what makes the edge read as a clean line rather than a smudge.
     */
    public static void roundPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                  int radius, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        roundRect(graphics, x, y, width, height, radius, border);
        if (width <= 2 || height <= 2) {
            return;
        }
        roundRect(graphics, x + 1, y + 1, width - 2, height - 2,
                Math.max(1, radius - 1), fill);
    }

    /** A filled circle, used for the toggle knob, status dots and the logo. */
    public static void fillCircle(GuiGraphicsExtractor graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            double remaining = (double) radius * radius - (double) dy * dy;
            if (remaining < 0) {
                continue;
            }
            int half = (int) Math.round(Math.sqrt(remaining));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** A circle drawn as a ring, used for the info badge. */
    public static void strokeCircle(GuiGraphicsExtractor graphics, int cx, int cy, int radius,
                                    int thickness, int color) {
        int inner = Math.max(0, radius - Math.max(1, thickness));
        for (int dy = -radius; dy <= radius; dy++) {
            double outerRemaining = (double) radius * radius - (double) dy * dy;
            if (outerRemaining < 0) {
                continue;
            }
            int outerHalf = (int) Math.round(Math.sqrt(outerRemaining));
            int innerHalf = 0;
            if (Math.abs(dy) <= inner) {
                double innerRemaining = (double) inner * inner - (double) dy * dy;
                innerHalf = innerRemaining <= 0 ? 0 : (int) Math.round(Math.sqrt(innerRemaining));
            }
            if (outerHalf <= innerHalf) {
                continue;
            }
            graphics.fill(cx - outerHalf, cy + dy, cx - innerHalf, cy + dy + 1, color);
            graphics.fill(cx + innerHalf, cy + dy, cx + outerHalf + 1, cy + dy + 1, color);
        }
    }

    /** A straight line of the given thickness, used for the small nav icons. */
    public static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1,
                            int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int half = Math.max(0, thickness / 2);
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
            if (x == x1 && y == y1) {
                break;
            }
            int doubled = 2 * err;
            if (doubled > -dy) {
                err -= dy;
                x += sx;
            }
            if (doubled < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
