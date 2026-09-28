package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Field;

/**
 * Trades the offer you pick, over and over, until the villager runs out of it.
 *
 * <p>Open a villager, click the trade you want, and it does the rest: it clicks the result
 * repeatedly until the offer is exhausted, then closes the window. You place the payment
 * yourself, exactly as you would by hand.
 *
 * <p>The payment is deliberately never touched. The merchant window fills the payment slots
 * itself when you click an offer, and the server runs the entire trade, payment included,
 * from the single result click this module sends. Two earlier attempts to help anyway, one
 * moving items by hand and one calling the game's own payment routine, both ended up taking
 * the player's items without trading.
 *
 * <p>There was a third cause, and it was the real one. The result slot used to be found by
 * its screen position, with index 0 as a fallback. Index 0 is a payment slot, so whenever the
 * position check failed the module clicked a payment slot instead and picked the payment items
 * up onto the cursor. A 32 stick for 1 emerald trade therefore ate the sticks while never
 * trading, and no amount of fixing the surrounding code changed that, because the fault was
 * in which slot was being clicked. The result slot is now identified by its own type, and the
 * module stops rather than guessing if it cannot be found.
 *
 * <p>How it knows a trade is finished is worth spelling out. A villager removes an offer
 * from its list once it is exhausted, so the list getting shorter is the reliable signal.
 * The offer object also reports being out of stock directly, and that is checked too, but
 * the list length is what it trusts, because the active offer reference can stay populated
 * on the client after the server has already removed it.
 *
 * <p>There is a hard trade cap as well, and it is not optional. A server villager that
 * restocks, or one with an effectively unlimited offer, would otherwise drain your currency
 * forever. If the trade count does not move for a while, it also stops on its own, which
 * covers running out of payment.
 */
public final class AutoTradeModule extends Module {
    public final ModuleSetting<Integer> delay;
    public final ModuleSetting<Integer> maxTrades;
    public final ModuleSetting<Integer> idleTimeout;
    public final ModuleSetting<Boolean> closeWhenDone;
    public final ModuleSetting<Boolean> showStatus;
    public final ModuleSetting<Boolean> tradeWhileFull;

    private static Field tradeContainerField;
    private static boolean reflectionWarned;

    private MerchantOffer tracked;
    private int baselineOffers = -1;
    private int trades;
    private int idleTicks;
    private int cooldown;
    private int containerId = -1;
    private String resultName = "";

    public AutoTradeModule() {
        super("auto-trade", "Auto Villager Trader",
                "Click a trade once and it keeps trading it over and over until that villager "
                        + "runs out of the offer.",
                ModuleCategory.MISC, false, true, true);

        delay = addSetting(ModuleSetting.integerSetting("delay", "Delay",
                "Ticks to wait between trades.", 8, 1, 60, 1));
        maxTrades = addSetting(ModuleSetting.integerSetting("max-trades", "Hard cap",
                "Safety limit. Stops after this many trades even if the villager still has stock.",
                2304, 1, 2304, 64));
        idleTimeout = addSetting(ModuleSetting.integerSetting("idle-timeout", "Stall timeout",
                "Stops if this many ticks pass with the offer not becoming available, which "
                        + "normally means you have run out of payment.", 200, 20, 1200, 20));
        closeWhenDone = addSetting(ModuleSetting.booleanSetting("close-when-done", "Close when done",
                "Close the trade window once the offer is used up.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it finishes.", true));
        tradeWhileFull = addSetting(ModuleSetting.booleanSetting("full-inventory", "Trade when full",
                "Keep going even if your inventory cannot fit another result. Turning this off "
                        + "stops before the inventory is completely full, since a full "
                        + "inventory can refuse the result.", true));
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void tick(Minecraft client) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (!(client.gui.screen() instanceof MerchantScreen) || client.player == null
                || client.player.containerMenu == null) {
            reset();
            return;
        }
        if (!(client.player.containerMenu instanceof MerchantMenu menu)) {
            reset();
            return;
        }
        if (menu.containerId != containerId) {
            containerId = menu.containerId;
            reset();
        }

        MerchantOffer active = activeOffer(menu);
        if (active == null) {
            // Nothing picked yet. Waiting for the click that chooses a trade.
            tracked = null;
            baselineOffers = -1;
            return;
        }

        if (tracked == null) {
            // First tick after a pick. Record the starting point and start trading.
            tracked = active;
            baselineOffers = menu.getOffers().size();
            trades = 0;
            idleTicks = 0;
            resultName = describe(active);
        }

        if (isFinished(menu, active)) {
            finish(client, "Used up " + resultName + " after " + trades + " trade(s)");
            return;
        }

        if (trades >= maxTrades.value()) {
            finish(client, "Reached the hard cap of " + maxTrades.value() + " trades on " + resultName);
            return;
        }

        if (!tradeWhileFull.value() && inventoryIsFull(client)) {
            finish(client, "Inventory is full, stopping before " + resultName);
            return;
        }

        if (cooldown > 0) {
            return;
        }

        int resultSlot = findResultSlot(menu);
        if (resultSlot < 0) {
            // Stops rather than clicking a guessed slot. The one time this module did that, it
            // clicked a payment slot and took the player's items, so it never guesses again.
            finish(client, "Could not find the trade result slot, stopping");
            return;
        }

        // The payment is never touched here. The merchant window already fills the payment
        // slots itself the moment the player clicks an offer, and it keeps them topped up
        // while the trade is available, so all this has to do is click the result over and
        // over.
        //
        // An earlier build tried to help by calling the game's own payment filling routine on
        // every tick. That was wrong in a way that was not obvious: the routine moves items
        // out of the player's inventory into the payment slots as a local change only, and
        // nothing tells the server. So a 32 stick for 1 emerald trade would quietly swallow
        // sticks from the player's inventory without ever trading, which looked exactly like
        // the module grabbing the wrong item. Reusing the game's code was not the safe option
        // here, because the game calls it in a context where the server is already watching.
        //
        // Only ever click when the server has actually put a result in the slot, which it
        // only does once the payment is present and the trade is genuinely available.
        if (!resultIsOffered(menu, resultSlot)) {
            idleTicks++;
            if (idleTicks >= idleTimeout.value()) {
                finish(client, "No trade became available - out of payment, or the offer is gone");
            }
            return;
        }

        // A plain left click on the result slot, the same one a player makes. The server runs
        // the whole trade from this one packet, payment included, so nothing here touches the
        // payment slots or the inventory at all.
        client.gameMode.handleContainerInput(menu.containerId, resultSlot, 0,
                ContainerInput.PICKUP, client.player);
        trades++;
        cooldown = delay.value();
        idleTicks = 0;
    }


