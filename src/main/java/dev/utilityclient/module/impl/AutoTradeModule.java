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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Trades the offer you pick, over and over, feeding the payment from your own inventory.
 *
 * <p>Open a villager, click the trade you want, and it does the rest: it moves the required
 * items from your inventory into the trade slots, clicks the result, and repeats until the
 * villager runs out of that offer. When the offer disappears the window closes itself.
 *
 * <p>Filling the payment is the whole point, and it is done by calling the merchant menu's
 * own private routine rather than by moving items here. That matters more than it sounds.
 * There is no click that fills a merchant payment slot: a quick move in a merchant window
 * sends items to your hotbar instead, so the only way to do it by hand is to pick items up
 * and place them, which is exactly the sort of thing that goes wrong and eats items. An
 * earlier build of this module did it by hand and ate the player's sticks. Reusing the game's
 * logic means only the items the offer actually asks for are ever touched, and it stops as
 * soon as the slots are full.
 *
 * <p>How it knows a trade is finished is worth spelling out. A villager removes an offer
 * from its list once it is exhausted, so the list getting shorter is the reliable signal.
 * The offer object also reports being out of stock directly, and that is checked too, but
 * the list length is what it trusts, because the active offer reference can stay populated
 * on the client after the server has already removed it.
 *
 * <p>There is a hard trade cap as well, and it is not optional. A server villager that
 * restocks, or one with an effectively unlimited offer, would otherwise drain your inventory
 * forever. If the trade count does not move for a while, it also stops on its own, which
 * covers running out of payment.
 */
public final class AutoTradeModule extends Module {
    /**
     * The result slot is the only one at this position, which lets it be found by shape
     * rather than trusting an index. In the vanilla merchant menu the result sits at 154,28
     * and the two payment slots at 75, so nothing else can match.
     */
    private static final int RESULT_X = 154;
    private static final int RESULT_Y = 28;

    /**
     * The two payment slots and the result, in the fixed order the merchant menu uses.
     *
     * <p>These are only used as a fallback, because the result slot is found by position.
     * A server that shifts the layout would be caught by that check rather than trusted.
     */
    private static final int PAYMENT1_SLOT = 0;
    private static final int PAYMENT2_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    /** The merchant menu's own inventory range, 3 to 39 exclusive. */
    private static final int INVENTORY_START = 3;
    private static final int INVENTORY_END = 39;

    public final ModuleSetting<Integer> delay;
    public final ModuleSetting<Integer> maxTrades;
    public final ModuleSetting<Integer> idleTimeout;
    public final ModuleSetting<Boolean> closeWhenDone;
    public final ModuleSetting<Boolean> showStatus;
    public final ModuleSetting<Boolean> tradeWhileFull;

    private static Field tradeContainerField;
    private static boolean reflectionWarned;

    /**
     * The merchant menu's private payment filling routine. Resolved once and reused, and
     * failing to find it only means the module reports out of payment rather than crashing.
     */
    private static Method moveFromInventoryToPaymentSlot;
    private static boolean paymentWarned;

    private MerchantOffer tracked;
    private int baselineOffers = -1;
    private int trades;
    private int idleTicks;
    private int cooldown;
    private int containerId = -1;
    private String resultName = "";

    public AutoTradeModule() {
        super("auto-trade", "Auto Villager Trader",
                "Click a trade once and it feeds the payment from your inventory and trades "
                        + "over and over until the villager runs out.",
                ModuleCategory.MISC, false, true, true);

        // Resolved eagerly so the first trade does not pay for a failed lookup, and so a
        // missing method is discovered while the class loads rather than mid trade.
        try {
            moveFromInventoryToPaymentSlot = MerchantMenu.class
                    .getDeclaredMethod("moveFromInventoryToPaymentSlot", int.class, ItemCost.class);
            moveFromInventoryToPaymentSlot.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Not fatal, but it must not be silent. Without this method the module cannot top
            // up the payment and will only ever report itself out of payment, which looks
            // exactly like having none of the items. Saying so once is far more useful.
            moveFromInventoryToPaymentSlot = null;
            System.out.println("[Utility Client] Auto Villager Trader cannot auto fill the "
                    + "trade payment, the method it needs is missing: " + exception);
        }

        delay = addSetting(ModuleSetting.integerSetting("delay", "Delay",
                "Ticks to wait between trades.", 8, 1, 60, 1));
        maxTrades = addSetting(ModuleSetting.integerSetting("max-trades", "Hard cap",
                "Safety limit. Stops after this many trades even if the villager still has stock.",
                2304, 1, 2304, 64));
        idleTimeout = addSetting(ModuleSetting.integerSetting("idle-timeout", "Out of payment after",
                "Stops if this many ticks pass with the payment slots still not fillable, "
                        + "which normally means you have run out of the items the trade wants.",
                200, 20, 1200, 20));
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
            // Should not happen in a real merchant window. Give up rather than click blind.
            finish(client, "Could not find the trade result slot, stopping");
            return;
        }

