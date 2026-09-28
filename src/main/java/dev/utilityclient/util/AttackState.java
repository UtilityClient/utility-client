package dev.utilityclient.util;

/**
 * Records that the game began an attack on this frame.
 *
 * <p>This lives outside the mixin on purpose. Mixin refuses to apply a non-private static
 * field to its target, and even a private one would be copied into {@code Minecraft}, so the
 * state is kept here and the mixin only writes to it.
 *
 * <p>The flag is set the instant an attack starts and cleared by whoever reads it, so an
 * attack is never lost between ticks and never counted twice.
 */
public final class AttackState {
    private static boolean attackStarted;

    private AttackState() {
    }

    /** Called by the mixin when an attack begins. */
    public static void mark() {
        attackStarted = true;
    }

    /** Reads and clears the flag, so each attack is reported exactly once. */
    public static boolean consume() {
        boolean was = attackStarted;
        attackStarted = false;
        return was;
    }

    /** Clears a stale flag, for example after a world change. */
    public static void clear() {
        attackStarted = false;
    }
}
