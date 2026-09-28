package dev.utilityclient.util;

import dev.utilityclient.module.impl.PlayerEspModule;
import dev.utilityclient.module.impl.TargetEspModule;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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
     * The per frame camera state needed to project anything, captured once.
     *
     * <p>Built a single time per render pass rather than per entity, because rebuilding the
     * view rotation projection matrix for every player on the server is exactly the sort of
     * cost that turns a visual aid into a frame rate problem.
     */
    public static final class Frame {
        private final Matrix4f matrix;
        private final double cameraX;
        private final double cameraY;
        private final double cameraZ;
        private final int screenWidth;
        private final int screenHeight;
        private final Vector4f scratch = new Vector4f();

        private Frame(Matrix4f matrix, Vec3 camera, int screenWidth, int screenHeight) {
            this.matrix = matrix;
            this.cameraX = camera.x;
            this.cameraY = camera.y;
            this.cameraZ = camera.z;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        /**
         * Captures the current camera. Returns null when the client is not ready to be
         * projected from, which is the case before the first frame is rendered.
         */
        public static Frame capture(Minecraft client) {
            if (client == null || client.gameRenderer == null || client.getWindow() == null) {
                return null;
            }
            try {
                Camera camera = client.gameRenderer.mainCamera();
                if (camera == null) {
                    return null;
                }
                Matrix4f matrix = new Matrix4f();
                camera.getViewRotationProjectionMatrix(matrix);
                return new Frame(matrix, camera.position(),
                        client.getWindow().getGuiScaledWidth(),
                        client.getWindow().getGuiScaledHeight());
            } catch (RuntimeException exception) {
                return null;
            }
        }

        /**
         * Projects one world point to GUI pixels, or null when it cannot be drawn.
         *
         * <p>The matrix is applied by hand rather than through the game's
         * {@code projectPointToScreen}, because that method returns normalised device
         * coordinates, which run from minus one to one. Treating those as pixels collapses
         * every marker into the top left corner, which is the bug this replaced. Scaling to
         * the screen here also means the result is correct at any GUI scale, which is
         * something the normalised form has no knowledge of.
         *
         * <p>The clip space w is checked before dividing. A point behind the camera has a
         * negative w, and dividing by it mirrors the point to a plausible looking position on
         * the opposite side of the screen, so a player standing behind you would otherwise
         * appear in front of you.
         */
        public int[] toScreen(double x, double y, double z) {
            scratch.set((float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ), 1.0F);
            matrix.transform(scratch);

            if (scratch.w <= 0.0001F) {
                return null;
            }
            float ndcX = scratch.x / scratch.w;
            float ndcY = scratch.y / scratch.w;
            float ndcZ = scratch.z / scratch.w;

            // Outside the depth range means beyond the far plane or clipped away entirely.
            if (ndcZ < -1.0F || ndcZ > 1.0F) {
                return null;
            }

            int screenX = (int) ((ndcX * 0.5F + 0.5F) * screenWidth);
            // NDC has y pointing up, the screen has it pointing down, hence the flip.
            int screenY = (int) ((1.0F - (ndcY * 0.5F + 0.5F)) * screenHeight);
            return new int[]{screenX, screenY};
        }
    }

    /**
     * Projects an entity's bounding box and returns its screen bounds, or null when it cannot
     * be drawn at all.
     *
     * <p>Any one corner failing means the box would be nonsense, so the whole entity is
     * skipped rather than drawn wrong. That also drops entities behind the camera, which is
     * what you want: a marker for someone behind you is not information you can act on.
     */
    public static EspMarker project(Frame frame, Entity entity, int argb, String label) {
        return project(frame, entity, argb, label, false);
    }

    /**
     * Projects an entity's bounding box and returns its screen bounds.
     *
     * @param skeletonRequested whether the skeleton style is in use, which is the only time
     *                          the body joints are worth projecting at all
     */
    public static EspMarker project(Frame frame, Entity entity, int argb, String label,
                                    boolean skeletonRequested) {
        if (frame == null) {
            return null;
        }

        double left = entity.getX() - entity.getBbWidth() / 2.0;
        double right = entity.getX() + entity.getBbWidth() / 2.0;
        double back = entity.getZ() - entity.getBbWidth() / 2.0;
        double front = entity.getZ() + entity.getBbWidth() / 2.0;
        double bottom = entity.getY();
        double top = entity.getY() + entity.getBbHeight();

        int[] p0 = frame.toScreen(left, top, back);
        int[] p1 = frame.toScreen(right, top, back);
        int[] p2 = frame.toScreen(right, top, front);
        int[] p3 = frame.toScreen(left, top, front);
        int[] p4 = frame.toScreen(left, bottom, back);
        int[] p5 = frame.toScreen(right, bottom, back);
        int[] p6 = frame.toScreen(right, bottom, front);
        int[] p7 = frame.toScreen(left, bottom, front);
        if (p0 == null || p1 == null || p2 == null || p3 == null
                || p4 == null || p5 == null || p6 == null || p7 == null) {
            return null;
        }

        int minX = minOf(p0[0], p1[0], p2[0], p3[0], p4[0], p5[0], p6[0], p7[0]);
        int maxX = maxOf(p0[0], p1[0], p2[0], p3[0], p4[0], p5[0], p6[0], p7[0]);
        int minY = minOf(p0[1], p1[1], p2[1], p3[1], p4[1], p5[1], p6[1], p7[1]);
        int maxY = maxOf(p0[1], p1[1], p2[1], p3[1], p4[1], p5[1], p6[1], p7[1]);

        if (maxX <= minX || maxY <= minY) {
            return null;
        }

        // An entity far off screen still projects, often to absurd coordinates, so the box is
        // rejected rather than clamped. Clamping would paint a huge box across the display for
        // a player a few kilometres away, which looks broken and hides the real markers.
        if (maxX < 0 || minX > frame.screenWidth || maxY < 0 || minY > frame.screenHeight) {
            return null;
        }

        // The skeleton joints are attached only when that style is actually in use, so the
        // extra eight projections are not paid for by the box styles.
        EspMarker.Skeleton skeleton = null;
        if (skeletonRequested) {
            skeleton = projectSkeleton(frame, entity);
            if (skeleton == null) {
                return null;
            }
        }

        return new EspMarker(minX, minY, maxX, maxY, argb, label, true, entity, skeleton);
    }

    /**
     * Projects the body joints used by the skeleton style, or null when they cannot be drawn.
     *
     * <p>Proportions follow the standard player model rather than being invented: a head at
     * the top, shoulders at about 82 percent of the height, hips at 45 percent, and the arms
     * hanging to just above the hips. Using the entity's own height and width means a mob
     * that is not a player still gets a believable figure instead of a stretched one.
     *
     * <p>If any single joint is behind the camera the whole skeleton is dropped. A stick
     * figure with one missing arm looks like a bug, so it is all or nothing, which is the same
     * rule the box follows.
     */
    public static EspMarker.Skeleton projectSkeleton(Frame frame, Entity entity) {
        if (frame == null) {
            return null;
        }
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double width = entity.getBbWidth();
        double height = entity.getBbHeight();

        double shoulderY = y + height * 0.82;
        double hipY = y + height * 0.45;
        double handY = y + height * 0.50;
        double halfWidth = width / 2.0;
        // Legs sit inboard of the shoulders, which is what makes the figure read as a person
        // rather than a ladder.
        double legOffset = width * 0.22;

        int[] headTop = frame.toScreen(x, y + height, z);
        int[] neck = frame.toScreen(x, shoulderY, z);
        int[] shoulderLeft = frame.toScreen(x - halfWidth, shoulderY, z);
        int[] shoulderRight = frame.toScreen(x + halfWidth, shoulderY, z);
        int[] handLeft = frame.toScreen(x - halfWidth, handY, z);
        int[] handRight = frame.toScreen(x + halfWidth, handY, z);
        int[] hipLeft = frame.toScreen(x - legOffset, hipY, z);
        int[] hipRight = frame.toScreen(x + legOffset, hipY, z);
        int[] footLeft = frame.toScreen(x - legOffset, y, z);
        int[] footRight = frame.toScreen(x + legOffset, y, z);

        if (headTop == null || neck == null || shoulderLeft == null || shoulderRight == null
                || handLeft == null || handRight == null || hipLeft == null || hipRight == null
                || footLeft == null || footRight == null) {
            return null;
        }
        return new EspMarker.Skeleton(headTop, neck, shoulderLeft, shoulderRight,
                handLeft, handRight, hipLeft, hipRight, footLeft, footRight);
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

    private static int minOf(int... values) {
        int best = Integer.MAX_VALUE;
        for (int value : values) {
            best = Math.min(best, value);
        }
        return best;
    }

    private static int maxOf(int... values) {
        int best = Integer.MIN_VALUE;
        for (int value : values) {
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

            boolean skeleton = "skeleton".equals(style) || "both".equals(style);
            boolean corners = "corners".equals(style) || "both".equals(style);
            boolean outline = "box".equals(style) || "both".equals(style);

            if (skeleton) {
                // "both" means the skeleton over a box, and either half on its own means the
                // other half is not drawn, which is why the joints are requested up front.
                if (marker.skeleton != null) {
                    drawSkeleton(graphics, marker.skeleton, settings.skeletonThickness(), marker.argb);
                }
            }
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

        /** Line thickness for the skeleton style, separate from the box outline thickness. */
        int skeletonThickness();
    }

    /**
     * Draws the stick figure: a spine, a head, two arms, two legs, and the bars that join the
     * shoulders and the hips.
     *
     * <p>Every limb is drawn as a thick line rather than a single row of pixels, because at
     * one pixel the figure disappears against most backgrounds, which is the whole point of
     * having a thickness setting.
     */
    private static void drawSkeleton(GuiGraphicsExtractor graphics, EspMarker.Skeleton skeleton,
                                     int thickness, int color) {
        int t = Math.max(1, thickness);

        // Spine, from the neck up to the top of the head.
        thickLine(graphics, skeleton.neck, skeleton.headTop, t, color);
        // Shoulder bar.
        thickLine(graphics, skeleton.shoulderLeft, skeleton.shoulderRight, t, color);
        // Arms, hanging from the shoulders.
        thickLine(graphics, skeleton.shoulderLeft, skeleton.handLeft, t, color);
        thickLine(graphics, skeleton.shoulderRight, skeleton.handRight, t, color);
        // Hip bar, and the legs down to the feet.
        thickLine(graphics, skeleton.hipLeft, skeleton.hipRight, t, color);
        thickLine(graphics, skeleton.shoulderLeft, skeleton.hipLeft, t, color);
        thickLine(graphics, skeleton.shoulderRight, skeleton.hipRight, t, color);
        thickLine(graphics, skeleton.hipLeft, skeleton.footLeft, t, color);
        thickLine(graphics, skeleton.hipRight, skeleton.footRight, t, color);

        // A small box on the head, which is what makes the figure read as a person rather
        // than a coat hanger.
        int hx = Math.min(skeleton.headTop[0], skeleton.neck[0]);
        int hw = Math.abs(skeleton.headTop[0] - skeleton.neck[0]) + 1;
        int hy = Math.min(skeleton.headTop[1], skeleton.neck[1]);
        int hh = Math.abs(skeleton.headTop[1] - skeleton.neck[1]) + 1;
        if (hw > 0 && hh > 0) {
            for (int i = 0; i < t; i++) {
                graphics.fill(hx - i, hy - i, hx + hw + i, hy - i + 1, color);
                graphics.fill(hx - i, hy + hh + i - 1, hx + hw + i, hy + hh + i, color);
                graphics.fill(hx - i, hy - i, hx - i + 1, hy + hh + i, color);
                graphics.fill(hx + hw + i - 1, hy - i, hx + hw + i, hy + hh + i, color);
            }
        }
    }

    /** A straight line of the given thickness between two projected points. */
    private static void thickLine(GuiGraphicsExtractor graphics, int[] from, int[] to,
                                  int thickness, int color) {
        if (from == null || to == null) {
            return;
        }
        int steps = Math.min(2000, Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1])));
        if (steps <= 0) {
            return;
        }
        int half = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int x = from[0] + (to[0] - from[0]) * i / steps;
            int y = from[1] + (to[1] - from[1]) * i / steps;
            graphics.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
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