        // Put the payment in first. This is the step the module is actually for: the player
        // clicks a trade once and this keeps feeding it from their inventory, over and over,
        // rather than trading a single time and stopping.
        if (!fillPayment(client, menu, active)) {
            // The payment slots are still not satisfied. Either the player has run out or the
            // offer needs something they do not have. Wait, but do not spam clicks.
            idleTicks++;
            if (idleTicks >= idleTimeout.value()) {
                finish(client, "Out of payment for " + resultName);
            }
            return;
        }

        // Only click once the payment is in and the server has actually put a result in the
        // slot. Clicking before that is what made an earlier build eat the payment instead of
        // trading.
        if (!resultIsOffered(menu, resultSlot)) {
            // The payment went in but the server has not acknowledged the result yet. This is
            // normal for a tick or two and is not a stall, so it does not count against the
            // stall timeout.
            return;
        }

        // A plain left click, the same one a player makes on the result slot.
        client.gameMode.handleContainerInput(menu.containerId, resultSlot, 0,
                ContainerInput.PICKUP, client.player);
        trades++;
        cooldown = delay.value();
        idleTicks = 0;
    }

    /* ---------------------------------------------------------------- payment */

    /**
     * Tops the payment slots up from the player's inventory, using the game's own routine.
     *
     * <p>This deliberately does not move items by hand. A merchant menu's quick move sends
     * inventory items to the hotbar, not to the payment slots, so there is no click that
     * fills payment. The game has a private method that does exactly the right thing, walking
     * the inventory and matching against the offer's own item costs, and calling that is both
     * shorter and far safer than reimplementing it: it only ever moves items that the offer
     * genuinely asks for, and it stops as soon as the slots are full.
     *
     * <p>An earlier version of this module moved items itself and ate the player's sticks.
     * Reusing the game's logic is what removes that risk.
     *
     * @return true when the payment slots look satisfied afterwards
     */
    private static boolean fillPayment(Minecraft client, MerchantMenu menu, MerchantOffer offer) {
        if (moveFromInventoryToPaymentSlot == null) {
            return paymentSatisfied(menu, offer);
        }
        try {
            // Cost A, then cost B when the offer has a second payment.
            moveFromInventoryToPaymentSlot.invoke(menu, PAYMENT1_SLOT, offer.getItemCostA());
            offer.getItemCostB().ifPresent(cost -> {
                try {
                    moveFromInventoryToPaymentSlot.invoke(menu, PAYMENT2_SLOT, cost);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // A second payment that cannot be filled simply will not appear, and the
                    // satisfaction check below reports it.
                }
            });
            return paymentSatisfied(menu, offer);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!paymentWarned) {
                paymentWarned = true;
                System.out.println("[Utility Client] Could not fill the trade payment: "
                        + exception);
            }
            return paymentSatisfied(menu, offer);
        }
    }

    /**
     * True when both payment slots hold enough for the trade.
     *
     * <p>Checked rather than assumed, because the fill runs locally and the server has not
     * necessarily agreed yet. Counting the required amount out of the slots that are actually
     * filled is the only trustworthy test.
     */
    private static boolean paymentSatisfied(MerchantMenu menu, MerchantOffer offer) {
        return hasAtLeast(menu, PAYMENT1_SLOT, offer.getItemCostA())
                && offer.getItemCostB().map(cost -> hasAtLeast(menu, PAYMENT2_SLOT, cost))
                .orElse(true);
    }

    private static boolean hasAtLeast(MerchantMenu menu, int slotIndex, ItemCost cost) {
        try {
            Slot slot = menu.getSlot(slotIndex);
            if (slot == null) {
                return false;
            }
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            return !stack.isEmpty() && cost.test(stack) && stack.getCount() >= cost.count();
        } catch (RuntimeException exception) {
            return false;
        }
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
     * Finds the result slot by its position in the window. Index 0 is used as a fallback
     * only if the position check finds nothing, which on a normal merchant window it never
     * will.
     */
    private static int findResultSlot(MerchantMenu menu) {
        try {
            for (int index = 0; index < menu.slots.size(); index++) {
                net.minecraft.world.inventory.Slot slot = menu.getSlot(index);
                if (slot != null && slot.x == RESULT_X && slot.y == RESULT_Y) {
                    return index;
                }
            }
        } catch (RuntimeException exception) {
            return 0;
        }
        return menu.slots.isEmpty() ? -1 : 0;
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
