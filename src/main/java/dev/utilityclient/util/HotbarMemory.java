package dev.utilityclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/**
 * Remembers the hotbar slot a swap started from, so a module can put it back.
 *
 * <p>Both swap modules need this: swap away for a moment, then return to whatever the player
 * was actually holding. They differ only in when they return, so the bookkeeping and the
 * selection change live here.
 *
 * <p>Only the hotbar selection changes. The selection is set locally and the server is told
 * separately, so both sides agree on what is in hand. No item is picked up, moved or
 * consumed, and nothing here writes to an item stack.
 */
public final class HotbarMemory {
    /** The slot the player was on before we swapped away from it, or -1 for none. */
    private static int previous = -1;
    /** The slot we swapped to, so we can tell whether the player has since moved on. */
    private static int swappedTo = -1;
    /** Ticks remaining before an automatic return is due. */
    private static int returnIn = -1;

    private HotbarMemory() {
    }

    /**
     * Selects a slot and remembers where we came from.
     *
     * <p>Does nothing when the target is the slot already held, so a redundant swap does not
     * overwrite the remembered slot with the slot we are already on. That would make the
     * module "return" to where it already was and lose the player's real previous item.
     *
     * @return true when the selection actually changed
     */
    public static boolean swapTo(Minecraft client, int slot) {
        if (slot < 0 || client.player == null) {
            return false;
        }
        int current = client.player.getInventory().getSelectedSlot();
        if (current == slot) {
            return false;
        }
        previous = current;
        swappedTo = slot;
        select(client, slot);
        return true;
    }

    /** Arms an automatic return after the given number of ticks. */
    public static void returnAfter(int ticks) {
        returnIn = Math.max(1, ticks);
    }

    /**
     * Returns to the remembered slot if the player is still on the one we moved them to.
     *
     * <p>That check is the important part. If they scrolled away themselves, they have since
     * made their own choice, and yanking them back would fight them. Cancelling is the
     * respectful behaviour and is almost always what you want.
     *
     * @return true when a return happened
     */
    public static boolean returnIfUnmoved(Minecraft client) {
        if (client.player == null) {
            forget();
            return false;
        }
        if (client.player.getInventory().getSelectedSlot() != swappedTo) {
            forget();
            return false;
        }
        if (previous < 0 || previous == swappedTo) {
            forget();
            return false;
        }
        int target = previous;
        forget();
        select(client, target);
        return true;
    }

    /** True when an automatic return is pending, for the HUD. */
    public static int ticksUntilReturn() {
        return returnIn;
    }

    /**
     * Counts down the automatic return. Called every tick, returns true on the tick the
     * return is due so the caller can act on it exactly once.
     */
    public static boolean tickReturn() {
        if (returnIn < 0) {
            return false;
        }
        if (--returnIn > 0) {
            return false;
        }
        returnIn = -1;
        return true;
    }

    public static void forget() {
        previous = -1;
        swappedTo = -1;
        returnIn = -1;
    }

    /** True when there is something to go back to. */
    public static boolean armed() {
        return previous >= 0 && previous != swappedTo;
    }

    private static void select(Minecraft client, int slot) {
        client.player.getInventory().setSelectedSlot(slot);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