    /**
     * True when the result slot is holding something, which the server only does once a
     * trade can actually be made.
     */
    private static boolean resultIsOffered(MerchantMenu menu, int resultSlot) {
        try {
            net.minecraft.world.inventory.Slot slot = menu.getSlot(resultSlot);
            return slot != null && !slot.getItem().isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Finds the result slot by its type.
     *
     * <p>An earlier version found it by screen position, 154,28, and fell back to index 0 when
     * that found nothing. Index 0 is the first payment slot. So if the position check ever
     * failed, the module clicked the payment slot instead, which picks the payment items up
     * onto the cursor. That is why it appeared to grab the sticks rather than the emerald, and
     * it is why the symptom never went away no matter what else was changed.
     *
     * <p>Matching on the slot's actual class cannot fail that way. The merchant menu builds
     * its result slot as a distinct type, and the two payment slots are ordinary slots, so
     * there is exactly one match and it is always the right one, whatever the window layout
     * or resolution.
     *
     * <p>There is deliberately no fallback any more. If the result slot cannot be identified
     * the module stops rather than guessing, because guessing here costs the player items.
     */
    private static int findResultSlot(MerchantMenu menu) {
        try {
            for (int index = 0; index < menu.slots.size(); index++) {
                if (menu.getSlot(index) instanceof MerchantResultSlot) {
                    return index;
                }
            }
        } catch (RuntimeException exception) {
            return -1;
        }
        return -1;
    }

    /* ---------------------------------------------------------------- state */

    /**
     * True once the offer being traded can no longer be made. The offer list shrinking is
     * the dependable signal, with the offer's own out of stock flag as a second opinion.
     */
    private boolean isFinished(MerchantMenu menu, MerchantOffer active) {
        if (baselineOffers >= 0 && menu.getOffers().size() < baselineOffers) {
            return true;
        }
        if (active.isOutOfStock()) {
            return true;
        }
        if (active.getUses() >= active.getMaxUses() && active.getMaxUses() > 0) {
            return true;
        }
        return false;
    }

    private void finish(Minecraft client, String message) {
        if (showStatus.value() && client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Auto Trader] " + message));
        }
        if (closeWhenDone.value() && client.player != null) {
            client.player.closeContainer();
            if (client.gui.screen() != null) {
                client.gui.setScreen(null);
            }
        }
        reset();
    }

    private void reset() {
        tracked = null;
        baselineOffers = -1;
        trades = 0;
        idleTicks = 0;
        cooldown = 0;
    }

    /* ---------------------------------------------------------------- helpers */

    private static boolean inventoryIsFull(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inventory = client.player.getInventory();
        // The first 36 slots are the main inventory. The hotbar cannot be a reason to stop,
        // because stackable results just merge into what is already there.
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String describe(MerchantOffer offer) {
        String result = offer.getResult().getHoverName().getString();
        return result.isBlank() ? "that trade" : result;
    }

    /**
     * The offer the player has selected. The screen does not expose it and the menu keeps
     * its trade container private, so it is read reflectively. If that ever stops working
     * the module simply never starts, rather than misbehaving.
     */
    private static MerchantOffer activeOffer(MerchantMenu menu) {
        try {
            if (tradeContainerField == null) {
                Field field = MerchantMenu.class.getDeclaredField("tradeContainer");
                field.setAccessible(true);
                tradeContainerField = field;
            }
            Object container = tradeContainerField.get(menu);
            if (container instanceof MerchantContainer tradeContainer) {
                return tradeContainer.getActiveOffer();
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!reflectionWarned) {
                reflectionWarned = true;
                System.out.println("[Utility Client] Could not read the selected trade: "
                        + exception);
            }
        }
        return null;
    }

    /** Total trades made since the module was enabled, for the HUD. */
    public int trades() {
        return trades;
    }

    public boolean trading() {
        return tracked != null;
    }
}
