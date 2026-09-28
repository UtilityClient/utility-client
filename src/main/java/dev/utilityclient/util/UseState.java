package dev.utilityclient.util;

/**
 * Records that the game began a right click (use item) on this frame.
 *
 * <p>Same problem as {@link AttackState}: the game consumes the use binding itself while
 * handling input, before the end of the client tick, so polling {@code options.keyUse} always
 * reads nothing. The flag is set by a mixin at the real entry point.
 *
 * <p>Kept outside the mixin because mixin refuses to apply a non-private static field to its
 * target, and a private one would still be merged into {@code Minecraft}.
 */
public final class UseState {
    private static boolean useStarted;

    private UseState() {
    }

    /** Called by the mixin when a use begins. */
    public static void mark() {
        useStarted = true;
    }

    /** Reads and clears the flag, so each use is reported exactly once. */
    public static boolean consume() {
        boolean was = useStarted;
        useStarted = false;
        return was;
    }

    /** Clears a stale flag, for example after a world change. */
    public static void clear() {
        useStarted = false;
    }
}
