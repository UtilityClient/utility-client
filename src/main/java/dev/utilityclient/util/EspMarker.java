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

    public EspMarker(int minX, int minY, int maxX, int maxY, int argb, String label,
                     boolean visible, Entity entity) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.argb = argb;
        this.label = label == null ? "" : label;
        this.visible = visible;
        this.entity = entity;
    }

    public int width() {
        return maxX - minX;
    }

    public int height() {
        return maxY - minY;
    }
}
