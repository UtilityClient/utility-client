package dev.utilityclient.util;

import net.minecraft.world.entity.Entity;

/**
 * One entity's projected marker, in GUI pixels.
 *
 * <p>Shared by Player ESP and Target ESP so both draw from exactly the same numbers. The box
 * is built from the entity's real eight bounding box corners rather than guessed from
 * distance, which is what keeps it correct at an angle, when crouched, or when scaled.
 */
public final class EspMarker {
    public final int minX;
    public final int minY;
    public final int maxX;
    public final int maxY;
    public final int argb;
    public final String label;
    /** True when the player could already be seen, so the colour can differ from a wall one. */
    public final boolean visible;
    public final Entity entity;
    /** The body joints used by the skeleton style, or null when the box style is in use. */
    public final Skeleton skeleton;

    public EspMarker(int minX, int minY, int maxX, int maxY, int argb, String label,
                     boolean visible, Entity entity) {
        this(minX, minY, maxX, maxY, argb, label, visible, entity, null);
    }

    public EspMarker(int minX, int minY, int maxX, int maxY, int argb, String label,
                     boolean visible, Entity entity, Skeleton skeleton) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.argb = argb;
        this.label = label == null ? "" : label;
        this.visible = visible;
        this.entity = entity;
        this.skeleton = skeleton;
    }

    public int width() {
        return maxX - minX;
    }

    public int height() {
        return maxY - minY;
    }

    /**
     * The joints of a stick figure skeleton, each already projected to screen pixels.
     *
     * <p>Every joint is a separate point rather than a line, because a limb is a straight
     * segment between two joints and a line needs both ends. Storing the joints and letting
     * the painter connect them is what lets the thickness setting apply to every limb at once.
     */
    public static final class Skeleton {
        /** Top of the head, and the neck just below it. */
        public final int[] headTop;
        public final int[] neck;
        /** Shoulders, the hands hanging below them, and the bar between the shoulders. */
        public final int[] shoulderLeft;
        public final int[] shoulderRight;
        public final int[] handLeft;
        public final int[] handRight;
        /** Hips, the feet below them, and the bar between the hips. */
        public final int[] hipLeft;
        public final int[] hipRight;
        public final int[] footLeft;
        public final int[] footRight;

        public Skeleton(int[] headTop, int[] neck, int[] shoulderLeft, int[] shoulderRight,
                        int[] handLeft, int[] handRight, int[] hipLeft, int[] hipRight,
                        int[] footLeft, int[] footRight) {
            this.headTop = headTop;
            this.neck = neck;
            this.shoulderLeft = shoulderLeft;
            this.shoulderRight = shoulderRight;
            this.handLeft = handLeft;
            this.handRight = handRight;
            this.hipLeft = hipLeft;
            this.hipRight = hipRight;
            this.footLeft = footLeft;
            this.footRight = footRight;
        }
    }
}
