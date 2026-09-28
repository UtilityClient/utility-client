package dev.utilityclient.util;

/**
 * Records that the game started an attack on this frame, and runs a hook immediately.
 *
 * <p>This lives outside the mixin on purpose. Mixin refuses to apply a non-private static
 * field to its target, and even a private one would be copied into {@code Minecraft}, so the
 * state is kept here and the mixin only writes to it.
 *
 * <p>The hook exists for speed. Mace Swap used to read the flag on the following tick, which
 * meant the attack was already resolved before the swap happened. Running the swap from inside
 * the attack itself puts the mace in hand before the hit is dealt, which is the whole point of
 * the module.
 */
public final class AttackState {
    private static boolean attackStarted;
    private static Runnable hook;

    private AttackState() {
    }

    /**
     * Sets the flag and runs the hook, if one is registered, before the attack continues.
     *
     * <p>The hook is cleared before it runs rather than after, so a hook that throws cannot
     * leave a broken one installed to fire on every future attack.
     */
    public static void mark() {
        attackStarted = true;
        Runnable current = hook;
        hook = null;
        if (current != null) {
            current.run();
        }
    }

    /**
     * Registers a hook to run on the next attack. Replaces any previous one, so a module can
     * arm it every tick without them piling up.
     */
    public static void arm(Runnable action) {
        hook = action;
    }

    /** Reads and clears the flag, so each attack is reported exactly once. */
    public static boolean consume() {
        boolean was = attackStarted;
        attackStarted = false;
        return was;
    }

    /**
     * Clears the flag and disarms any hook, for example when a module is switched off or the
     * world changes. Disarming matters: a hook left installed would swap to a mace on the
     * player's next attack even though the module that armed it is off.
     */
    public static void clear() {
        attackStarted = false;
        hook = null;
    }
}
